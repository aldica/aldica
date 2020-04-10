/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.discovery;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import org.aldica.common.ignite.context.ExternalContext;
import org.aldica.common.ignite.lifecycle.IgniteInstanceLifecycleAware;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.cmr.attributes.AttributeService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.AddressResolver;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinderAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * @author Axel Faust
 */
public class MemberTcpDiscoveryIpFinder extends TcpDiscoveryIpFinderAdapter
        implements InitializingBean, IgniteInstanceLifecycleAware, MemberAddressRegistrar
{

    private static final String ALDICA_IGNITE_GRID_MEMBERS = "aldica-ignite-instance-members";

    private static final int DEFAULT_REGISTRATION_CUT_OFF_AGE_DAYS = 30;

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberTcpDiscoveryIpFinder.class);

    protected final Collection<InetSocketAddress> addresses = new LinkedHashSet<>();

    protected final Collection<InetSocketAddress> localAddresses = new LinkedHashSet<>();

    protected final String registrationUuid = UUID.randomUUID().toString();

    protected String effectiveRegistrationId;

    protected String instanceName;

    protected int registrationCutOffAgeDays = DEFAULT_REGISTRATION_CUT_OFF_AGE_DAYS;

    protected TransactionService transactionService;

    protected AttributeService attributeService;

    protected AddressResolver addressResolver;

    protected String initialMembers;

    public MemberTcpDiscoveryIpFinder()
    {
        super();
        this.setShared(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "transactionService", this.transactionService);
        PropertyCheck.mandatory(this, "attributeService", this.attributeService);
        PropertyCheck.mandatory(this, "addressResolver", this.addressResolver);

        if (this.initialMembers != null && !this.initialMembers.trim().isEmpty())
        {
            final String[] fragments = this.initialMembers.split("\\s*,\\s*");
            final List<String> addresses = new ArrayList<>();
            for (final String address : fragments)
            {
                if (!address.isEmpty())
                {
                    addresses.add(address);
                }
            }
            if (!addresses.isEmpty())
            {
                this.setAddresses(addresses);
            }
        }
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
     * @param attributeService
     *            the attributeService to set
     */
    public void setAttributeService(final AttributeService attributeService)
    {
        this.attributeService = attributeService;
    }

    /**
     * @param addressResolver
     *            the addressResolver to set
     */
    public void setAddressResolver(final AddressResolver addressResolver)
    {
        this.addressResolver = addressResolver;
    }

    /**
     * @param initialMembers
     *            the initialMembers to set
     */
    public void setInitialMembers(final String initialMembers)
    {
        this.initialMembers = initialMembers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceStartup(final String instanceName)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceStartup(final String instanceName)
    {
        // NO-OP
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceShutdown(final String instanceName)
    {
        if (this.instanceName != null && this.instanceName.equals(instanceName))
        {
            LOGGER.info("Ignite instance {} is about to be shut down - removing local address(es) from database", instanceName);
            try
            {
                this.removeDiscoveryAddressesAttributes(this.effectiveRegistrationId);
            }
            catch (final AccessDeniedException ade)
            {
                LOGGER.warn("Failed to remove local address(es) from database due to error: {}", ade.getMessage());
            }

            this.localAddresses.clear();
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceShutdown(final String instanceName)
    {
        // NO-OP
    }

    /**
     * @param addresses
     *            the addresses to set
     */
    public void setAddresses(final Collection<String> addresses)
    {
        final Collection<InetSocketAddress> socketAddresses = new LinkedHashSet<>();

        for (final String address : addresses)
        {
            if (!address.trim().isEmpty())
            {
                final InetSocketAddress socketAddress = this.stringToAddress(address);
                if (socketAddress != null)
                {
                    socketAddresses.add(socketAddress);
                }
            }
        }

        this.addresses.clear();
        this.addresses.addAll(socketAddresses);
    }

    /**
     * @param registrationCutOffAgeDays
     *            the registrationCutOffAgeDays to set
     */
    public void setRegistrationCutOffAgeDays(final int registrationCutOffAgeDays)
    {
        if (registrationCutOffAgeDays <= 0)
        {
            throw new IllegalStateException("registrationCutOffAgeDays must be positive");
        }
        this.registrationCutOffAgeDays = registrationCutOffAgeDays;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeLocalAddresses(final Collection<InetSocketAddress> addresses) throws IgniteSpiException
    {
        ParameterCheck.mandatoryCollection("addresses", addresses);
        LOGGER.debug("Initialising local addresses with provided socket addresses {}", addresses);

        super.initializeLocalAddresses(addresses);

        // we are in startup so instanceName should be available in context
        final Object instanceName = ExternalContext.getExternalContextAttribute(ExternalContext.KEY_IGNITE_INSTANCE_NAME);
        this.instanceName = instanceName instanceof String ? (String) instanceName : null;

        final Map<InetSocketAddress, String> registrationIdByAddress = this.loadRemoteAddressesFromAttributes();
        this.localAddresses.addAll(addresses);

        // before storing our local addresses we clean up all old registrations that intersect with our current addresses
        for (final InetSocketAddress localAddress : addresses)
        {
            final String oldRegistrationId = registrationIdByAddress.get(localAddress);
            if (oldRegistrationId != null)
            {
                this.removeDiscoveryAddressesAttributes(oldRegistrationId);
            }
        }

        this.registerAddresses();

        LOGGER.debug("Completed local address initialisation");
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Collection<InetSocketAddress> getRegisteredAddresses() throws IgniteSpiException
    {
        final Collection<InetSocketAddress> addresses = new LinkedHashSet<>(this.addresses);
        return addresses;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void registerAddresses(final Collection<InetSocketAddress> addresses) throws IgniteSpiException
    {
        ParameterCheck.mandatoryCollection("addresses", addresses);
        this.addresses.addAll(addresses);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void unregisterAddresses(final Collection<InetSocketAddress> addresses) throws IgniteSpiException
    {
        ParameterCheck.mandatoryCollection("addresses", addresses);
        this.addresses.removeAll(addresses);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refreshAddressRegistration()
    {
        LOGGER.info("Refreshing address registration");
        this.registerAddresses();
        final String keepRegistrationId = this.effectiveRegistrationId;
        this.removeAddressRegistrations(keepRegistrationId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerAddresses()
    {
        if (this.instanceName != null && !this.localAddresses.isEmpty())
        {
            final TimeZone tz = TimeZone.getTimeZone("UTC");
            final Calendar cal = Calendar.getInstance(tz, Locale.ENGLISH);
            final long utcMillisNow = cal.getTimeInMillis();

            this.effectiveRegistrationId = String.valueOf(utcMillisNow) + "@" + this.registrationUuid;

            AuthenticationUtil.runAsSystem(() -> {
                this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {

                    LOGGER.debug(
                            "About to add local/public addresses {} to registered Ignite instance members in instance {} using registration UUID {}",
                            this.localAddresses, this.instanceName, this.effectiveRegistrationId);

                    final Serializable[] keys = new Serializable[] { ALDICA_IGNITE_GRID_MEMBERS, this.instanceName,
                            this.effectiveRegistrationId };

                    final Ignite ignite = Ignition.ignite(this.instanceName);
                    final String localHost = ignite != null ? ignite.configuration().getLocalHost() : null;

                    if (this.localAddresses.size() == 1)
                    {
                        final InetSocketAddress localAddress = this.localAddresses.iterator().next();
                        if (localAddress.isUnresolved() || !localAddress.getAddress().isLoopbackAddress())
                        {
                            LOGGER.trace("Registering local address {}", localAddress);
                            final String addressString = this.addressToString(localAddress);
                            this.attributeService.createAttribute(addressString, keys);
                        }
                        else if (localHost != null)
                        {
                            final InetSocketAddress constructedLocalAddress = new InetSocketAddress(localHost, localAddress.getPort());
                            LOGGER.trace("Registering local address {} constructed from loopback address {} and configured local host name",
                                    constructedLocalAddress, localAddress);
                            final String addressString = this.addressToString(constructedLocalAddress);
                            this.attributeService.createAttribute(addressString, keys);
                        }
                        else
                        {
                            LOGGER.warn(
                                    "The local address {} is a loopback address and Ignite configuration does not contain a local host to register for other cluster members to auto-discover this node",
                                    localAddress);
                        }
                    }
                    else
                    {
                        final Collection<String> addressValues = new LinkedHashSet<>();
                        for (final InetSocketAddress localAddress : this.localAddresses)
                        {
                            if (localAddress.isUnresolved() || !localAddress.getAddress().isLoopbackAddress())
                            {
                                LOGGER.trace("Registering local address {}", localAddress);
                                final String addressString = this.addressToString(localAddress);
                                addressValues.add(addressString);
                            }
                            else if (localHost != null)
                            {
                                final InetSocketAddress constructedLocalAddress = new InetSocketAddress(localHost, localAddress.getPort());
                                final String addressString = this.addressToString(constructedLocalAddress);
                                if (addressValues.add(addressString))
                                {
                                    LOGGER.trace(
                                            "Registering local address {} constructed from loopback address {} and configured local host name",
                                            constructedLocalAddress, localAddress);
                                }
                            }
                        }

                        if (!addressValues.isEmpty())
                        {
                            this.attributeService.createAttribute(new ArrayList<>(addressValues), keys);
                            LOGGER.info("Registered local addresses {} with registration ID {}", addressValues,
                                    this.effectiveRegistrationId);
                        }
                        else
                        {
                            LOGGER.warn(
                                    "The local addresses {} are all loopback addresses and Ignite configuration does not contain a local host to register for other cluster members to auto-discover this node",
                                    this.localAddresses);
                        }
                    }

                    return null;
                }, false);

                return null;
            });

            if (ExternalContext.hasActiveContext())
            {
                ExternalContext.registerFailureHandler((t) -> {
                    LOGGER.info("Cleaning up after Ignite operation error", t);
                    this.removeDiscoveryAddressesAttributes(this.effectiveRegistrationId);
                }, true);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAddressRegistrations()
    {
        this.removeAddressRegistrations(new String[0]);
    }

    protected void removeAddressRegistrations(final String... excludeRegistrationIds)
    {
        if (this.instanceName != null)
        {
            final Set<String> registrationIdExclusions = excludeRegistrationIds != null
                    ? new HashSet<>(Arrays.asList(excludeRegistrationIds))
                    : Collections.emptySet();

            final List<Serializable[]> keysToDelete = new ArrayList<>();

            AuthenticationUtil.runAsSystem(() -> {
                this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {

                    final Serializable[] keys = new Serializable[] { ALDICA_IGNITE_GRID_MEMBERS, this.instanceName };

                    this.attributeService.getAttributes((id, value, entryKeys) -> {

                        if (entryKeys.length == 3)
                        {
                            final String effectiveRegistrationId = String.valueOf(entryKeys[2]);
                            if (!registrationIdExclusions.contains(effectiveRegistrationId))
                            {
                                final int separatorIndex = effectiveRegistrationId.indexOf('@');
                                final String registrationUuid = effectiveRegistrationId.substring(separatorIndex + 1);
                                if (EqualsHelper.nullSafeEquals(this.registrationUuid, registrationUuid))
                                {
                                    LOGGER.debug("Marking registration with UUID {} for deletion", effectiveRegistrationId);
                                    keysToDelete.add(entryKeys);
                                }
                            }
                        }

                        return true;
                    }, keys);

                    for (final Serializable[] keysForDeletion : keysToDelete)
                    {
                        this.attributeService.removeAttribute(keysForDeletion);
                    }

                    LOGGER.info("Deleted {} address registrations", keysToDelete.size());

                    return null;
                }, false);

                return null;
            });
        }
    }

    protected InetSocketAddress stringToAddress(final String addressStr)
    {
        final int aSeparatorIndex = addressStr.lastIndexOf(':');
        InetSocketAddress address;
        if (aSeparatorIndex != -1)
        {
            final String hostStr = addressStr.substring(0, aSeparatorIndex);
            final int port = Integer.parseInt(addressStr.substring(aSeparatorIndex + 1));
            address = new InetSocketAddress(hostStr, port);
            Collection<InetSocketAddress> externalAddresses;
            try
            {
                externalAddresses = this.addressResolver.getExternalAddresses(address);
            }
            catch (final IgniteCheckedException ice)
            {
                LOGGER.warn("Error resolving registered address {}", address, ice);
                externalAddresses = Collections.emptyList();
            }
            address = externalAddresses.size() > 0 ? externalAddresses.iterator().next() : address;
        }
        else
        {
            address = null;
        }
        return address;
    }

    protected String addressToString(final InetSocketAddress localAddress)
    {
        final String hostString = localAddress.getHostString();
        final int port = localAddress.getPort();
        final String addressString = hostString + ":" + String.valueOf(port);
        return addressString;
    }

    protected Map<InetSocketAddress, String> loadRemoteAddressesFromAttributes()
    {
        final Map<InetSocketAddress, String> registrationIdByAddress = new HashMap<>();

        if (this.instanceName != null)
        {
            final TimeZone tz = TimeZone.getTimeZone("UTC");
            final Calendar cal = Calendar.getInstance(tz, Locale.ENGLISH);
            cal.add(Calendar.DATE, -this.registrationCutOffAgeDays);
            final long utcMillisCutOff = cal.getTimeInMillis();

            AuthenticationUtil.runAsSystem(() -> {
                this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {

                    final Serializable[] keys = new Serializable[] { ALDICA_IGNITE_GRID_MEMBERS, this.instanceName };

                    final Collection<InetSocketAddress> addresses = new LinkedHashSet<>();

                    this.attributeService.getAttributes((id, value, entryKeys) -> {
                        final boolean doContinue = true;

                        if (entryKeys.length == 3)
                        {
                            final String effectiveRegistrationId = String.valueOf(entryKeys[2]);
                            final int separatorIndex = effectiveRegistrationId.indexOf('@');

                            final long timestamp = Long.parseLong(effectiveRegistrationId.substring(0, separatorIndex));
                            final String registrationUuid = effectiveRegistrationId.substring(separatorIndex + 1);

                            if (timestamp < utcMillisCutOff)
                            {
                                LOGGER.debug("Ignoring Ignite instance member registration {} before cutOff timestamp {}", registrationUuid,
                                        utcMillisCutOff);

                                if (value instanceof Collection<?>)
                                {
                                    this.processMultiAddressRegistration(effectiveRegistrationId, value, null, registrationIdByAddress);
                                }
                                else if (value instanceof String)
                                {
                                    this.processAddressRegistration(effectiveRegistrationId, value, null, registrationIdByAddress);
                                }
                            }
                            else if (!this.registrationUuid.equals(registrationUuid))
                            {
                                if (value instanceof Collection<?>)
                                {
                                    LOGGER.debug("Processing multi-address Ignite instance member registration {} for {}", registrationUuid,
                                            value);
                                    this.processMultiAddressRegistration(effectiveRegistrationId, value, addresses,
                                            registrationIdByAddress);
                                }
                                else if (value instanceof String)
                                {
                                    LOGGER.debug("Processing single-address Ignite instance member registration {} for {}",
                                            registrationUuid, value);
                                    this.processAddressRegistration(effectiveRegistrationId, value, addresses, registrationIdByAddress);
                                }
                                else
                                {
                                    LOGGER.warn("Unprocessable Ignite instance member registration {} value: {}", registrationUuid, value);
                                }
                            }
                        }

                        return doContinue;
                    }, keys);

                    if (!addresses.isEmpty())
                    {
                        LOGGER.debug("Adding remote addresses {} read from attributes to addresses for member discovery", addresses);
                        this.registerAddresses(addresses);
                    }

                    return null;
                }, true);

                return null;
            });
        }

        return registrationIdByAddress;
    }

    protected void processMultiAddressRegistration(final String effectiveRegistrationId, final Object value,
            final Collection<InetSocketAddress> addresses, final Map<InetSocketAddress, String> registrationIdByAddress)
    {
        for (final Object element : (Collection<?>) value)
        {
            this.processAddressRegistration(effectiveRegistrationId, element, addresses, registrationIdByAddress);
        }
    }

    protected void processAddressRegistration(final String effectiveRegistrationId, final Object value,
            final Collection<InetSocketAddress> addresses, final Map<InetSocketAddress, String> registrationIdByAddress)
    {
        final String addressStr = String.valueOf(value);
        final InetSocketAddress address = this.stringToAddress(addressStr);
        if (address != null)
        {
            LOGGER.debug("Processing address {} for registration ID {}", address, effectiveRegistrationId);
            if (addresses != null)
            {
                addresses.add(address);
            }
            if (registrationIdByAddress != null)
            {
                registrationIdByAddress.put(address, effectiveRegistrationId);
            }
        }
        else
        {
            LOGGER.debug("Address value {} for registration ID {} cannot be mapped to a socket address", value, effectiveRegistrationId);
        }
    }

    protected void removeDiscoveryAddressesAttributes(final String registrationId)
    {
        if (this.instanceName != null)
        {
            AuthenticationUtil.runAsSystem(() -> {
                this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
                    final Serializable[] keys = new Serializable[] { ALDICA_IGNITE_GRID_MEMBERS, this.instanceName, registrationId };
                    this.attributeService.removeAttribute(keys);

                    return null;
                }, false);

                return null;
            });
        }
    }
}
