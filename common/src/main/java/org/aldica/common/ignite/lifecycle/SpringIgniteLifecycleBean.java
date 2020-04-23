/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.lifecycle;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.UUID;

import org.aldica.common.ignite.context.ExternalContext;
import org.alfresco.util.PropertyCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.DiscoveryEvent;
import org.apache.ignite.events.EventType;
import org.apache.ignite.plugin.segmentation.SegmentationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * This lifecycle bean initialises an Ignite grid when the Spring application context has been bootstrapped and shuts it down when the
 * context is about to be stopped.
 *
 * @author Axel Faust
 */
public class SpringIgniteLifecycleBean implements InitializingBean, ApplicationListener<ApplicationEvent>, ApplicationContextAware
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringIgniteLifecycleBean.class);

    protected boolean enabled;

    protected ApplicationContext applicationContext;

    protected IgniteConfiguration configuration;

    protected Ignite ignite;

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() throws Exception
    {
        PropertyCheck.mandatory(this, "configuration", this.configuration);
    }

    @Override
    public void onApplicationEvent(final ApplicationEvent event)
    {
        if (event instanceof ContextRefreshedEvent)
        {
            final ContextRefreshedEvent refreshEvent = (ContextRefreshedEvent) event;
            final ApplicationContext refreshContext = refreshEvent.getApplicationContext();
            if (refreshContext != null && refreshContext.equals(this.applicationContext))
            {
                this.initializeGrid();
            }
        }
        else if (event instanceof ContextClosedEvent)
        {
            final ContextClosedEvent closedEvent = (ContextClosedEvent) event;
            final ApplicationContext closedContext = closedEvent.getApplicationContext();
            if (closedContext != null && closedContext.equals(this.applicationContext))
            {
                this.shutdownGrid();
            }
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     * @param enabled
     *            the enabled to set
     */
    public void setEnabled(final boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * @param configuration
     *            the configuration to set
     */
    public void setConfiguration(final IgniteConfiguration configuration)
    {
        this.configuration = configuration;
    }

    protected void initializeGrid()
    {
        if (this.enabled && this.ignite == null)
        {
            final String instanceName = this.configuration.getIgniteInstanceName();
            final Collection<IgniteInstanceLifecycleAware> gridLifecycleAwareBeans = this.applicationContext
                    .getBeansOfType(IgniteInstanceLifecycleAware.class, true, false).values();

            synchronized (this)
            {
                ExternalContext.withExternalContext(() -> {
                    LOGGER.info("Starting Ignite instance {}", instanceName);
                    gridLifecycleAwareBeans.forEach(bean -> {
                        bean.beforeInstanceStartup(instanceName);
                    });

                    if (this.configuration.getConsistentId() == null || "".equals(this.configuration.getConsistentId()))
                    {
                        this.configuration.setConsistentId(UUID.randomUUID().toString());
                    }
                    this.ignite = Ignition.start(this.configuration);

                    this.setupGridEvents();

                    gridLifecycleAwareBeans.forEach(bean -> {
                        bean.afterInstanceStartup(instanceName);
                    });
                    LOGGER.info("Started Ignite instance {}", instanceName);
                    return null;
                }, Collections.singletonMap(ExternalContext.KEY_IGNITE_INSTANCE_NAME, instanceName));
            }
        }
    }

    protected void shutdownGrid()
    {
        if (this.ignite != null)
        {
            final String instaneName = this.configuration.getIgniteInstanceName();
            final Collection<IgniteInstanceLifecycleAware> gridLifecycleAwareBeans = this.applicationContext
                    .getBeansOfType(IgniteInstanceLifecycleAware.class, true, false).values();

            synchronized (this)
            {
                ExternalContext.withExternalContext(() -> {
                    LOGGER.info("Closing Ignite isntance {}", instaneName);
                    gridLifecycleAwareBeans.forEach(bean -> {
                        bean.beforeInstanceShutdown(instaneName);
                    });

                    Ignition.stop(this.ignite.name(), true);

                    gridLifecycleAwareBeans.forEach(bean -> {
                        bean.afterInstanceShutdown(instaneName);
                    });
                    LOGGER.info("Closed Ignite instance {}", instaneName);

                    this.ignite = null;

                    return null;
                }, Collections.singletonMap(ExternalContext.KEY_IGNITE_INSTANCE_NAME, instaneName));
            }
        }
    }

    protected void setupGridEvents()
    {
        this.logCurrentGridNodes(this.ignite.cluster().nodes());

        this.ignite.events().localListen(e -> {
            LOGGER.error(
                    "Local node has been determined to no longer be part of valid cluster group / network segment in Ignite instance {}.",
                    this.configuration.getIgniteInstanceName());
            if (this.ignite.configuration().getSegmentationPolicy() == SegmentationPolicy.NOOP)
            {
                LOGGER.warn(
                        "This node has been configured to remain active after a segmentation - this ensures operations can continue to be performed but can cause data inconsistency.");
                LOGGER.warn(
                        "It is highly recommended to properly restart the server at the earliest opportunity to properly rejoin the Ignite instance.");
            }
            return false;
        }, EventType.EVT_NODE_SEGMENTED);

        this.ignite.events().<DiscoveryEvent> remoteListen((uuid, e) -> {
            switch (e.type())
            {
                case EventType.EVT_NODE_JOINED:
                    LOGGER.info("Node {} (on {}) joined the Ignite instance {}",
                            new Object[] { e.eventNode().id(), e.eventNode().addresses(), this.configuration.getIgniteInstanceName() });
                    break;
                case EventType.EVT_NODE_LEFT:
                    LOGGER.info("Node {} (on {}) left the Ignite instance {}",
                            new Object[] { e.eventNode().id(), e.eventNode().addresses(), this.configuration.getIgniteInstanceName() });
                    break;
                case EventType.EVT_NODE_FAILED:
                    LOGGER.info("Node {} (on {}) failed within the Ignite instance {}",
                            new Object[] { e.eventNode().id(), e.eventNode().addresses(), this.configuration.getIgniteInstanceName() });
                    break;
                default:
                    LOGGER.debug("Received discovery event for which we did not register: {}", e);
            }

            final Collection<ClusterNode> topologyNodes = e.topologyNodes();
            this.logCurrentGridNodes(topologyNodes);

            return true;
        }, null, EventType.EVT_NODE_JOINED, EventType.EVT_NODE_LEFT, EventType.EVT_NODE_FAILED);
    }

    protected void logCurrentGridNodes(final Collection<ClusterNode> topologyNodes)
    {
        final Collection<String> addresses = new LinkedHashSet<>();
        for (final ClusterNode node : topologyNodes)
        {
            addresses.addAll(node.addresses());
        }

        LOGGER.info("Ignite instance {} currently has {} active nodes on addresses {}",
                new Object[] { this.configuration.getIgniteInstanceName(), Integer.valueOf(topologyNodes.size()), addresses });
    }
}
