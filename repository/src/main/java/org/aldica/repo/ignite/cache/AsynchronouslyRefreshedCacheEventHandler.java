/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.util.List;

import org.aldica.common.ignite.lifecycle.IgniteInstanceLifecycleAware;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.cache.AsynchronouslyRefreshedCache;
import org.alfresco.util.cache.AsynchronouslyRefreshedCacheRegistry;
import org.alfresco.util.cache.RefreshableCacheEvent;
import org.alfresco.util.cache.RefreshableCacheListener;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Instances of this class handle events of {@link AsynchronouslyRefreshedCache} instances and propagate them to other members of the same
 * Ignite grid.
 *
 * @author Axel Faust
 */
public class AsynchronouslyRefreshedCacheEventHandler extends TransactionListenerAdapter
        implements InitializingBean, RefreshableCacheListener, IgniteInstanceLifecycleAware
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AsynchronouslyRefreshedCacheEventHandler.class);

    // copied from org.alfresco.repo.transaction.TransactionSupportUtil (not accessible)
    private static final int COMMIT_ORDER_CACHE = 4;

    protected AsynchronouslyRefreshedCacheRegistry registry;

    protected String instanceName;

    protected String topicName;

    protected boolean instanceActive;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "registry", this.registry);
        PropertyCheck.mandatory(this, "instanceName", this.instanceName);
        PropertyCheck.mandatory(this, "topicName", this.topicName);

        this.registry.register(this);
    }

    /**
     * @param registry
     *            the registry to set
     */
    public void setRegistry(final AsynchronouslyRefreshedCacheRegistry registry)
    {
        this.registry = registry;
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
     * @param topicName
     *            the topicName to set
     */
    public void setTopicName(final String topicName)
    {
        this.topicName = topicName;
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
        if (EqualsHelper.nullSafeEquals(instanceName, this.instanceName))
        {
            this.instanceActive = true;

            Ignition.ignite(instanceName).message().localListen(this.topicName, (UUID, event) -> {
                if (event instanceof RefreshableCacheEvent)
                {
                    LOGGER.debug("Received refreshable cache event {}", event);
                    // only to the listeners interested in the specific cache
                    // (should be the cache itself, and definitely not us)
                    this.registry.broadcastEvent((RefreshableCacheEvent) event, false);
                }
                return true;
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceShutdown(final String instanceName)
    {
        if (EqualsHelper.nullSafeEquals(instanceName, this.instanceName))
        {
            this.instanceActive = false;
        }
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
    public void onRefreshableCacheEvent(final RefreshableCacheEvent refreshableCacheEvent)
    {
        if (TransactionSupportUtil.isActualTransactionActive())
        {
            final List<RefreshableCacheEvent> queuedEvents = TransactionalResourceHelper.getList(this.toString());
            if (queuedEvents.isEmpty())
            {
                TransactionSupportUtil.bindListener(this, COMMIT_ORDER_CACHE);
            }
            queuedEvents.add(refreshableCacheEvent);
        }
        else if (this.instanceActive)
        {
            LOGGER.debug("Sending non-transactional refreshable cache event {}", refreshableCacheEvent);
            final Ignite ignite = Ignition.ignite(this.instanceName);
            final ClusterGroup remotes = ignite.cluster().forRemotes().forServers();
            if (!remotes.nodes().isEmpty())
            {
                ignite.message(remotes).send(this.topicName, refreshableCacheEvent);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCommit()
    {
        if (this.instanceActive)
        {
            final List<RefreshableCacheEvent> queuedEvents = TransactionalResourceHelper.getList(this.toString());
            LOGGER.debug("Sending transactional refreshable cache events {}", queuedEvents);
            final Ignite ignite = Ignition.ignite(this.instanceName);
            final ClusterGroup remotes = ignite.cluster().forRemotes().forServers();
            if (!remotes.nodes().isEmpty())
            {
                queuedEvents.forEach(event -> ignite.message(remotes).send(this.topicName, event));
            }
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String getCacheId()
    {
        // no interest in any particular cache - want to catch events for "any" cache
        return null;
    }

}
