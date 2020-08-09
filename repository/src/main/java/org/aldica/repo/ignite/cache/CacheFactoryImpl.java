/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.cache.configuration.Factory;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.expiry.ModifiedExpiryPolicy;

import org.aldica.common.ignite.cache.CombinedExpiryPolicy;
import org.aldica.common.ignite.lifecycle.IgniteInstanceLifecycleAware;
import org.aldica.common.ignite.lifecycle.SpringIgniteLifecycleBean;
import org.alfresco.repo.cache.AbstractCacheFactory;
import org.alfresco.repo.cache.DefaultSimpleCache;
import org.alfresco.repo.cache.NullCache;
import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.PropertyCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheRebalanceMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cache.eviction.AbstractEvictionPolicyFactory;
import org.apache.ignite.cache.eviction.EvictionPolicy;
import org.apache.ignite.cache.eviction.fifo.FifoEvictionPolicyFactory;
import org.apache.ignite.cache.eviction.lru.LruEvictionPolicyFactory;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.NearCacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * @author Axel Faust
 */
public class CacheFactoryImpl<K extends Serializable, V extends Serializable> extends AbstractCacheFactory<K, V>
        implements InitializingBean, ApplicationContextAware, IgniteInstanceLifecycleAware
{

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheFactoryImpl.class);

    private static final String CACHE_TYPE_NOT_SET = "<not set>";

    private static final String CACHE_TYPE_NULL = "nullCache";

    private static final String CACHE_TYPE_LOCAL = "local";

    private static final String CACHE_TYPE_LOCAL_DEFAULT_SIMPLE = "localDefaultSimple";

    private static final String CACHE_TYPE_INVALIDATING = "invalidating";

    private static final String CACHE_TYPE_INVALIDATING_DEFAULT_SIMPLE = "invalidatingDefaultSimple";

    private static final String CACHE_TYPE_PARTITIONED = "partitioned";

    private static final String CACHE_TYPE_REPLICATED = "replicated";

    /**
     * Alias for {@link #CACHE_TYPE_PARTITIONED} - the Alfresco terminology of fully-distributed refers to the Ignite terminology of a
     * partitioned cache
     */
    private static final String CACHE_TYPE_ALFRESCO_FULLY_DISTRIBUTED = "fully-distributed";

    private static final String EVICTION_POLICY_LRU = "LRU";

    private static final String EVICTION_POLICY_FIFO = "FIFO";

    private static final String EVICTION_POLICY_NONE = "NONE";

    protected ApplicationContext applicationContext;

    protected Properties properties;

    protected String placeholderPrefix = PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_PREFIX;

    protected String placeholderSuffix = PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_SUFFIX;

    protected String valueSeparator = PlaceholderConfigurerSupport.DEFAULT_VALUE_SEPARATOR;

    protected PropertyPlaceholderHelper placeholderHelper;

    protected String instanceName;

    protected int partitionsCount = 32;

    protected boolean instanceStarted = false;

    protected boolean enableRemoteSupport;

    protected boolean ignoreDefaultEvictionConfiguration;

    protected boolean disableAllStatistics;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "instanceName", this.instanceName);

        this.placeholderHelper = new PropertyPlaceholderHelper(this.placeholderPrefix, this.placeholderSuffix, this.valueSeparator, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext)
    {
        this.applicationContext = applicationContext;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void setProperties(final Properties properties)
    {
        this.properties = properties;
        super.setProperties(properties);
    }

    /**
     * @param placeholderPrefix
     *            the placeholderPrefix to set
     */
    public void setPlaceholderPrefix(final String placeholderPrefix)
    {
        this.placeholderPrefix = placeholderPrefix;
    }

    /**
     * @param placeholderSuffix
     *            the placeholderSuffix to set
     */
    public void setPlaceholderSuffix(final String placeholderSuffix)
    {
        this.placeholderSuffix = placeholderSuffix;
    }

    /**
     * @param valueSeparator
     *            the valueSeparator to set
     */
    public void setValueSeparator(final String valueSeparator)
    {
        this.valueSeparator = valueSeparator;
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
     *            the name of the Ignite instance to which to attach caches
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
     * @param ignoreDefaultEvictionConfiguration
     *            the ignoreDefaultEvictionConfiguration to set
     */
    public void setIgnoreDefaultEvictionConfiguration(final boolean ignoreDefaultEvictionConfiguration)
    {
        this.ignoreDefaultEvictionConfiguration = ignoreDefaultEvictionConfiguration;
    }

    /**
     * @param disableAllStatistics
     *            the disableAllStatistics to set
     */
    public void setDisableAllStatistics(final boolean disableAllStatistics)
    {
        this.disableAllStatistics = disableAllStatistics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SimpleCache<K, V> createCache(final String cacheName)
    {
        return this.createCache(cacheName, true);
    }

    @SuppressWarnings("resource")
    protected SimpleCache<K, V> createCache(final String cacheName, final boolean withProxy)
    {
        String cacheType = this.getProperty(cacheName, "ignite.cache.type", "cache.type", "cluster.type", CACHE_TYPE_NOT_SET);
        if (CACHE_TYPE_NOT_SET.equals(cacheType))
        {
            throw new IllegalStateException("Cache " + cacheName + " has not been configured for a specifc cache type");
        }

        boolean disabled = false;
        boolean requiresRemoteSupport = true;
        boolean requiresIgnite = true;
        switch (cacheType)
        {
            case CACHE_TYPE_NULL:
                disabled = true;
                break;
            case CACHE_TYPE_LOCAL:
                requiresRemoteSupport = false;
                break;
            case CACHE_TYPE_LOCAL_DEFAULT_SIMPLE:
                requiresRemoteSupport = false;
                requiresIgnite = false;
                break;
            case CACHE_TYPE_INVALIDATING:
                if (!this.enableRemoteSupport)
                {
                    requiresRemoteSupport = false;
                    cacheType = CACHE_TYPE_LOCAL;
                }
                break;
            case CACHE_TYPE_INVALIDATING_DEFAULT_SIMPLE:
                if (!this.enableRemoteSupport)
                {
                    requiresRemoteSupport = false;
                    requiresIgnite = false;
                    cacheType = CACHE_TYPE_LOCAL_DEFAULT_SIMPLE;
                }
                break;
            case CACHE_TYPE_ALFRESCO_FULLY_DISTRIBUTED:
            case CACHE_TYPE_PARTITIONED:
                if (!this.enableRemoteSupport)
                {
                    requiresRemoteSupport = false;
                    cacheType = CACHE_TYPE_LOCAL;
                }
                break;
            case CACHE_TYPE_REPLICATED:
                if (!this.enableRemoteSupport)
                {
                    requiresRemoteSupport = false;
                    cacheType = CACHE_TYPE_LOCAL;
                }
                break;
            default:
                // NO-OP
        }

        if (!disabled && requiresRemoteSupport && !this.enableRemoteSupport)
        {
            throw new UnsupportedOperationException("Cache type " + cacheType + " is not supported as remote support is not enabled");
        }

        SimpleCache<K, V> cache;

        if (disabled)
        {
            cache = new NullCache<>();
        }
        else if (this.instanceStarted)
        {
            final Ignite grid = this.instanceName != null ? Ignition.ignite(this.instanceName) : Ignition.ignite();
            // old behaviour was to always invalidate on put - keep as long as no override has been configured
            final boolean alwaysInvalidateOnPut = Boolean
                    .parseBoolean(this.getProperty(cacheName, "ignite.forceInvalidateOnPut", "forceInvalidateOnPut", "true"));
            final boolean allowValueSentinels = Boolean
                    .parseBoolean(this.getProperty(cacheName, "ignite.allowValueSentinels", "allowValueSentinels", "true"));

            switch (cacheType)
            {
                case CACHE_TYPE_LOCAL:
                    cache = this.createLocalCache(grid, cacheName, false, false);
                    break;
                case CACHE_TYPE_LOCAL_DEFAULT_SIMPLE:
                    cache = this.createLocalDefaultSimpleCache(cacheName);
                    break;
                case CACHE_TYPE_INVALIDATING:
                    cache = this.createLocalCache(grid, cacheName, true, alwaysInvalidateOnPut);
                    break;
                case CACHE_TYPE_INVALIDATING_DEFAULT_SIMPLE:
                    cache = this.createLocalDefaultSimpleCache(cacheName);
                    cache = new InvalidatingCacheFacade<>(cacheName, cache, grid, alwaysInvalidateOnPut, allowValueSentinels);
                    break;
                case CACHE_TYPE_ALFRESCO_FULLY_DISTRIBUTED:
                case CACHE_TYPE_PARTITIONED:
                    cache = this.createPartitionedCache(grid, cacheName);
                    break;
                case CACHE_TYPE_REPLICATED:
                    cache = this.createReplicatedCache(grid, cacheName);
                    break;
                default:
                    throw new UnsupportedOperationException("Cache type " + cacheType + " is not supported");
            }
        }
        else
        {
            cache = this.createLocalDefaultSimpleCache(cacheName);
        }

        if (!disabled && withProxy)
        {
            if (!this.instanceStarted && requiresIgnite)
            {
                cache = this.createLazySwapProxy(cacheName, cache);
            }
        }

        return cache;
    }

    protected String getProperty(final String cacheName, final String igniteProperty, final String alfrescoOrSimplifiedProperty,
            final String defaultValue)
    {
        // we look up our own configuration but use Alfresco / a simplified property as fallback / default value
        final String alfrescoOrSimplifiedValue = this.getProperty(cacheName, alfrescoOrSimplifiedProperty, defaultValue);
        String igniteValue = this.getProperty(cacheName, igniteProperty, alfrescoOrSimplifiedValue);

        // base implementation does not support placeholders
        igniteValue = igniteValue != null ? this.placeholderHelper.replacePlaceholders(igniteValue, this.properties) : null;

        return igniteValue;
    }

    protected String getProperty(final String cacheName, final String igniteProperty, final String simplifiedProperty,
            final String alfrescoProperty, final String defaultValue)
    {
        // we look up our own configuration but use Alfresco as fallback / default value
        // we also support a simplified property separate from the Alfresco one
        // (to allow for consistent naming in our simplified properties while Alfresco ones can still be used for fallback)
        final String alfrescoValue = this.getProperty(cacheName, alfrescoProperty, defaultValue);
        final String simplifiedValue = this.getProperty(cacheName, simplifiedProperty, alfrescoValue);
        String igniteValue = this.getProperty(cacheName, igniteProperty, simplifiedValue);

        // base implementation does not support placeholders
        igniteValue = igniteValue != null ? this.placeholderHelper.replacePlaceholders(igniteValue, this.properties) : null;

        return igniteValue;
    }

    protected SimpleCache<K, V> createLazySwapProxy(final String cacheName, final SimpleCache<K, V> temporaryCache)
    {
        LOGGER.debug("Creating enhanced cache proxy to lazily swap temporary cache {} with real instance when grid has started", cacheName);

        @SuppressWarnings("unchecked")
        final SimpleCache<K, V> proxyInstance = (SimpleCache<K, V>) Proxy.newProxyInstance(CacheFactoryImpl.class.getClassLoader(),
                new Class<?>[] { SimpleCache.class, IgniteInstanceLifecycleAware.class, CacheWithMetrics.class },

                new SimpleLazySwapCacheInvoker(temporaryCache, cacheName));
        return proxyInstance;
    }

    protected SimpleCache<K, V> createLocalDefaultSimpleCache(final String cacheName)
    {
        LOGGER.debug("Creating local default simple cache {}", cacheName);

        final int maxItems = Integer.parseInt(this.getProperty(cacheName, "ignite.heap.maxItems", "heap.maxItems", "maxItems", "0"));
        final boolean useMaxItems = !EVICTION_POLICY_NONE.equals(this.getProperty(cacheName, "ignite.heap.eviction-policy",
                "heap.eviction-policy", "eviction-policy", EVICTION_POLICY_NONE));
        final int ttlSeconds = Integer.parseInt(this.getProperty(cacheName, "ignite.timeToLiveSeconds", "timeToLiveSeconds", "0"));
        final int maxIdleSeconds = Integer.parseInt(this.getProperty(cacheName, "ignite.maxIdleSeconds", "maxIdleSeconds", "0"));

        final DefaultSimpleCache<K, V> cache = new DefaultSimpleCache<>(maxItems, useMaxItems, ttlSeconds, maxIdleSeconds, cacheName);
        return cache;
    }

    protected SimpleCache<K, V> createLocalCache(final Ignite grid, final String cacheName, final boolean invalidate,
            final boolean alwaysInvalidateOnPut)
    {
        LOGGER.debug("Creating local cache {} in grid {}", cacheName, grid.name());

        final CacheConfiguration<K, V> cacheConfig = new CacheConfiguration<>();

        cacheConfig.setName(cacheName.startsWith("cache.") ? cacheName.substring(6) : cacheName);
        cacheConfig.setCacheMode(CacheMode.LOCAL);
        cacheConfig.setStatisticsEnabled(!this.disableAllStatistics);
        cacheConfig.setStoreKeepBinary(true);

        this.processMemoryConfig(cacheName, cacheConfig);
        this.processExpiryPolicy(cacheName, cacheConfig);

        final boolean allowValueSentinels = Boolean
                .parseBoolean(this.getProperty(cacheName, "ignite.allowValueSentinels", "allowValueSentinels", "true"));

        final IgniteCache<K, V> backingCache = grid.getOrCreateCache(cacheConfig);
        final SimpleIgniteBackedCache<K, V> localCache = new SimpleIgniteBackedCache<>(grid,
                SimpleIgniteBackedCache.Mode.getLocalCacheMode(invalidate, alwaysInvalidateOnPut), backingCache, allowValueSentinels);
        return localCache;
    }

    protected SimpleCache<K, V> createPartitionedCache(final Ignite grid, final String cacheName)
    {
        LOGGER.debug("Creating partitioned cache {} in grid {}", cacheName, grid.name());

        final CacheConfiguration<K, V> cacheConfig = new CacheConfiguration<>();

        cacheConfig.setName(cacheName.startsWith("cache.") ? cacheName.substring(6) : cacheName);
        cacheConfig.setCacheMode(CacheMode.PARTITIONED);
        cacheConfig.setStatisticsEnabled(!this.disableAllStatistics);
        cacheConfig.setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC);
        cacheConfig.setRebalanceMode(CacheRebalanceMode.ASYNC);
        cacheConfig.setStoreKeepBinary(true);

        final int backupCount = Integer.parseInt(this.getProperty(cacheName, "ignite.backup-count", "backup-count", "1"));
        cacheConfig.setBackups(backupCount);
        // reading from backup is more efficient than doing network calls - Alfresco defaults most caches to false though
        final boolean readBackupData = Boolean.parseBoolean(this.getProperty(cacheName, "ignite.readBackupData", "readBackupData", "true"));
        cacheConfig.setReadFromBackup(readBackupData);

        final RendezvousAffinityFunction affinityFunction = new RendezvousAffinityFunction(false, this.partitionsCount);
        cacheConfig.setAffinity(affinityFunction);

        this.processMemoryConfig(cacheName, cacheConfig);
        this.processExpiryPolicy(cacheName, cacheConfig);
        this.processNearCache(cacheName, cacheConfig);

        final boolean allowValueSentinels = Boolean
                .parseBoolean(this.getProperty(cacheName, "ignite.allowValueSentinels", "allowValueSentinels", "true"));

        final IgniteCache<K, V> backingCache = grid.getOrCreateCache(cacheConfig);
        final SimpleIgniteBackedCache<K, V> localCache = new SimpleIgniteBackedCache<>(grid, SimpleIgniteBackedCache.Mode.PARTITIONED,
                backingCache, allowValueSentinels);
        return localCache;
    }

    protected SimpleCache<K, V> createReplicatedCache(final Ignite grid, final String cacheName)
    {
        LOGGER.debug("Creating replicated cache {} in grid {}", cacheName, grid.name());

        final CacheConfiguration<K, V> cacheConfig = new CacheConfiguration<>();

        cacheConfig.setName(cacheName.startsWith("cache.") ? cacheName.substring(6) : cacheName);
        cacheConfig.setCacheMode(CacheMode.REPLICATED);
        cacheConfig.setStatisticsEnabled(!this.disableAllStatistics);
        cacheConfig.setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC);
        cacheConfig.setRebalanceMode(CacheRebalanceMode.ASYNC);
        cacheConfig.setStoreKeepBinary(true);
        cacheConfig.setReadFromBackup(true);

        final RendezvousAffinityFunction affinityFunction = new RendezvousAffinityFunction(false, this.partitionsCount);
        cacheConfig.setAffinity(affinityFunction);

        this.processMemoryConfig(cacheName, cacheConfig);
        this.processExpiryPolicy(cacheName, cacheConfig);

        final boolean allowValueSentinels = Boolean
                .parseBoolean(this.getProperty(cacheName, "ignite.allowValueSentinels", "allowValueSentinels", "true"));

        final IgniteCache<K, V> backingCache = grid.getOrCreateCache(cacheConfig);
        final SimpleIgniteBackedCache<K, V> localCache = new SimpleIgniteBackedCache<>(grid, SimpleIgniteBackedCache.Mode.REPLICATED,
                backingCache, allowValueSentinels);
        return localCache;
    }

    protected void processMemoryConfig(final String cacheName, final CacheConfiguration<K, V> cacheConfig)
    {
        final String dataRegionName = this.getProperty(cacheName, "ignite.dataRegionName", "dataRegionName", null);
        if (dataRegionName != null)
        {
            final String effectiveDataRegionName = this.instanceName + ".region." + dataRegionName;
            cacheConfig.setDataRegionName(effectiveDataRegionName);
        }

        this.processEvictionPolicy(cacheName, cacheConfig);
    }

    protected void processExpiryPolicy(final String cacheName, final CacheConfiguration<K, V> cacheConfig)
    {
        final int timeToLiveSeconds = Integer.parseInt(this.getProperty(cacheName, "ignite.timeToLiveSeconds", "timeToLiveSeconds", "0"));
        final int maxIdleSeconds = Integer.parseInt(this.getProperty(cacheName, "ignite.maxIdleSeconds", "maxIdleSeconds", "0"));

        final Collection<Factory<? extends ExpiryPolicy>> policyFactories = new ArrayList<>();
        if (timeToLiveSeconds > 0)
        {
            policyFactories.add(ModifiedExpiryPolicy.factoryOf(new Duration(TimeUnit.SECONDS, timeToLiveSeconds)));
        }

        if (maxIdleSeconds > 0)
        {
            policyFactories.add(AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.SECONDS, timeToLiveSeconds)));
        }

        if (policyFactories.size() > 1)
        {
            cacheConfig.setExpiryPolicyFactory(CombinedExpiryPolicy.factoryOf(policyFactories));
        }
        else if (!policyFactories.isEmpty())
        {
            cacheConfig.setExpiryPolicyFactory(policyFactories.iterator().next());
        }
    }

    protected void processEvictionPolicy(final String cacheName, final CacheConfiguration<K, V> cacheConfig)
    {
        final long maxMemory = Long.parseLong(this.getProperty(cacheName, "ignite.heap.maxMemory", "heap.maxMemory", "0"));
        final int maxItems = Integer.parseInt(
                this.ignoreDefaultEvictionConfiguration ? this.getProperty(cacheName, "ignite.heap.maxItems", "heap.maxItems", "0")
                        : this.getProperty(cacheName, "ignite.heap.maxItems", "heap.maxItems", "maxItems", "0"));

        final String evictionPolicy = this.ignoreDefaultEvictionConfiguration
                ? this.getProperty(cacheName, "ignite.heap.eviction-policy", "heap.eviction-policy", EVICTION_POLICY_NONE)
                : this.getProperty(cacheName, "ignite.heap.eviction-policy", "heap.eviction-policy", "eviction-policy",
                        EVICTION_POLICY_NONE);

        if (!EVICTION_POLICY_NONE.equals(evictionPolicy) && (maxMemory > 0 || maxItems > 0))
        {
            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setCopyOnRead(false);

            final int batchEvictionItems = Integer
                    .parseInt(this.getProperty(cacheName, "ignite.heap.batchEvictionItems", "heap.batchEvictionItems", "0"));
            // eviction-percentage as an option is an artifact of pre 6.0 Alfresco default configuration
            // it was removed as part of https://issues.alfresco.com/jira/browse/REPO-3050
            final int evictionPercentage = Integer.parseInt(this.ignoreDefaultEvictionConfiguration
                    ? this.getProperty(cacheName, "ignite.heap.eviction-percentage", "heap.eviction-percentage", "0")
                    : this.getProperty(cacheName, "ignite.heap.eviction-percentage", "heap.eviction-percentage", "eviction-percentage",
                            "0"));

            final AbstractEvictionPolicyFactory<? extends EvictionPolicy<K, V>> evictPolicyFactory = this.createEvictionPolicy(maxMemory,
                    maxItems, evictionPolicy, batchEvictionItems, evictionPercentage);
            cacheConfig.setEvictionPolicyFactory(evictPolicyFactory);
        }
    }

    protected AbstractEvictionPolicyFactory<? extends EvictionPolicy<K, V>> createEvictionPolicy(final long maxMemory, int maxItems,
            final String evictionPolicy, int batchEvictionItems, final int evictionPercentage)
    {
        AbstractEvictionPolicyFactory<? extends EvictionPolicy<K, V>> evictPolicyFactory;
        switch (evictionPolicy)
        {
            case EVICTION_POLICY_FIFO:
                evictPolicyFactory = new FifoEvictionPolicyFactory<>();
                break;
            case EVICTION_POLICY_LRU:
                evictPolicyFactory = new LruEvictionPolicyFactory<>();
                break;
            default:
                throw new IllegalStateException("Unsupported eviction policy: " + evictionPolicy);
        }

        if (maxMemory > 0)
        {
            evictPolicyFactory.setMaxMemorySize(maxMemory);
        }

        if (maxItems > 0)
        {
            if (batchEvictionItems <= 0)
            {
                if (evictionPercentage > 0)
                {
                    batchEvictionItems = (int) Math.round(maxItems * evictionPercentage * 0.01d);
                }
                batchEvictionItems = Math.max(batchEvictionItems, 1);
            }
            // Ignite allows cache to grow up to maxItems + (batchEvictionItems - 1) before running eviction
            maxItems = maxItems - (batchEvictionItems - 1);

            evictPolicyFactory.setMaxSize(maxItems);
            evictPolicyFactory.setBatchSize(batchEvictionItems);
        }
        return evictPolicyFactory;
    }

    protected void processNearCache(final String cacheName, final CacheConfiguration<K, V> cacheConfig)
    {
        final String nearEvictionPolicy = this.getProperty(cacheName, "ignite.near.eviction-policy", "near.eviction-policy",
                EVICTION_POLICY_NONE);

        final long cacheMaxMemory = Long.parseLong(this.getProperty(cacheName, "ignite.heap.maxMemory", "heap.maxMemory", "0"));
        final long nearMaxMemory = Long.parseLong(this.getProperty(cacheName, "ignite.near.maxMemory", "near.maxMemory",
                cacheMaxMemory > 0 ? String.valueOf(cacheMaxMemory / 4) : "0"));

        final int cacheMaxItems = Integer.parseInt(
                this.ignoreDefaultEvictionConfiguration ? this.getProperty(cacheName, "ignite.heap.maxItems", "heap.maxItems", "0")
                        : this.getProperty(cacheName, "ignite.heap.maxItems", "heap.maxItems", "maxItems", "0"));
        final int nearMaxItems = Integer.parseInt(this.getProperty(cacheName, "ignite.near.maxItems", "near.maxItems",
                cacheMaxItems > 0 ? String.valueOf(cacheMaxItems / 4) : "0"));

        if (!EVICTION_POLICY_NONE.equals(nearEvictionPolicy) && (nearMaxItems > 0 || nearMaxMemory > 0))
        {
            final NearCacheConfiguration<K, V> nearCacheCfg = new NearCacheConfiguration<>();
            cacheConfig.setNearConfiguration(nearCacheCfg);

            final int cacheBatchEvictionItems = Integer
                    .parseInt(this.getProperty(cacheName, "ignite.heap.batchEvictionItems", "heap.batchEvictionItems", "0"));
            final int nearBatchEvictionItems = Integer.parseInt(this.getProperty(cacheName, "ignite.near.batchEvictionItems",
                    "near.batchEvictionItems", cacheBatchEvictionItems > 0 ? String.valueOf(cacheBatchEvictionItems) : "0"));
            final int cacheBatchEvictionPercentage = Integer.parseInt(this.ignoreDefaultEvictionConfiguration
                    ? this.getProperty(cacheName, "ignite.heap.eviction-percentage", "heap.eviction-percentage", "0")
                    : this.getProperty(cacheName, "ignite.heap.eviction-percentage", "heap.eviction-percentage", "eviction-percentage",
                            "0"));
            final int nearBatchEvictionPercentage = Integer.parseInt(this.getProperty(cacheName, "ignite.near.eviction-percentage",
                    "near.eviction-percentage", cacheBatchEvictionPercentage > 0 ? String.valueOf(cacheBatchEvictionPercentage) : "0"));

            final AbstractEvictionPolicyFactory<? extends EvictionPolicy<K, V>> evictionPolicyFactory = this.createEvictionPolicy(
                    nearMaxMemory, nearMaxItems, nearEvictionPolicy, nearBatchEvictionItems, nearBatchEvictionPercentage);
            nearCacheCfg.setNearEvictionPolicyFactory(evictionPolicyFactory);
        }
    }

    /**
     * Instances of this AOP invocation handler handle grid starts after initial cache(s) have been created since the
     * {@link SpringIgniteLifecycleBean grid startup}, specifically discovery may rely on Alfresco services which in turn require caches.
     * This would cause a circular dependency graph without the ability to lazily swap temporary caches with the eventual final instances.
     *
     * @author Axel Faust
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public class SimpleLazySwapCacheInvoker extends SimpleCacheInvoker
    {

        private final String cacheName;

        private boolean swapped = false;

        protected SimpleLazySwapCacheInvoker(final SimpleCache backingCache, final String cacheName)
        {
            super(backingCache);
            this.cacheName = cacheName;
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable
        {
            Object result = null;
            final String methodName = method.getName();
            if (IgniteInstanceLifecycleAware.class.isAssignableFrom(method.getDeclaringClass())
                    && (methodName.startsWith("beforeInstance") || methodName.startsWith("afterInstance")))
            {
                if ("afterInstanceStartup".equals(methodName) && args.length == 1
                        && EqualsHelper.nullSafeEquals(CacheFactoryImpl.this.instanceName, args[0]))
                {
                    // cannot rely on lifecycle call ordering based on some inherent Spring order, so forward to factory itself
                    if (!CacheFactoryImpl.this.instanceStarted)
                    {
                        CacheFactoryImpl.this.afterInstanceStartup(CacheFactoryImpl.this.instanceName);
                    }

                    if (!this.swapped)
                    {
                        final SimpleCache newCache = CacheFactoryImpl.this.createCache(this.cacheName, false);

                        // transfer
                        this.backingCache.getKeys().forEach(key -> {
                            newCache.put((Serializable) key, this.backingCache.get((Serializable) key));
                        });

                        this.backingCache = newCache;
                        this.swapped = true;

                        LOGGER.debug("Lazily swapped temporary cache {} with real instance", this.cacheName);
                    }
                }
                result = null;
            }
            else
            {
                result = super.invoke(proxy, method, args);
            }
            return result;
        }
    }
}