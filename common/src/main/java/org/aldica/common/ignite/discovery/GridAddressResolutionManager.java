/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.discovery;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

import org.aldica.common.ignite.lifecycle.IgniteInstanceLifecycleAware;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.PropertyCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.AddressResolver;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.DiscoveryEvent;
import org.apache.ignite.events.EventType;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.spi.communication.CommunicationSpi;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Instances of this class serve to resolve private/public addresses of Ignite data grid members. The instances actively coordinate and
 * exchange address resolution maps with other members of the grid in order to support very dynamic address translation scenarios, such as
 * in Docker- or Kubernetes-based deployments.
 *
 * @author Axel Faust
 */
public class GridAddressResolutionManager implements AddressResolver, IgniteInstanceLifecycleAware, InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(GridAddressResolutionManager.class);

    private static final String ATTR_KEY_HOST_MAPPINGS = GridAddressResolutionManager.class.getName() + ".hostMappings";

    private static final String ATTR_KEY_SOCKET_MAPPINGS = GridAddressResolutionManager.class.getName() + ".socketMappings";

    protected IgniteConfiguration configuration;

    protected String externalHost;

    protected Integer externalDiscoPortBase;

    protected Integer externalCommPortBase;

    protected Integer externalTimePortBase;

    protected final Map<String, Collection<String>> effectiveHostMappings = new HashMap<>();

    protected final Map<InetSocketAddress, Collection<InetSocketAddress>> effectiveSocketMappings = new HashMap<>();

    protected final ReentrantReadWriteLock effectiveMappingLock = new ReentrantReadWriteLock(true);

    protected final Map<String, String> ownHostMappings = new HashMap<>();

    protected final Map<InetSocketAddress, InetSocketAddress> ownSocketMappings = new HashMap<>();

    protected final Map<UUID, Map<String, String>> hostMappingsByNode = new HashMap<>();

    protected final Map<UUID, Map<InetSocketAddress, InetSocketAddress>> socketMappingsByNode = new HashMap<>();

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "configuration", this.configuration);
    }

    /**
     * @param configuration
     *            the configuration to set
     */
    public void setConfiguration(final IgniteConfiguration configuration)
    {
        this.configuration = configuration;
    }

    /**
     * @param externalHost
     *            the externalHost to set
     */
    public void setExternalHost(final String externalHost)
    {
        this.externalHost = externalHost;
    }

    /**
     * @param externalDiscoPortBase
     *            the externalDiscoPortBase to set
     */
    public void setExternalDiscoPortBase(final Integer externalDiscoPortBase)
    {
        this.externalDiscoPortBase = externalDiscoPortBase;
    }

    /**
     * @param externalCommPortBase
     *            the externalCommPortBase to set
     */
    public void setExternalCommPortBase(final Integer externalCommPortBase)
    {
        this.externalCommPortBase = externalCommPortBase;
    }

    /**
     * @param externalTimePortBase
     *            the externalTimePortBase to set
     */
    public void setExternalTimePortBase(final Integer externalTimePortBase)
    {
        this.externalTimePortBase = externalTimePortBase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceStartup(final String instanceName)
    {
        if (instanceName.equals(this.configuration.getIgniteInstanceName()))
        {
            // make sure we are registered globally
            this.configuration.setAddressResolver(this);

            LOGGER.debug("Initialising own host / socket mappings");

            this.initialiseOwnAddressMappings();

            if (!this.ownHostMappings.isEmpty() || !this.ownSocketMappings.isEmpty())
            {
                final Map<String, ?> userAttributesPrev = this.configuration.getUserAttributes();
                final Map<String, Object> userAttributesCur = userAttributesPrev != null ? new HashMap<>(userAttributesPrev)
                        : new HashMap<>();
                if (!this.ownHostMappings.isEmpty())
                {
                    LOGGER.debug("Setting host mappings {} in Ignite user attributes", this.ownHostMappings);
                    userAttributesCur.put(ATTR_KEY_HOST_MAPPINGS, this.ownHostMappings);
                }
                if (!this.ownSocketMappings.isEmpty())
                {
                    LOGGER.debug("Setting socket mappings {} in Ignite user attributes", this.ownSocketMappings);
                    userAttributesCur.put(ATTR_KEY_SOCKET_MAPPINGS, this.ownSocketMappings);
                }
                this.configuration.setUserAttributes(userAttributesCur);

                this.updateEffectiveMappings();
            }

            LOGGER.debug("Completed initialisation of own host / socket mappings");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceStartup(final String instanceName)
    {
        if (instanceName.equals(this.configuration.getIgniteInstanceName()))
        {
            final Ignite ignite = Ignition.ignite(instanceName);

            LOGGER.debug("Collecting any host / socket mappings from existing nodes");
            ignite.cluster().forServers().forRemotes().nodes().forEach(this::addNodeMappings);
            if (!this.hostMappingsByNode.isEmpty() || !this.socketMappingsByNode.isEmpty())
            {
                this.updateEffectiveMappings();
            }
            else
            {
                LOGGER.debug("No host / socket mappings provided by existing nodes");
            }

            LOGGER.debug("Setting up discovery listener");
            ignite.events().localListen((final DiscoveryEvent e) -> this.onDiscoveryEvent(e), EventType.EVT_NODE_JOINED,
                    EventType.EVT_NODE_LEFT, EventType.EVT_NODE_FAILED);
            LOGGER.debug("Discovery listener set up");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceShutdown(final String instanceName)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceShutdown(final String instanceName)
    {
        // NO-OP
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Collection<InetSocketAddress> getExternalAddresses(final InetSocketAddress addr) throws IgniteCheckedException
    {
        final Collection<InetSocketAddress> externalAddresses = new LinkedHashSet<>();

        this.effectiveMappingLock.readLock().lock();
        try
        {
            LOGGER.debug("Looking up mappings for socket address {}", addr);
            final Collection<InetSocketAddress> socketMappings = this.effectiveSocketMappings.get(addr);
            if (socketMappings != null && !socketMappings.isEmpty())
            {
                LOGGER.debug("Found explicit socket mappings {} for {}", socketMappings, addr);
                externalAddresses.addAll(socketMappings);
            }
            else
            {

                if (!addr.isUnresolved())
                {
                    final String hostAddress = addr.getAddress().getHostAddress();
                    final Collection<String> mappedHosts = this.effectiveHostMappings.get(hostAddress);
                    if (mappedHosts != null && !mappedHosts.isEmpty())
                    {
                        LOGGER.debug("Found host mappings {} for {}", mappedHosts, hostAddress);
                        mappedHosts.forEach(host -> {
                            externalAddresses.add(new InetSocketAddress(host, addr.getPort()));
                        });
                    }
                }

                final String hostString = addr.getHostString();
                final Collection<String> mappedHosts = this.effectiveHostMappings.get(hostString);
                if (mappedHosts != null && !mappedHosts.isEmpty())
                {
                    LOGGER.debug("Found host mappings {} for {}", mappedHosts, hostString);
                    mappedHosts.forEach(host -> {
                        externalAddresses.add(new InetSocketAddress(host, addr.getPort()));
                    });
                }

                if (externalAddresses.isEmpty())
                {
                    LOGGER.debug("No socket or host mappings found for {}", addr);
                    externalAddresses.add(addr);
                }
            }
        }
        finally
        {
            this.effectiveMappingLock.readLock().unlock();
        }

        return externalAddresses;
    }

    protected void initialiseOwnAddressMappings()
    {
        LOGGER.debug(
                "Configuration of grid address resolution manager: externalHost={}, externalCommPortBase={}, externalDiscoPortBase={}, externalTimePortBase={}",
                this.externalHost, this.externalCommPortBase, this.externalDiscoPortBase, this.externalTimePortBase);

        if ((this.externalHost != null && !this.externalHost.trim().isEmpty()) || this.externalDiscoPortBase != null
                || this.externalCommPortBase != null || this.externalTimePortBase != null)
        {
            final String localHost = this.configuration.getLocalHost();

            try
            {
                final InetAddress localHostAddr = IgniteUtils.resolveLocalHost(localHost);
                final String effectiveLocalHostAddr = localHostAddr.getHostAddress();
                final String effectiveLocalHostName = localHostAddr.getHostName();

                final String effectiveExternalHostAddr;
                final String effectiveExternalHostName;
                {
                    String effectiveExternalHostAddrI;
                    String effectiveExternalHostNameI;

                    try
                    {
                        final InetAddress externalHostAddr;
                        if (this.externalHost != null && !this.externalHost.trim().isEmpty())
                        {
                            externalHostAddr = InetAddress.getByName(this.externalHost);
                            LOGGER.debug("External host {} resolved to {}", this.externalHost, externalHostAddr);
                        }
                        else
                        {
                            externalHostAddr = localHostAddr;
                        }

                        if (!(externalHostAddr.isLoopbackAddress() || externalHostAddr.isAnyLocalAddress()))
                        {
                            effectiveExternalHostAddrI = externalHostAddr.getHostAddress();
                            effectiveExternalHostNameI = externalHostAddr.getHostName();
                        }
                        else
                        {
                            LOGGER.debug("External host address {} is a loopback / any local address", externalHostAddr);
                            effectiveExternalHostAddrI = null;
                            effectiveExternalHostNameI = null;
                        }
                    }
                    catch (final UnknownHostException uhe)
                    {
                        LOGGER.info("External host {} could not be resolved to IP", this.externalHost);
                        effectiveExternalHostAddrI = null;
                        effectiveExternalHostNameI = this.externalHost;
                    }
                    effectiveExternalHostAddr = effectiveExternalHostAddrI;
                    effectiveExternalHostName = effectiveExternalHostNameI;
                }

                final IgniteBiTuple<Collection<String>, Collection<String>> localAddresses = IgniteUtils
                        .resolveLocalAddresses(localHostAddr);

                if ((effectiveExternalHostAddr != null && !EqualsHelper.nullSafeEquals(effectiveLocalHostAddr, effectiveExternalHostAddr))
                        || (effectiveExternalHostName != null
                                && !EqualsHelper.nullSafeEquals(effectiveLocalHostName, effectiveExternalHostName)))
                {
                    if (!(localHostAddr.isLoopbackAddress() || localHostAddr.isAnyLocalAddress()))
                    {
                        // map primary local host name + address with preference to map to external IP instead of host name
                        // host name resolution adds additional overhead / delay to grid communication
                        if (effectiveExternalHostAddr != null
                                && !EqualsHelper.nullSafeEquals(effectiveLocalHostAddr, effectiveExternalHostAddr))
                        {
                            this.ownHostMappings.put(effectiveLocalHostAddr, effectiveExternalHostAddr);
                        }
                        else if (effectiveExternalHostName != null
                                && !EqualsHelper.nullSafeEquals(effectiveLocalHostName, effectiveExternalHostName))
                        {
                            this.ownHostMappings.put(effectiveLocalHostAddr, effectiveExternalHostName);
                        }

                        if (!EqualsHelper.nullSafeEquals(effectiveLocalHostAddr, effectiveLocalHostName))
                        {
                            if (effectiveExternalHostAddr != null
                                    && !EqualsHelper.nullSafeEquals(effectiveLocalHostName, effectiveExternalHostAddr))
                            {
                                this.ownHostMappings.put(effectiveLocalHostName, effectiveExternalHostAddr);
                            }
                            else if (effectiveExternalHostName != null
                                    && !EqualsHelper.nullSafeEquals(effectiveLocalHostName, effectiveExternalHostName))
                            {
                                this.ownHostMappings.put(effectiveLocalHostName, effectiveExternalHostName);
                            }
                        }
                    }

                    localAddresses.get2().stream().filter(hostName -> !this.ownHostMappings.containsKey(hostName)).forEach(hostName -> {
                        if (effectiveExternalHostAddr != null && !EqualsHelper.nullSafeEquals(hostName, effectiveExternalHostAddr))
                        {
                            this.ownHostMappings.put(hostName, effectiveExternalHostAddr);
                        }
                        else if (effectiveExternalHostName != null && !EqualsHelper.nullSafeEquals(hostName, effectiveExternalHostName))
                        {
                            this.ownHostMappings.put(hostName, effectiveExternalHostName);
                        }
                    });

                    // map local host addresses with preference to map to external IP address instead of host name
                    // host name resolution adds additional overhead / delay to grid communication
                    localAddresses.get1().stream().filter(locAddr -> {
                        boolean result;
                        try
                        {
                            final InetAddress address = InetAddress.getByName(locAddr);
                            result = !(address.isLoopbackAddress() || address.isAnyLocalAddress());
                        }
                        catch (final IOException ioex)
                        {
                            // should never happen since these localAddrs are result of address resolutions via InetAddress
                            result = false;
                        }
                        return result;
                    }).filter(locAddr -> !this.ownHostMappings.containsKey(locAddr)).forEach(locAddr -> {
                        if (effectiveExternalHostAddr != null)
                        {
                            this.ownHostMappings.put(locAddr, effectiveExternalHostAddr);
                        }
                        else if (effectiveExternalHostName != null)
                        {
                            this.ownHostMappings.put(locAddr, effectiveExternalHostName);
                        }
                    });
                }

                final CommunicationSpi<?> communicationSpi = this.configuration.getCommunicationSpi();
                final int localCommPort;
                final int localCommPortRange;
                if (communicationSpi instanceof TcpCommunicationSpi)
                {
                    localCommPort = ((TcpCommunicationSpi) communicationSpi).getLocalPort();
                    localCommPortRange = ((TcpCommunicationSpi) communicationSpi).getLocalPortRange();
                }
                else
                {
                    localCommPort = TcpCommunicationSpi.DFLT_PORT;
                    localCommPortRange = TcpCommunicationSpi.DFLT_PORT_RANGE;
                }
                LOGGER.debug("Local TCP communication configuration: portBase={}, portRange={}, explicit={}", localCommPort,
                        localCommPortRange, communicationSpi instanceof TcpCommunicationSpi);
                this.mapOwnSocketAddresses(localHostAddr, effectiveLocalHostAddr, effectiveLocalHostName, localAddresses.get1(),
                        localAddresses.get2(), effectiveExternalHostAddr, effectiveExternalHostName, localCommPort, localCommPortRange,
                        this.externalCommPortBase);

                final DiscoverySpi discoverySpi = this.configuration.getDiscoverySpi();
                final int localDiscoPort;
                final int localDiscoPortRange;
                if (discoverySpi instanceof TcpDiscoverySpi)
                {
                    // Note: TcpDiscoverySpi has an awkward getLocalPort() which only returns an established and not the configured port
                    // TODO: Report bug / inconsistency with other get-operations yielding only configured values
                    Integer localDiscoPortI = null;
                    try
                    {
                        final Field field = TcpDiscoverySpi.class.getDeclaredField("locPort");
                        field.setAccessible(true);
                        localDiscoPortI = (Integer) field.get(discoverySpi);
                    }
                    catch (NoSuchFieldException | IllegalAccessException e)
                    {
                        LOGGER.warn("Unable to retrieve the configured local TCP discovery port", e);
                    }
                    // best attempt to use the default port
                    localDiscoPort = localDiscoPortI != null ? localDiscoPortI : TcpDiscoverySpi.DFLT_PORT;
                    localDiscoPortRange = ((TcpDiscoverySpi) discoverySpi).getLocalPortRange();
                }
                else
                {
                    localDiscoPort = TcpDiscoverySpi.DFLT_PORT;
                    localDiscoPortRange = TcpDiscoverySpi.DFLT_PORT_RANGE;
                }
                LOGGER.debug("Local TCP discovery configuration: portBase={}, portRange={}, explicit={}", localDiscoPort,
                        localDiscoPortRange, discoverySpi instanceof TcpDiscoverySpi);
                this.mapOwnSocketAddresses(localHostAddr, effectiveLocalHostAddr, effectiveLocalHostName, localAddresses.get1(),
                        localAddresses.get2(), effectiveExternalHostAddr, effectiveExternalHostName, localDiscoPort, localDiscoPortRange,
                        this.externalDiscoPortBase);

                final int localTimePort = this.configuration.getTimeServerPortBase();
                final int localTimePortRange = this.configuration.getTimeServerPortRange();
                LOGGER.debug("Local time server configuration: portBase={}, portRange={}", localTimePort, localTimePortRange);
                this.mapOwnSocketAddresses(localHostAddr, effectiveLocalHostAddr, effectiveLocalHostName, localAddresses.get1(),
                        localAddresses.get2(), effectiveExternalHostAddr, effectiveExternalHostName, localTimePort, localTimePortRange,
                        this.externalTimePortBase);
            }
            catch (final IOException e)
            {
                LOGGER.warn("Error resolving local/external addresses for grid address resolution", e);
            }
        }
    }

    protected void mapOwnSocketAddresses(final InetAddress localHostAddr, final String effectiveLocalHostAddr,
            final String effectiveLocalHostName, final Collection<String> localAddrs, final Collection<String> localHostNames,
            final String effectiveExternalHostAddr, final String effectiveExternalHostName, final int localPortBase,
            final int localPortRange, final Integer externalPortBase)
    {
        if (externalPortBase != null && externalPortBase.intValue() != localPortBase)
        {
            for (int offset = 0; offset <= localPortRange; offset++)
            {
                final int localPort = localPortBase + offset;
                final int externalPort = externalPortBase.intValue() + offset;

                if (!(localHostAddr.isLoopbackAddress() || localHostAddr.isAnyLocalAddress()))
                {
                    // map primary local socket address with preference to map to external IP address instead of host name
                    // host name resolution adds additional overhead / delay to grid communication
                    if (effectiveExternalHostAddr != null
                            && !EqualsHelper.nullSafeEquals(effectiveLocalHostAddr, effectiveExternalHostAddr))
                    {
                        this.ownSocketMappings.put(new InetSocketAddress(effectiveLocalHostAddr, localPort),
                                new InetSocketAddress(effectiveExternalHostAddr, externalPort));
                    }
                    else if (effectiveExternalHostName != null
                            && !EqualsHelper.nullSafeEquals(effectiveLocalHostName, effectiveExternalHostName))
                    {
                        this.ownSocketMappings.put(new InetSocketAddress(effectiveLocalHostAddr, localPort),
                                new InetSocketAddress(effectiveExternalHostName, externalPort));
                    }

                    if (!EqualsHelper.nullSafeEquals(effectiveLocalHostAddr, effectiveLocalHostName))
                    {
                        if (effectiveExternalHostAddr != null
                                && !EqualsHelper.nullSafeEquals(effectiveLocalHostName, effectiveExternalHostAddr))
                        {
                            this.ownSocketMappings.put(new InetSocketAddress(effectiveLocalHostName, localPort),
                                    new InetSocketAddress(effectiveExternalHostAddr, externalPort));
                        }
                        else if (effectiveExternalHostName != null
                                && !EqualsHelper.nullSafeEquals(effectiveLocalHostName, effectiveExternalHostName))
                        {
                            this.ownSocketMappings.put(new InetSocketAddress(effectiveLocalHostName, localPort),
                                    new InetSocketAddress(effectiveExternalHostName, externalPort));
                        }
                    }
                }

                localHostNames.stream().map(hostName -> new InetSocketAddress(hostName, localPort))
                        .filter(socketAddr -> !this.ownSocketMappings.containsKey(socketAddr)).forEach(socketAddr -> {
                            if (effectiveExternalHostAddr != null)
                            {
                                this.ownSocketMappings.put(socketAddr, new InetSocketAddress(effectiveExternalHostAddr, externalPort));
                            }
                            else if (effectiveExternalHostName != null)
                            {
                                this.ownSocketMappings.put(socketAddr, new InetSocketAddress(effectiveExternalHostName, externalPort));
                            }
                        });

                // map local socket addresses with preference to map to external IP address instead of host name
                // host name resolution adds additional overhead / delay to grid communication
                localAddrs.stream().filter(locAddr -> {
                    boolean result;
                    try
                    {
                        final InetAddress address = InetAddress.getByName(locAddr);
                        result = !(address.isLoopbackAddress() || address.isAnyLocalAddress());
                    }
                    catch (final IOException ioex)
                    {
                        // should never happen since these localAddrs are result of address resolutions via InetAddress
                        result = false;
                    }
                    return result;
                }).map(locAddr -> new InetSocketAddress(locAddr, localPort))
                        .filter(socketAddr -> !this.ownSocketMappings.containsKey(socketAddr)).forEach(socketAddr -> {
                            if (effectiveExternalHostAddr != null)
                            {
                                this.ownSocketMappings.put(socketAddr, new InetSocketAddress(effectiveExternalHostAddr, externalPort));
                            }
                            else if (effectiveExternalHostName != null)
                            {
                                this.ownSocketMappings.put(socketAddr, new InetSocketAddress(effectiveExternalHostName, externalPort));
                            }
                        });
            }
        }
    }

    protected boolean onDiscoveryEvent(final DiscoveryEvent e)
    {
        final ClusterNode eventNode = e.eventNode();
        if (!eventNode.isClient())
        {
            final UUID nodeId = eventNode.id();
            final int eventType = e.type();

            LOGGER.debug("Handling of discovery event {} for node {}", eventType, nodeId);

            boolean changed = false;
            switch (eventType)
            {
                case EventType.EVT_NODE_JOINED:
                    changed = this.addNodeMappings(eventNode);
                    break;
                case EventType.EVT_NODE_LEFT:
                case EventType.EVT_NODE_FAILED:
                    LOGGER.debug("Removing host / socket mappings for node {}", nodeId);
                    synchronized (this.hostMappingsByNode)
                    {
                        changed = this.hostMappingsByNode.remove(nodeId) != null;
                        changed = this.socketMappingsByNode.remove(nodeId) != null || changed;
                    }
                    break;
                default:
                    LOGGER.warn("Received event type {} for which listener was not registered", eventType);
            }

            if (changed)
            {
                this.updateEffectiveMappings();
            }

            LOGGER.debug("Completed handling of discovery event {} for node {}", eventType, nodeId);
        }

        return true;
    }

    protected boolean addNodeMappings(final ClusterNode node)
    {
        final UUID nodeId = node.id();
        final Map<String, Object> attributes = node.attributes();
        final Object hostMappingsCandidate = attributes.get(ATTR_KEY_HOST_MAPPINGS);
        final Object socketMappingsCandidate = attributes.get(ATTR_KEY_SOCKET_MAPPINGS);

        boolean containedMappings = false;
        synchronized (this.hostMappingsByNode)
        {
            if (hostMappingsCandidate instanceof Map<?, ?>)
            {
                @SuppressWarnings("unchecked")
                final Map<String, String> hostMappings = (Map<String, String>) hostMappingsCandidate;
                if (!hostMappings.isEmpty())
                {
                    this.hostMappingsByNode.put(nodeId, hostMappings);
                    LOGGER.debug("Registered host mappings {} from node {}", hostMappings, nodeId);
                    containedMappings = true;
                }
            }
            if (socketMappingsCandidate instanceof Map<?, ?>)
            {
                @SuppressWarnings("unchecked")
                final Map<InetSocketAddress, InetSocketAddress> socketMappings = (Map<InetSocketAddress, InetSocketAddress>) socketMappingsCandidate;
                if (!socketMappings.isEmpty())
                {
                    this.socketMappingsByNode.put(nodeId, socketMappings);
                    LOGGER.debug("Registered socket mappings {} from node {}", socketMappings, nodeId);
                    containedMappings = true;
                }
            }
        }

        return containedMappings;
    }

    protected void updateEffectiveMappings()
    {
        final Map<String, Collection<String>> effectiveHostMappings = new HashMap<>();
        final Map<InetSocketAddress, Collection<InetSocketAddress>> effectiveSocketMappings = new HashMap<>();

        final BiConsumer<String, String> hostMappingProcessor = (from, to) -> {
            effectiveHostMappings.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
        };
        final BiConsumer<InetSocketAddress, InetSocketAddress> socketMappingProcessor = (from, to) -> {
            effectiveSocketMappings.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
        };

        this.ownHostMappings.forEach(hostMappingProcessor);
        this.ownSocketMappings.forEach(socketMappingProcessor);

        final HashMap<UUID, Map<String, String>> hostMappingsByNodeShallowCopy;
        final HashMap<UUID, Map<InetSocketAddress, InetSocketAddress>> socketMappingsByNodeShallowCopy;
        synchronized (this.hostMappingsByNode)
        {
            hostMappingsByNodeShallowCopy = new HashMap<>(this.hostMappingsByNode);
            socketMappingsByNodeShallowCopy = new HashMap<>(this.socketMappingsByNode);
        }

        hostMappingsByNodeShallowCopy.values().forEach(hostMappings -> hostMappings.forEach(hostMappingProcessor));
        socketMappingsByNodeShallowCopy.values().forEach(socketMappings -> socketMappings.forEach(socketMappingProcessor));

        this.effectiveMappingLock.writeLock().lock();
        try
        {
            this.effectiveHostMappings.clear();
            this.effectiveHostMappings.putAll(effectiveHostMappings);

            this.effectiveSocketMappings.clear();
            this.effectiveSocketMappings.putAll(effectiveSocketMappings);
        }
        finally
        {
            this.effectiveMappingLock.writeLock().unlock();
        }

        LOGGER.debug("Recalculated effective host mappings {} and socket mappings {}", effectiveHostMappings, effectiveSocketMappings);
    }
}
