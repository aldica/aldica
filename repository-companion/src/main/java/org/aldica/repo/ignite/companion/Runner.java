/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.companion;

import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Instances of this class provide the main entry-point of the stand-alone Repository-tier companion application.
 *
 * @author Axel Faust
 */
public class Runner
{

    private static final Logger LOGGER = LoggerFactory.getLogger(Runner.class);

    private final Properties runtimeOverrides = new Properties();

    private ClassPathXmlApplicationContext applicationContext;

    private Thread keepAliveThread;

    /**
     * Runs the companion application, starting a single instance and letting it run until the JVM is forcibly stopped via a kill signal.
     *
     * @param args
     *            the command line arguments
     */
    public static void main(final String[] args)
    {
        final Runner runner = new Runner();
        runner.start();
    }

    /**
     * Initialises a standard instance of the companion application runner.
     */
    public Runner()
    {
        // NO-OP
    }

    /**
     * Initialises a customised instance of the companion application runner, with runtime configuration values that override / supplement
     * configuration read from static files.
     *
     * @param configurationProperties
     *            the overriding / supplemental configuration properties to use in the instance
     */
    public Runner(final Map<String, String> configurationProperties)
    {
        if (configurationProperties == null)
        {
            throw new IllegalArgumentException("configurationProperties cannot be null");
        }
        configurationProperties.forEach((k, v) -> this.runtimeOverrides.setProperty(k, v));
    }

    /**
     * Starts this instance of the companion application.
     */
    public synchronized void start()
    {
        if (this.applicationContext != null)
        {
            throw new IllegalStateException("Instance is already started");
        }

        this.applicationContext = new ClassPathXmlApplicationContext(new String[] { "classpath:application-context.xml" }, false)
        {

            /**
             *
             * {@inheritDoc}
             */
            @Override
            protected void prepareBeanFactory(final ConfigurableListableBeanFactory beanFactory)
            {
                super.prepareBeanFactory(beanFactory);
                beanFactory.registerSingleton("runtime-overrides", Runner.this.runtimeOverrides);
            }
        };
        this.applicationContext.refresh();
        this.applicationContext.registerShutdownHook();

        this.keepAliveThread = new Thread("keep-alive-thread@" + this.hashCode())
        {

            /**
             *
             * {@inheritDoc}
             */
            @Override
            public void run()
            {
                try
                {
                    while (true)
                    {
                        Thread.sleep(Long.MAX_VALUE);
                    }
                }
                catch (final InterruptedException ex)
                {
                    LOGGER.debug("Interrupted during thread sleep - application is probably being forcibly shut down");
                }
            }
        };
        this.keepAliveThread.setDaemon(false);
        this.keepAliveThread.start();
    }

    /**
     * Stops this instance of the companion application.
     */
    public synchronized void stop()
    {
        if (this.applicationContext == null)
        {
            throw new IllegalStateException("Instance is not started");
        }

        this.keepAliveThread.interrupt();
        this.applicationContext.close();

        this.keepAliveThread = null;
        this.applicationContext = null;
    }
}
