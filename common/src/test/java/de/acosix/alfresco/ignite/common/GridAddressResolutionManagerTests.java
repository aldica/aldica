package de.acosix.alfresco.ignite.common;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Random;
import java.util.UUID;

import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import de.acosix.alfresco.ignite.common.discovery.GridAddressResolutionManager;

/**
 *
 * @author Axel Faust
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class GridAddressResolutionManagerTests
{

    @Test
    public void externalHostNameMapping() throws Exception
    {
        final String externalHostName = "dummyExternalHost";

        final IgniteConfiguration conf = new IgniteConfiguration();
        conf.setIgniteInstanceName(UUID.randomUUID().toString());
        final GridAddressResolutionManager resolutionManager = new GridAddressResolutionManager();
        resolutionManager.setConfiguration(conf);
        resolutionManager.setExternalHost(externalHostName);

        final InetAddress localHost = IgniteUtils.getLocalHost();
        final IgniteBiTuple<Collection<String>, Collection<String>> localAddresses = IgniteUtils.resolveLocalAddresses(localHost);

        resolutionManager.afterPropertiesSet();
        resolutionManager.beforeInstanceStartup(conf.getIgniteInstanceName());

        if (!(localHost.isLoopbackAddress() || localHost.isAnyLocalAddress()))
        {
            final Collection<InetSocketAddress> externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), 0));
            Assert.assertTrue("Primary local address should have been mapped to external host",
                    externalAddresses.contains(new InetSocketAddress(externalHostName, 0)));
        }

        localAddresses.get2().stream().forEach(locHost -> {
            try
            {
                final Collection<InetSocketAddress> externalAddresses = resolutionManager
                        .getExternalAddresses(new InetSocketAddress(locHost, 0));
                Assert.assertTrue("Local address " + locHost + " should have been mapped to external host",
                        externalAddresses.contains(new InetSocketAddress(externalHostName, 0)));
            }
            catch (final Exception e)
            {
                Assert.fail("Failed to resolve external addresses: " + e.getMessage());
            }
        });
    }

    @Test
    public void externalHostAddressMapping() throws Exception
    {
        final String externalHostAddress = "199.200.201.202";

        final IgniteConfiguration conf = new IgniteConfiguration();
        conf.setIgniteInstanceName(UUID.randomUUID().toString());
        final GridAddressResolutionManager resolutionManager = new GridAddressResolutionManager();
        resolutionManager.setConfiguration(conf);
        resolutionManager.setExternalHost(externalHostAddress);

        final InetAddress localHost = IgniteUtils.getLocalHost();
        final IgniteBiTuple<Collection<String>, Collection<String>> localAddresses = IgniteUtils.resolveLocalAddresses(localHost);

        resolutionManager.afterPropertiesSet();
        resolutionManager.beforeInstanceStartup(conf.getIgniteInstanceName());

        if (!(localHost.isLoopbackAddress() || localHost.isAnyLocalAddress()))
        {
            final Collection<InetSocketAddress> externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostAddress(), 0));
            Assert.assertTrue("Primary local address should have been mapped to external address",
                    externalAddresses.contains(new InetSocketAddress(externalHostAddress, 0)));
        }

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
        }).forEach(locAddr -> {
            try
            {
                final Collection<InetSocketAddress> externalAddresses = resolutionManager
                        .getExternalAddresses(new InetSocketAddress(locAddr, 0));
                Assert.assertTrue("Local address " + locAddr + " should have been mapped to external address",
                        externalAddresses.contains(new InetSocketAddress(externalHostAddress, 0)));
            }
            catch (final Exception e)
            {
                Assert.fail("Failed to resolve external addresses: " + e.getMessage());
            }
        });
    }

    @Test
    public void externalHostNameSocketMappingDefaultPorts() throws Exception
    {
        final String externalHostName = "dummyExternalHost";

        final IgniteConfiguration conf = new IgniteConfiguration();
        conf.setIgniteInstanceName(UUID.randomUUID().toString());

        final GridAddressResolutionManager resolutionManager = new GridAddressResolutionManager();
        resolutionManager.setConfiguration(conf);
        resolutionManager.setExternalHost(externalHostName);
        resolutionManager.setExternalCommPortBase(1234);
        resolutionManager.setExternalDiscoPortBase(2345);
        resolutionManager.setExternalTimePortBase(3456);

        final InetAddress localHost = IgniteUtils.getLocalHost();
        final IgniteBiTuple<Collection<String>, Collection<String>> localAddresses = IgniteUtils.resolveLocalAddresses(localHost);

        resolutionManager.afterPropertiesSet();
        resolutionManager.beforeInstanceStartup(conf.getIgniteInstanceName());

        final Random rng = new SecureRandom();
        if (!(localHost.isLoopbackAddress() || localHost.isAnyLocalAddress()))
        {
            Collection<InetSocketAddress> externalAddresses;
            int chosenOffset;

            externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), TcpCommunicationSpi.DFLT_PORT));
            Assert.assertTrue(
                    "Primary local host socket address with default communication port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostName, 1234)));
            chosenOffset = rng.nextInt(TcpCommunicationSpi.DFLT_PORT_RANGE);
            externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), TcpCommunicationSpi.DFLT_PORT + chosenOffset));
            Assert.assertTrue(
                    "Primary local host socket address with offset communication port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostName, 1234 + chosenOffset)));

            externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), TcpDiscoverySpi.DFLT_PORT));
            Assert.assertTrue(
                    "Primary local host socket address with default discovery port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostName, 2345)));
            chosenOffset = rng.nextInt(TcpDiscoverySpi.DFLT_PORT_RANGE);
            externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), TcpDiscoverySpi.DFLT_PORT + chosenOffset));
            Assert.assertTrue(
                    "Primary local host socket address with offset discovery port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostName, 2345 + chosenOffset)));

            externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), IgniteConfiguration.DFLT_TIME_SERVER_PORT_BASE));
            Assert.assertTrue(
                    "Primary local host socket address with default time port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostName, 3456)));
            chosenOffset = rng.nextInt(IgniteConfiguration.DFLT_TIME_SERVER_PORT_RANGE);
            externalAddresses = resolutionManager.getExternalAddresses(
                    new InetSocketAddress(localHost.getHostName(), IgniteConfiguration.DFLT_TIME_SERVER_PORT_BASE + chosenOffset));
            Assert.assertTrue(
                    "Primary local host socket address with offset time port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostName, 3456 + chosenOffset)));
        }

        localAddresses.get2().stream().forEach(locHost -> {
            try
            {
                Collection<InetSocketAddress> externalAddresses;
                int chosenOffset;

                externalAddresses = resolutionManager.getExternalAddresses(new InetSocketAddress(locHost, TcpCommunicationSpi.DFLT_PORT));
                Assert.assertTrue(
                        "Local address " + locHost
                                + " socket with default communication port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostName, 1234)));
                chosenOffset = rng.nextInt(TcpCommunicationSpi.DFLT_PORT_RANGE);
                externalAddresses = resolutionManager
                        .getExternalAddresses(new InetSocketAddress(locHost, TcpCommunicationSpi.DFLT_PORT + chosenOffset));
                Assert.assertTrue(
                        "Local address " + locHost
                                + " socket with offset communication port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostName, 1234 + chosenOffset)));

                externalAddresses = resolutionManager.getExternalAddresses(new InetSocketAddress(locHost, TcpDiscoverySpi.DFLT_PORT));
                Assert.assertTrue(
                        "Local address " + locHost
                                + " socket with default discovery port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostName, 2345)));
                chosenOffset = rng.nextInt(TcpDiscoverySpi.DFLT_PORT_RANGE);
                externalAddresses = resolutionManager
                        .getExternalAddresses(new InetSocketAddress(locHost, TcpDiscoverySpi.DFLT_PORT + chosenOffset));
                Assert.assertTrue(
                        "Local address " + locHost
                                + " socket with offset discovery port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostName, 2345 + chosenOffset)));

                externalAddresses = resolutionManager
                        .getExternalAddresses(new InetSocketAddress(locHost, IgniteConfiguration.DFLT_TIME_SERVER_PORT_BASE));
                Assert.assertTrue(
                        "Local address " + locHost
                                + " socket with default time port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostName, 3456)));
                chosenOffset = rng.nextInt(IgniteConfiguration.DFLT_TIME_SERVER_PORT_RANGE);
                externalAddresses = resolutionManager.getExternalAddresses(
                        new InetSocketAddress(locHost, IgniteConfiguration.DFLT_TIME_SERVER_PORT_BASE + chosenOffset));
                Assert.assertTrue(
                        "Local address " + locHost
                                + " socket with offset time port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostName, 3456 + chosenOffset)));
            }
            catch (final Exception e)
            {
                Assert.fail("Failed to resolve external addresses: " + e.getMessage());
            }
        });
    }

    @Test
    public void externalHostAddressSocketMappingDefaultPorts() throws Exception
    {
        final String externalHostAddress = "199.200.201.202";

        final IgniteConfiguration conf = new IgniteConfiguration();
        conf.setIgniteInstanceName(UUID.randomUUID().toString());
        final GridAddressResolutionManager resolutionManager = new GridAddressResolutionManager();
        resolutionManager.setConfiguration(conf);
        resolutionManager.setExternalHost(externalHostAddress);
        resolutionManager.setExternalCommPortBase(1234);
        resolutionManager.setExternalDiscoPortBase(2345);
        resolutionManager.setExternalTimePortBase(3456);

        final InetAddress localHost = IgniteUtils.getLocalHost();
        final IgniteBiTuple<Collection<String>, Collection<String>> localAddresses = IgniteUtils.resolveLocalAddresses(localHost);

        resolutionManager.afterPropertiesSet();
        resolutionManager.beforeInstanceStartup(conf.getIgniteInstanceName());

        final Random rng = new SecureRandom();
        if (!(localHost.isLoopbackAddress() || localHost.isAnyLocalAddress()))
        {
            Collection<InetSocketAddress> externalAddresses;
            int chosenOffset;

            externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), TcpCommunicationSpi.DFLT_PORT));
            Assert.assertTrue(
                    "Primary local host socket address with default communication port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostAddress, 1234)));
            chosenOffset = rng.nextInt(TcpCommunicationSpi.DFLT_PORT_RANGE);
            externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), TcpCommunicationSpi.DFLT_PORT + chosenOffset));
            Assert.assertTrue(
                    "Primary local host socket address with offset communication port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostAddress, 1234 + chosenOffset)));

            externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), TcpDiscoverySpi.DFLT_PORT));
            Assert.assertTrue(
                    "Primary local host socket address with default discovery port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostAddress, 2345)));
            chosenOffset = rng.nextInt(TcpDiscoverySpi.DFLT_PORT_RANGE);
            externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), TcpDiscoverySpi.DFLT_PORT + chosenOffset));
            Assert.assertTrue(
                    "Primary local host socket address with offset discovery port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostAddress, 2345 + chosenOffset)));

            externalAddresses = resolutionManager
                    .getExternalAddresses(new InetSocketAddress(localHost.getHostName(), IgniteConfiguration.DFLT_TIME_SERVER_PORT_BASE));
            Assert.assertTrue(
                    "Primary local host socket address with default time port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostAddress, 3456)));
            chosenOffset = rng.nextInt(IgniteConfiguration.DFLT_TIME_SERVER_PORT_RANGE);
            externalAddresses = resolutionManager.getExternalAddresses(
                    new InetSocketAddress(localHost.getHostName(), IgniteConfiguration.DFLT_TIME_SERVER_PORT_BASE + chosenOffset));
            Assert.assertTrue(
                    "Primary local host socket address with offset time port should have been mapped to external host socket address",
                    externalAddresses.contains(new InetSocketAddress(externalHostAddress, 3456 + chosenOffset)));
        }

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
        }).forEach(locAddr -> {
            try
            {
                Collection<InetSocketAddress> externalAddresses;
                int chosenOffset;

                externalAddresses = resolutionManager.getExternalAddresses(new InetSocketAddress(locAddr, TcpCommunicationSpi.DFLT_PORT));
                Assert.assertTrue(
                        "Local address " + locAddr
                                + " socket with default communication port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostAddress, 1234)));
                chosenOffset = rng.nextInt(TcpCommunicationSpi.DFLT_PORT_RANGE);
                externalAddresses = resolutionManager
                        .getExternalAddresses(new InetSocketAddress(locAddr, TcpCommunicationSpi.DFLT_PORT + chosenOffset));
                Assert.assertTrue(
                        "Local address " + locAddr
                                + " socket with offset communication port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostAddress, 1234 + chosenOffset)));

                externalAddresses = resolutionManager.getExternalAddresses(new InetSocketAddress(locAddr, TcpDiscoverySpi.DFLT_PORT));
                Assert.assertTrue(
                        "Local address " + locAddr
                                + " socket with default discovery port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostAddress, 2345)));
                chosenOffset = rng.nextInt(TcpDiscoverySpi.DFLT_PORT_RANGE);
                externalAddresses = resolutionManager
                        .getExternalAddresses(new InetSocketAddress(locAddr, TcpDiscoverySpi.DFLT_PORT + chosenOffset));
                Assert.assertTrue(
                        "Local address " + locAddr
                                + " socket with offset discovery port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostAddress, 2345 + chosenOffset)));

                externalAddresses = resolutionManager
                        .getExternalAddresses(new InetSocketAddress(locAddr, IgniteConfiguration.DFLT_TIME_SERVER_PORT_BASE));
                Assert.assertTrue(
                        "Local address " + locAddr
                                + " socket with default time port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostAddress, 3456)));
                chosenOffset = rng.nextInt(IgniteConfiguration.DFLT_TIME_SERVER_PORT_RANGE);
                externalAddresses = resolutionManager.getExternalAddresses(
                        new InetSocketAddress(locAddr, IgniteConfiguration.DFLT_TIME_SERVER_PORT_BASE + chosenOffset));
                Assert.assertTrue(
                        "Local address " + locAddr
                                + " socket with offset time port should have been mapped to external host socket address",
                        externalAddresses.contains(new InetSocketAddress(externalHostAddress, 3456 + chosenOffset)));
            }
            catch (final Exception e)
            {
                Assert.fail("Failed to resolve external addresses: " + e.getMessage());
            }
        });
    }
}
