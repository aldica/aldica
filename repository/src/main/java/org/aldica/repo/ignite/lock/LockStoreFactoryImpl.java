/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.lock;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aldica.common.ignite.lifecycle.IgniteInstanceLifecycleAware;
import org.aldica.common.ignite.lifecycle.LazySwappingInvoker;
import org.aldica.common.ignite.lifecycle.SpringIgniteLifecycleBean;
import org.alfresco.error.AlfrescoRuntimeException;
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
import org.apache.ignite.cache.eviction.lru.LruEvictionPolicyFactory;
import org.apache.ignite.configuration.CacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * This factory implementation for {@link LockStore lock store} handles the creation of Ignite-backed instances, either immediately or
 * delayed (lazily) whenever the appropriate Ignite instance {@link IgniteInstanceLifecycleAware#afterInstanceStartup(String) has been
 * started}.
 *
 * @author Axel Faust
 */
public class LockStoreFactoryImpl implements LockStoreFactory, InitializingBean, IgniteInstanceLifecycleAware
{

    private static final Logger LOGGER = LoggerFactory.getLogger(LockStoreFactoryImpl.class);

    private static final Map<Method, MethodHandle> LOCK_STORE_HANDLES = new HashMap<>();
    static
    {
        final MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
        final Method[] methods = LockStore.class.getDeclaredMethods();
        try
        {
            for (final Method method : methods)
            {
                LOCK_STORE_HANDLES.put(method, publicLookup.unreflect(method));
            }
        }
        catch (final IllegalAccessException iae)
        {
            throw new AlfrescoRuntimeException("Cannot obtain method handle", iae);
        }
    }

    protected String instanceName;

    protected int partitionsCount = 32;

    protected boolean instanceStarted = false;

    protected boolean enableRemoteSupport;

    protected boolean disableAllStatistics;

    private final List<LazySwapLockStoreInvoker> invokers = new ArrayList<>();

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "instanceName", this.instanceName);
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
        if (EqualsHelper.nullSafeEquals(this.instanceName, instanceName))
        {
            this.instanceStarted = true;

            this.invokers.stream().forEach(LazySwapLockStoreInvoker::checkSwapInstance);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceShutdown(final String instanceName)
    {
        if (EqualsHelper.nullSafeEquals(this.instanceName, instanceName))
        {
            this.instanceStarted = false;
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
     * @param instanceName
     *     the name of the Ignite instance to which to attach the lock cache
     */
    public void setInstanceName(final String instanceName)
    {
        this.instanceName = instanceName;
    }

    /**
     * @param partitionsCount
     *     the partitionsCount to set
     */
    public void setPartitionsCount(final int partitionsCount)
    {
        this.partitionsCount = partitionsCount;
    }

    /**
     * @param enableRemoteSupport
     *     the enableRemoteSupport to set
     */
    public void setEnableRemoteSupport(final boolean enableRemoteSupport)
    {
        this.enableRemoteSupport = enableRemoteSupport;
    }

    /**
     * @param disableAllStatistics
     *     the disableAllStatistics to set
     */
    public void setDisableAllStatistics(final boolean disableAllStatistics)
    {
        this.disableAllStatistics = disableAllStatistics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockStore createLockStore()
    {
        LockStore lockStore;
        if (this.instanceStarted && this.enableRemoteSupport)
        {
            LOGGER.debug("Creating Ignite-backed lock store");
            lockStore = this.createIgniteLockStore();
        }
        else if (this.enableRemoteSupport)
        {
            LOGGER.debug("Creating proxy to lazily swap lock store with Ignite-backed instance when grid has started");
            final LockStoreImpl temporaryLockStore = new LockStoreImpl();
            final LazySwapLockStoreInvoker invoker = new LazySwapLockStoreInvoker(temporaryLockStore);
            lockStore = (LockStore) Proxy.newProxyInstance(LockStoreFactoryImpl.class.getClassLoader(), new Class<?>[] { LockStore.class },
                    invoker);
            this.invokers.add(invoker);
        }
        else
        {
            LOGGER.debug("Creating default lock store");
            lockStore = new LockStoreImpl();
        }

        return lockStore;
    }

    protected LockStore createIgniteLockStore()
    {
        final CacheConfiguration<NodeRef, LockState> cacheConfig = new CacheConfiguration<>();
        cacheConfig.setName("lockStore");
        cacheConfig.setCacheMode(CacheMode.REPLICATED);
        cacheConfig.setStatisticsEnabled(!this.disableAllStatistics);

        // evict to off-heap after 975+25 entries
        final LruEvictionPolicyFactory<Object, Object> evictionPolicyFactory = new LruEvictionPolicyFactory<>(975);
        evictionPolicyFactory.setBatchSize(25);
        cacheConfig.setOnheapCacheEnabled(true);
        cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

        cacheConfig.setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC);
        cacheConfig.setRebalanceMode(CacheRebalanceMode.ASYNC);

        @SuppressWarnings("resource")
        final Ignite instance = this.instanceName != null ? Ignition.ignite(this.instanceName) : Ignition.ignite();
        final IgniteCache<NodeRef, LockState> backingCache = instance.getOrCreateCache(cacheConfig);

        final LockStore lockStore = new IgniteBackedLockStore(backingCache);
        return lockStore;
    }

    /**
     * This old-school AOP invocation handler is necessary to handle grid starts after initial lock store has been created since the
     * {@link SpringIgniteLifecycleBean grid startup}, specifically discovery may rely on Alfresco services which in turn require caches and
     * other initialisation, possibly the lock store. This would cause a circular dependency graph without the ability to lazily swap
     * temporary lock stores with the eventual final instances.
     *
     * @author Axel Faust
     */
    public class LazySwapLockStoreInvoker extends LazySwappingInvoker<LockStore>
    {

        private boolean swapped = false;

        protected LazySwapLockStoreInvoker(final LockStore lockStore)
        {
            super(lockStore);
        }

        protected void checkSwapInstance()
        {

            if (!this.swapped)
            {
                final LockStore newLockStore = LockStoreFactoryImpl.this.createIgniteLockStore();

                // transfer
                this.backingObject.getNodes().forEach(node -> {
                    final LockState lockState = this.backingObject.get(node);
                    newLockStore.set(node, lockState);
                });

                this.backingObject = newLockStore;
                this.swapped = true;

                this.resetBoundMethodHandles();

                LOGGER.debug("Lazily swapped temporary lock store with Ignite-backed instance");
            }
        }

        @Override
        protected MethodHandle bindIfSupported(final Method method)
        {
            final MethodHandle mh = LOCK_STORE_HANDLES.get(method);
            return mh != null ? mh.bindTo(this.backingObject) : super.bindIfSupported(method);
        }
    }
}
