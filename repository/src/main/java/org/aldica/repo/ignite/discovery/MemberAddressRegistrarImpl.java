/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.discovery;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.aldica.common.ignite.lifecycle.IgniteInstanceLifecycleAware;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.service.cmr.attributes.AttributeService;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.PropertyCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.DiscoveryEvent;
import org.apache.ignite.events.EventType;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Instances of this class handle all registration of grid member addresses via Alfresco's {@link AttributeService} so that each grid member
 * can obtain a consistent view of active members. Each member's registrar provides the following functionalities:
 *
 * <ul>
 * <li><b>Before instance startup:</b> Obtain the list of active grid member addresses from the AttributeService and register these as
 * addresses with the {@link TcpDiscoveryIpFinder}</li>
 * <li><b>After instance startup:</b> Collect all active members of the grid and update the list of active grid member addresses in the
 * AttributeService</li>
 * <li><b>Upon {@link EventType#EVT_NODE_JOINED node joined} events:</b> If the new node is itself not using a registrar instance of this
 * class, collect all active members of the grid and update the list of active grid member addresses in the AttributeService - the registrar
 * will use Alfresco's {@link JobLockService} to ensure only one member in the grid preforms this action as a result of the event</li>
 * <li><b>Upon {@link EventType#EVT_NODE_LEFT node left} events:</b> Collect all remaining active members of the grid and update the list of
 * active grid member addresses in the AttributeService - the registrar will use Alfresco's {@link JobLockService} to ensure only one member
 * in the grid preforms this action as a result of the event</li>
 * <li><b>Upon {@link EventType#EVT_NODE_FAILED node failed} events:</b> Records the node failure in-memory for any subsequent validations
 * of active member lists</li>
 * <li><b>When triggered by a caller (e.g. recurring job):</b> Collect all active members of the grid and update the list of active grid
 * member addresses in the AttributeService - the registrar will use Alfresco's {@link JobLockService} to ensure only one member in the grid
 * preforms this action at any given
 * time</li>
 * </ul>
 *
 * @author Axel Faust
 */
public class MemberAddressRegistrarImpl implements InitializingBean, IgniteInstanceLifecycleAware, MemberAddressRegistrar
{

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberAddressRegistrarImpl.class);

    private static final String ALDICA_IGNITE_GRID_MEMBERS = "aldica-ignite-instance-members";

    private static final QName LOCK_QNAME = QName.createQName("http://aldica.org/services/discovery/1.0",
            MemberAddressRegistrar.class.getSimpleName());

    private static final int LOCK_TTL = 10000;

    private static final int SELF_REGISTRATION_LOCK_RETRY_WAIT = 2500;

    private static final int SELF_REGISTRATION_LOCK_RETRY_COUNT = 5;

    private static final String SELF_REGISTRATION_KEY = IgniteUtils.getSimpleName(MemberAddressRegistrarImpl.class) + ".selfRegistration";

    protected TransactionService transactionService;

    protected JobLockService jobLockService;

    protected AttributeService attributeService;

    protected IgniteConfiguration configuration;

    protected TcpDiscoveryIpFinder ipFinder;

    protected String instanceName;

    protected String discoveryAddressesKey;

    protected final Map<UUID, Collection<String>> addressesByNodeId = new HashMap<>();

    protected final Collection<String> leftMemberAddresses = new HashSet<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "transactionService", this.transactionService);
        PropertyCheck.mandatory(this, "jobLockService", this.jobLockService);
        PropertyCheck.mandatory(this, "attributeService", this.attributeService);
        PropertyCheck.mandatory(this, "configuration", this.configuration);
        PropertyCheck.mandatory(this, "ipFinder", this.ipFinder);
        PropertyCheck.mandatory(this, "instanceName", this.instanceName);

        final Map<String, ?> userAttributesPrev = this.configuration.getUserAttributes();
        final Map<String, Object> userAttributesCur = userAttributesPrev != null ? new HashMap<>(userAttributesPrev) : new HashMap<>();
        userAttributesCur.put(SELF_REGISTRATION_KEY, Boolean.TRUE);
        this.configuration.setUserAttributes(userAttributesCur);

        this.discoveryAddressesKey = IgniteUtils.spiAttribute(this.configuration.getDiscoverySpi(), TcpDiscoverySpi.ATTR_EXT_ADDRS);
    }

    /**
     * @param transactionService
     *            the transactionService to set
     */
    public void setTransactionService(final TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    /**
     * @param jobLockService
     *            the jobLockService to set
     */
    public void setJobLockService(final JobLockService jobLockService)
    {
        this.jobLockService = jobLockService;
    }

    /**
     * @param attributeService
     *            the attributeService to set
     */
    public void setAttributeService(final AttributeService attributeService)
    {
        this.attributeService = attributeService;
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
     * @param ipFinder
     *            the ipFinder to set
     */
    public void setIpFinder(final TcpDiscoveryIpFinder ipFinder)
    {
        this.ipFinder = ipFinder;
    }

    /**
     * @param instanceName
     *            the instanceName to set
     */
    public void setInstanceName(final String instanceName)
    {
        this.instanceName = instanceName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceStartup(final String instanceName)
    {
        this.initialiseTcpDiscoveryIpFinder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceStartup(final String instanceName)
    {
        if (EqualsHelper.nullSafeEquals(this.instanceName, instanceName))
        {
            try
            {
                this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
                    this.jobLockService.getTransactionalLock(LOCK_QNAME, LOCK_TTL, SELF_REGISTRATION_LOCK_RETRY_WAIT,
                            SELF_REGISTRATION_LOCK_RETRY_COUNT);
                    this.updateMemberRegistrationsImpl();
                    return null;
                }, false, true);
            }
            catch (final LockAcquisitionException laex)
            {
                LOGGER.error(
                        "Failed to update grid member registrations after instance startup due to lock acquisition error - stopping instance startup as member could end up undiscoverable, potentially causing split grids");
                throw new AlfrescoRuntimeException("Failed to update grid member registration due to lock acquisition error");
            }

            final Ignite ignite = Ignition.ignite(instanceName);
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
     * Processes an Ignite discovery event.
     *
     * @param e
     *            the event to process
     * @return {@code true} if the listener should continue to be invoked on subsequent events, {@code false} otherwise
     *
     * @see IgnitePredicate
     */
    public boolean onDiscoveryEvent(final DiscoveryEvent e)
    {
        final ClusterNode eventNode = e.eventNode();
        if (!eventNode.isClient())
        {
            final UUID nodeId = eventNode.id();
            final int eventType = e.type();

            LOGGER.debug("Handling of discovery event {} for node {}", eventType, eventNode);

            switch (eventType)
            {
                case EventType.EVT_NODE_JOINED:
                    final Boolean selfRegistration = eventNode.attribute(SELF_REGISTRATION_KEY);
                    if (!Boolean.TRUE.equals(selfRegistration))
                    {
                        LOGGER.debug("Requesting update of member registrations for join of non-self registration supporting node {}",
                                nodeId);
                        this.updateMemberRegistration();
                    }
                    else
                    {
                        LOGGER.debug("Joined node {} supports self registration - not processing event any further", nodeId);
                    }
                    break;
                case EventType.EVT_NODE_LEFT:
                case EventType.EVT_NODE_FAILED:
                    LOGGER.debug("Recording addresses of left / failed node {}", nodeId);

                    synchronized (this.leftMemberAddresses)
                    {
                        synchronized (this.addressesByNodeId)
                        {
                            this.leftMemberAddresses.addAll(this.addressesByNodeId.getOrDefault(nodeId, Collections.emptySet()));
                        }
                    }

                    break;
                default:
                    LOGGER.warn("Received event type {} for which listener was not registered", eventType);
            }

            LOGGER.debug("Completed handling of discovery event {} for node {}", eventType, nodeId);
        }

        return true;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void updateMemberRegistration()
    {
        LOGGER.debug("An update of member registrations was requested");
        try
        {
            final String lock = this.jobLockService.getLock(LOCK_QNAME, LOCK_TTL);
            try
            {
                this.updateMemberRegistrationsImpl();
            }
            finally
            {
                this.jobLockService.releaseLock(lock, LOCK_QNAME);
            }
        }
        catch (final LockAcquisitionException laex)
        {
            LOGGER.info(
                    "The lock for an update of member registrations is currently held by another server - skipping request to update on this server");
        }
    }

    protected void initialiseTcpDiscoveryIpFinder()
    {
        LOGGER.debug("Initialising TcpDiscoveryIpFinder");

        this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
            final Serializable existingAttributeValue = this.attributeService.getAttribute(ALDICA_IGNITE_GRID_MEMBERS);
            final Collection<String> currentlyRegisteredAddresses = existingAttributeValue instanceof Collection<?>
                    ? DefaultTypeConverter.INSTANCE.convert(String.class, (Collection<?>) existingAttributeValue)
                    : Collections.emptySet();

            LOGGER.debug("Loaded registered member addresses {} from database", currentlyRegisteredAddresses);

            final List<InetSocketAddress> socketAddressses = currentlyRegisteredAddresses.stream()
                    .map(MemberAddressRegistrarImpl::stringToAddress).collect(Collectors.toList());
            this.ipFinder.registerAddresses(socketAddressses);

            return null;
        }, true, true);

        LOGGER.debug("Completed TcpDiscoveryIpFinder initialisation");
    }

    /**
     * Updates the registration details of currently active grid members.
     */
    protected void updateMemberRegistrationsImpl()
    {
        LOGGER.debug("Updating member registrations");

        final Ignite ignite = Ignition.ignite(this.instanceName);

        final Collection<String> memberAddresses = new HashSet<>();
        final Map<UUID, Collection<String>> addressesByNodeId = new HashMap<>();

        ignite.cluster().forServers().nodes().forEach(n -> {
            final Collection<InetSocketAddress> publicAddresses = n.attribute(this.discoveryAddressesKey);
            final Collection<String> publicSimpleAddresses = publicAddresses.stream().map(MemberAddressRegistrarImpl::addressToString)
                    .collect(Collectors.toSet());
            addressesByNodeId.put(n.id(), publicSimpleAddresses);
            memberAddresses.addAll(publicSimpleAddresses);
        });

        LOGGER.debug("Determined active member addresses {}", memberAddresses);

        final Serializable existingAttributeValue = this.attributeService.getAttribute(ALDICA_IGNITE_GRID_MEMBERS);
        final Collection<String> currentlyRegisteredAddresses = existingAttributeValue instanceof Collection<?>
                ? DefaultTypeConverter.INSTANCE.convert(String.class, (Collection<?>) existingAttributeValue)
                : Collections.emptySet();

        LOGGER.debug("Loaded registered member addresses {} from database", currentlyRegisteredAddresses);

        Collection<String> leftMemberAddresses;
        synchronized (this.leftMemberAddresses)
        {
            leftMemberAddresses = new HashSet<>(this.leftMemberAddresses);
            this.leftMemberAddresses.clear();
        }

        LOGGER.debug("Validating current registrations with active members and left node addresses {}", leftMemberAddresses);

        final Collection<String> foreignAddresses = currentlyRegisteredAddresses.stream()
                .filter(a -> !memberAddresses.contains(a) && !leftMemberAddresses.contains(a)).collect(Collectors.toSet());
        final Collection<String> newAddresses = memberAddresses.stream().filter(a -> !currentlyRegisteredAddresses.contains(a))
                .collect(Collectors.toSet());
        final Collection<String> obsoleteAddresses = leftMemberAddresses.stream().filter(currentlyRegisteredAddresses::contains)
                .collect(Collectors.toSet());

        if (!foreignAddresses.isEmpty())
        {
            LOGGER.warn(
                    "Current member registrations in database contain the unconnected addresses {} - these may either be remnants of a previous grid failure, names of moved/migrated/reconfigured members, or a sign that multiple grids are operating in parallel",
                    foreignAddresses);
        }
        else
        {
            LOGGER.debug("No foreign addresses found in current registrations");
        }

        if (!foreignAddresses.isEmpty() || !newAddresses.isEmpty() || !obsoleteAddresses.isEmpty())
        {
            if (foreignAddresses.isEmpty())
            {
                LOGGER.debug("Updating member registrations due to new addresses {} / obsolete addresses {}", newAddresses,
                        obsoleteAddresses);
            }
            else
            {
                LOGGER.debug("Updating member registrations to override conflicting state with unconnected addresses");
            }

            // ordered lists are more efficient for AttributeService than sets
            final List<String> addressesForRegistration = new ArrayList<>(memberAddresses);
            Collections.sort(addressesForRegistration);
            this.attributeService.setAttribute((Serializable) addressesForRegistration, ALDICA_IGNITE_GRID_MEMBERS);
        }
        else
        {
            LOGGER.debug("Not updating member registrations as state is up-to-date");
        }

        synchronized (this.addressesByNodeId)
        {
            this.addressesByNodeId.clear();
            this.addressesByNodeId.putAll(addressesByNodeId);
        }

        LOGGER.debug("Completed processing member registrations update");
    }

    /**
     * Converts a socket address to a simplified textual representation using the host name / address without performing reverse lookup
     * attempts.
     *
     * @param address
     *            the address to convert
     * @return the textual representation of the address
     */
    protected static String addressToString(final InetSocketAddress address)
    {
        final String hostString = address.getHostString();
        final int port = address.getPort();
        final String addressString = hostString + ":" + String.valueOf(port);
        return addressString;
    }

    /**
     * Converts a simplified textual representation of a socket address to an actual socket address instance.
     *
     * @param addressStr
     *            the textual address representation to convert
     * @return the proper socket address instance
     */
    protected static InetSocketAddress stringToAddress(final String addressStr)
    {
        final int aSeparatorIndex = addressStr.lastIndexOf(':');

        if (aSeparatorIndex == -1)
        {
            throw new IllegalArgumentException("address must be a socket address representation");
        }

        final String hostStr = addressStr.substring(0, aSeparatorIndex);
        final int port = Integer.parseInt(addressStr.substring(aSeparatorIndex + 1));
        final InetSocketAddress address = new InetSocketAddress(hostStr, port);
        return address;
    }
}
