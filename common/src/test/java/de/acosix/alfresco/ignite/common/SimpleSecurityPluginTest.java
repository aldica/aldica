/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.common;

import java.util.Arrays;
import java.util.Collections;

import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.plugin.security.SecurityCredentials;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.hamcrest.core.StringStartsWith;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import de.acosix.alfresco.ignite.common.discovery.CredentialsAwareTcpDiscoverySpi;
import de.acosix.alfresco.ignite.common.plugin.SimpleSecurityPluginConfiguration;

/**
 *
 * @author Axel Faust
 */
public class SimpleSecurityPluginTest
{

    private static final String TEST_HOST = "127.0.0.1";

    private static final int TEST_COMM_PORT = 10000;

    private static final int TEST_DISCO_PORT = 10100;

    private static final int TEST_PORT_RANGE = 100;

    private static final int DUMMY_TIMEOUT = 3000;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void joinWithNewNodeMissingCredentials()
    {
        try
        {
            final SecurityCredentials credentials = new SecurityCredentials("login", "pass");

            final IgniteConfiguration conf1 = createConfiguration(1, false, credentials);
            final IgniteConfiguration conf2 = createConfiguration(2, true, null);

            Ignition.start(conf1);

            this.thrown.expect(IgniteException.class);
            this.thrown.expectMessage(StringStartsWith.startsWith("Failed to start manager:"));
            // ExpectedException.expectCause only works for first-level causes - SecurityException is nested multiple times
            Ignition.start(conf2);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void joinWithExistingNodeMissingSecurityConfig()
    {
        try
        {
            final SecurityCredentials credentials = new SecurityCredentials("login", "pass");

            final IgniteConfiguration conf1 = createConfiguration(1, false, null);
            final IgniteConfiguration conf2 = createConfiguration(2, true, credentials);
            final IgniteConfiguration conf3 = createConfiguration(3, false, null);

            Ignition.start(conf1);

            // with existing node missing any security configuration, additional nodes can always join
            Ignition.start(conf2);
            Ignition.start(conf3);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void joinWithValidCredentials()
    {
        try
        {
            final SecurityCredentials credentials1 = new SecurityCredentials("login1", "pass1");
            final SecurityCredentials credentials2 = new SecurityCredentials("login2", "pass2");

            final IgniteConfiguration conf1 = createConfiguration(1, false, credentials1, credentials1, credentials2);
            final IgniteConfiguration conf2 = createConfiguration(2, true, credentials1, credentials1, credentials2);
            final IgniteConfiguration conf3 = createConfiguration(3, true, credentials2, credentials1, credentials2);

            Ignition.start(conf1);
            Ignition.start(conf2);
            Ignition.start(conf3);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void joinWithInvalidCredentials()
    {
        try
        {
            final SecurityCredentials credentials1 = new SecurityCredentials("login", "pass1");
            final SecurityCredentials credentials2 = new SecurityCredentials("login", "pass2");

            final IgniteConfiguration conf1 = createConfiguration(1, false, credentials1);
            final IgniteConfiguration conf2 = createConfiguration(2, true, credentials2);

            Ignition.start(conf1);

            this.thrown.expect(IgniteException.class);
            this.thrown.expectMessage(StringStartsWith.startsWith("Failed to start manager:"));
            // ExpectedException.expectCause only works for first-level causes - SecurityException is nested multiple times
            Ignition.start(conf2);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    private static IgniteConfiguration createConfiguration(final int no, final boolean assumeExisting,
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

    private static TcpCommunicationSpi createCommunicationSpi()
    {
        final TcpCommunicationSpi spi = new TcpCommunicationSpi();
        spi.setLocalPort(TEST_COMM_PORT);
        spi.setLocalPortRange(TEST_PORT_RANGE);
        return spi;
    }

    private static TcpDiscoverySpi createDiscoverySpi(final SecurityCredentials credentials, final boolean assumeExistingMember)
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
