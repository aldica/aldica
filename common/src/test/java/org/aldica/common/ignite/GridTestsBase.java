/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.aldica.common.ignite.discovery.CredentialsAwareTcpDiscoverySpi;
import org.aldica.common.ignite.plugin.SimpleSecurityPluginConfiguration;
import org.aldica.common.ignite.plugin.SimpleSecurityPluginProvider;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.plugin.security.SecurityCredentials;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

/**
 *
 * @author Axel Faust
 */
public abstract class GridTestsBase
{

    private static final String TEST_HOST = "127.0.0.1";

    private static final int TEST_COMM_PORT = 10000;

    private static final int TEST_DISCO_PORT = 10100;

    private static final int TEST_PORT_RANGE = 100;

    private static final int DUMMY_TIMEOUT = 3000;

    protected static IgniteConfiguration createConfiguration(final int no, final boolean assumeExisting)
    {
        return createConfiguration(no, assumeExisting, null);
    }

    protected static IgniteConfiguration createConfiguration(final int no, final boolean assumeExisting,
            final SecurityCredentials primaryCredentials)
    {
        return createConfiguration(no, assumeExisting, primaryCredentials, null);
    }

    protected static IgniteConfiguration createConfiguration(final int no, final boolean assumeExisting,
            final SecurityCredentials primaryCredentials, final Collection<SecurityCredentials> validCredentials)
    {
        return createConfiguration(no, assumeExisting, primaryCredentials, validCredentials, null, null, null);
    }

    protected static IgniteConfiguration createConfiguration(final int no,
            final boolean assumeExisting,
            final SecurityCredentials primaryCredentials, final Collection<SecurityCredentials> validCredentials,
            final String nodeTierAttributeKey, final String nodeTierAttributeValue, final Collection<String> allowedNodeTierAttributeValues)
    {
        final IgniteConfiguration conf = new IgniteConfiguration();
        conf.setIgniteInstanceName("testGrid" + no);
        conf.setGridLogger(new Slf4jLogger());
        conf.setLocalHost(TEST_HOST);
        conf.setWorkDirectory(Paths.get("target", "IgniteWork").toAbsolutePath().toString());

        conf.setCommunicationSpi(createCommunicationSpi());
        conf.setDiscoverySpi(createDiscoverySpi(primaryCredentials, assumeExisting));

        final SimpleSecurityPluginConfiguration secConf = new SimpleSecurityPluginConfiguration();
        if (validCredentials != null)
        {
            secConf.setEnabled(true);
            secConf.setAllowedNodeCredentials(validCredentials);
        }
        else if (primaryCredentials != null)
        {
            secConf.setEnabled(true);
            secConf.setAllowedNodeCredentials(Collections.singleton(primaryCredentials));
        }

        secConf.setNodeTierAttributeKey(nodeTierAttributeKey);
        secConf.setAllowedNodeTierAttributeValues(allowedNodeTierAttributeValues);

        if (nodeTierAttributeKey != null)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> userAttributes = (Map<String, Object>) conf.getUserAttributes();
            if (userAttributes == null)
            {
                userAttributes = new HashMap<>();
                conf.setUserAttributes(userAttributes);
            }
            userAttributes.put(nodeTierAttributeKey, nodeTierAttributeValue);
        }

        if (secConf.isEnabled())
        {
            final SimpleSecurityPluginProvider secProvider = new SimpleSecurityPluginProvider();
            secProvider.setConfiguration(secConf);
            conf.setPluginProviders(secProvider);
        }

        return conf;
    }

    protected static TcpCommunicationSpi createCommunicationSpi()
    {
        final TcpCommunicationSpi spi = new TcpCommunicationSpi();
        spi.setLocalPort(TEST_COMM_PORT);
        spi.setLocalPortRange(TEST_PORT_RANGE);
        return spi;
    }

    protected static TcpDiscoverySpi createDiscoverySpi(final SecurityCredentials credentials, final boolean assumeExistingMember)
    {
        TcpDiscoverySpi spi;

        if (credentials != null)
        {
            spi = new CredentialsAwareTcpDiscoverySpi();
            ((CredentialsAwareTcpDiscoverySpi) spi).setCredentials(credentials);
        }
        else
        {
            spi = new TcpDiscoverySpi();
        }

        spi.setLocalPort(TEST_DISCO_PORT);
        spi.setLocalPortRange(TEST_PORT_RANGE);
        spi.setJoinTimeout(DUMMY_TIMEOUT);
        spi.setNetworkTimeout(DUMMY_TIMEOUT);
        spi.setSocketTimeout(DUMMY_TIMEOUT);
        spi.setAckTimeout(DUMMY_TIMEOUT);

        final TcpDiscoveryVmIpFinder discoIpFinder = new TcpDiscoveryVmIpFinder();
        discoIpFinder.setShared(true);
        if (assumeExistingMember)
        {
            discoIpFinder.setAddresses(Arrays.asList(TEST_HOST + ":" + TEST_DISCO_PORT));
        }
        spi.setIpFinder(discoIpFinder);

        return spi;
    }
}
