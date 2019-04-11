/*
 * Copyright 2016 - 2019 Acosix GmbH
 */
package de.acosix.alfresco.ignite.repo.lock;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.alfresco.repo.lock.mem.LockState;
import org.alfresco.repo.lock.mem.LockStore;
import org.alfresco.repo.lock.mem.LockStoreFactory;
import org.alfresco.repo.lock.mem.LockStoreImpl;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.PropertyCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheRebalanceMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cache.eviction.lru.LruEvictionPolicyFactory;
import org.apache.ignite.configuration.CacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.acosix.alfresco.ignite.common.lifecycle.IgniteInstanceLifecycleAware;
import de.acosix.alfresco.ignite.common.lifecycle.SpringIgniteLifecycleBean;
import de.acosix.alfresco.ignite.repo.discovery.MemberTcpDiscoveryIpFinder;

/**
 * @author Axel Faust
 */
public class LockStoreFactoryImpl implements LockStoreFactory, InitializingBean, IgniteInstanceLifecycleAware
{

    private static final Logger LOGGER = LoggerFactory.getLogger(LockStoreFactoryImpl.class);

    protected String instanceName;

    protected int partitionsCount = 32;

    protected boolean gridStarted = false;

    protected boolean enableRemoteSupport;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "gridName", this.instanceName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceStartup(final String gridName)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceStartup(final String gridName)
    {
        if (EqualsHelper.nullSafeEquals(this.instanceName, gridName))
        {
            this.gridStarted = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceShutdown(final String gridName)
    {
        if (EqualsHelper.nullSafeEquals(this.instanceName, gridName))
        {
            this.gridStarted = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceShutdown(final String gridName)
    {
        // NO-OP
    }

    /**
     * @param instanceName
     *            the name of the Ignite instance to which to attach the lock cache
     */
    public void setInstanceName(final String instanceName)
    {
        this.instanceName = instanceName;
    }

    /**
     * @param partitionsCount
     *            the partitionsCount to set
     */
    public void setPartitionsCount(final int partitionsCount)
    {
        this.partitionsCount = partitionsCount;
    }

    /**
     * @param enableRemoteSupport
     *            the enableRemoteSupport to set
     */
    public void setEnableRemoteSupport(final boolean enableRemoteSupport)
    {
        this.enableRemoteSupport = enableRemoteSupport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockStore createLockStore()
    {
        LockStore lockStore;
        if (this.gridStarted)
        {
            lockStore = this.createIgniteLockStore();
        }
        else
        {
            LOGGER.debug("Creating proxy to lazily swap lcok store with real instance when grid has started");
            final LockStoreImpl temporaryLockStore = new LockStoreImpl();
            lockStore = (LockStore) Proxy.newProxyInstance(LockStoreFactoryImpl.class.getClassLoader(),
                    new Class<?>[] { LockStore.class, IgniteInstanceLifecycleAware.class },
                    new LockStoreInvokerWithLazySwapSupport(temporaryLockStore));
        }

        return lockStore;
    }

    protected LockStore createIgniteLockStore()
    {
        final CacheConfiguration<NodeRef, LockState> cacheConfig = new CacheConfiguration<>();
        cacheConfig.setName("lockStore");
        cacheConfig.setCacheMode(this.enableRemoteSupport ? CacheMode.PARTITIONED : CacheMode.LOCAL);
        cacheConfig.setStatisticsEnabled(true);

        // evict to off-heap after 975+25 entries
        final LruEvictionPolicyFactory<Object, Object> evictionPolicyFactory = new LruEvictionPolicyFactory<>(975);
        evictionPolicyFactory.setBatchSize(25);
        cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

        if (cacheConfig.getCacheMode() == CacheMode.PARTITIONED)
        {
            cacheConfig.setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC);
            cacheConfig.setRebalanceMode(CacheRebalanceMode.ASYNC);
            cacheConfig.setBackups(1);
            cacheConfig.setReadFromBackup(true);

            final RendezvousAffinityFunction affinityFunction = new RendezvousAffinityFunction(false, this.partitionsCount);
            cacheConfig.setAffinity(affinityFunction);
        }

        @SuppressWarnings("resource")
        final Ignite instance = this.instanceName != null ? Ignition.ignite(this.instanceName) : Ignition.ignite();
        final IgniteCache<NodeRef, LockState> backingCache = instance.getOrCreateCache(cacheConfig);

        final LockStore lockStore = new IgniteBackedLockStore(backingCache);
        return lockStore;
    }

    /**
     * This old-school AOP invocation handler is necessary to handle grid starts after initial lock store has been created since the
     * {@link SpringIgniteLifecycleBean grid startup}, specifically {@link MemberTcpDiscoveryIpFinder discovery} may rely on Alfresco
     * services
     * which in turn require caches and other initialisation, possibly the lock store. This would cause a circular dependency graph without
     * the ability to lazily swap temporary lock stores with the eventual final instances.
     *
     * @author Axel Faust
     */
    public class LockStoreInvokerWithLazySwapSupport implements InvocationHandler
    {

        private boolean swapped = false;

        private LockStore lockStore;

        protected LockStoreInvokerWithLazySwapSupport(final LockStore lockStore)
        {
            this.lockStore = lockStore;
        }

        public LockStore getBackingObject()
        {
            return this.lockStore;
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable
        {
            Object result = null;
            if (!this.swapped && "afterGridStartup".equals(method.getName()) && args.length == 1
                    && EqualsHelper.nullSafeEquals(LockStoreFactoryImpl.this.instanceName, args[0]))
            {
                LockStoreFactoryImpl.this.gridStarted = true;

                final LockStore newLockStore = LockStoreFactoryImpl.this.createLockStore();

                // transfer
                this.lockStore.getNodes().forEach(node -> {
                    final LockState lockState = this.lockStore.get(node);
                    newLockStore.set(node, lockState);
                });

                this.lockStore = newLockStore;
                this.swapped = true;

                LOGGER.debug("Lazily swapped temporary lock store with real instance");
            }

            if (method.getDeclaringClass().isInstance(this.lockStore))
            {
                try
                {
                    result = method.invoke(this.lockStore, args);
                }
                catch (final InvocationTargetException e)
                {
                    throw e.getTargetException();
                }
            }

            return result;
        }
    }
}
