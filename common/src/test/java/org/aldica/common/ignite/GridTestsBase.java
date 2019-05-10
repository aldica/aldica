/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite;

import java.util.Arrays;
import java.util.Collections;

import org.aldica.common.ignite.discovery.CredentialsAwareTcpDiscoverySpi;
import org.aldica.common.ignite.plugin.SimpleSecurityPluginConfiguration;
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

    protected static IgniteConfiguration createConfiguration(final int no, final boolean assumeExisting,
            final SecurityCredentials primaryCredentials, final SecurityCredentials... validCredentials)
    {
        final IgniteConfiguration conf = new IgniteConfiguration();
        conf.setIgniteInstanceName("testGrid" + no);
        conf.setGridLogger(new Slf4jLogger());
        conf.setLocalHost(TEST_HOST);

        conf.setCommunicationSpi(createCommunicationSpi());
        conf.setDiscoverySpi(createDiscoverySpi(primaryCredentials, assumeExisting));

        if (validCredentials.length > 0)
        {
            final SimpleSecurityPluginConfiguration secConf = new SimpleSecurityPluginConfiguration();
            secConf.setEnabled(true);
            secConf.setAllowedNodeCredentials(Arrays.asList(validCredentials));
            conf.setPluginConfigurations(secConf);
        }
        else if (primaryCredentials != null)
        {
            final SimpleSecurityPluginConfiguration secConf = new SimpleSecurityPluginConfiguration();
            secConf.setEnabled(true);
            secConf.setAllowedNodeCredentials(Collections.singleton(primaryCredentials));
            conf.setPluginConfigurations(secConf);
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
