/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;

import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.cache.TransactionalCache.ValueHolder;
import org.alfresco.repo.cache.lookup.EntityLookupCache;
import org.alfresco.util.ParameterCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cluster.ClusterGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instances of this class provide an alternative to the Alfresco default {@link SimpleCache SimpleCache} implementations with backing by
 * potentially data grid-capable cache instances of the Apache Ignite library.
 *
 * @author Axel Faust
 */
public class SimpleIgniteBackedCache<K extends Serializable, V> implements SimpleCache<K, V>, CacheWithMetrics
{

    // value copied from EntityLookupCache (not accessible there)
    private static final Serializable VALUE_NULL = "@@VALUE_NULL@@";

    // value copied from EntityLookupCache (not accessible there)
    private static final Serializable VALUE_NOT_FOUND = "@@VALUE_NOT_FOUND@@";

    private final Logger instanceLogger;

    protected final String gridName;

    protected final boolean fullCache;

    protected final String cacheName;

    protected final IgniteCache<K, V> backingCache;

    protected final boolean allowSentinelsInBackingCache;

    protected volatile boolean informedUnserializableValueType = false;

    /**
     * Creates a simple Ignite-backed cache that is capable of communicating with other grid nodes that also host an instance of the same
     * underlying cache.
     *
     * @param gridName
     *            the name of the Ignite grid to use for communicating with other grid nodes
     * @param fullCache
     *            {@code true} if this cache should consider itself a "complete" cache (either a {@link CacheMode#LOCAL local} or
     *            {@link CacheMode#REPLICATED replicated} cache), {@code false} otherwise
     * @param backingCache
     *            the low-level Ignite cache instance
     * @param allowSentinelsInBackingCache
     *            {@code true} if sentinels for dummy values (defined by {@link EntityLookupCache}) are allowed to be stored in the cache
     */
    public SimpleIgniteBackedCache(final String gridName, final boolean fullCache, final IgniteCache<K, V> backingCache,
            final boolean allowSentinelsInBackingCache)
    {
        ParameterCheck.mandatoryString("gridName", gridName);
        ParameterCheck.mandatory("backingCache", backingCache);

        this.gridName = gridName;
        this.fullCache = fullCache;
        this.backingCache = backingCache;
        this.cacheName = backingCache.getName();
        this.allowSentinelsInBackingCache = allowSentinelsInBackingCache;

        this.instanceLogger = LoggerFactory.getLogger(this.getClass().getName() + "." + this.cacheName);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean contains(final K key)
    {
        this.instanceLogger.debug("Checking for containment of {}", key);

        final boolean containsKey = this.backingCache.containsKey(key);

        this.instanceLogger.debug("Cache contains key {}: {}", key, containsKey);

        return containsKey;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Collection<K> getKeys()
    {
        this.instanceLogger.debug("Retrieving all (local) keys");

        final Collection<K> keys = new LinkedHashSet<>();
        if (this.fullCache)
        {
            // local lookup is sufficient for local / replicated cache
            this.backingCache.localEntries(CachePeekMode.ALL).forEach(entry -> {
                final K key = entry.getKey();
                keys.add(key);
            });
        }
        else
        {
            // partitioned cache collects all keys from all instances
            final Ignite ignite = Ignition.ignite(this.gridName);
            final ClusterGroup cacheNodes = ignite.cluster().forCacheNodes(this.cacheName);

            final Collection<Collection<K>> allCacheKeys = ignite.compute(cacheNodes).broadcast(() -> {
                final Collection<K> localKeys = new LinkedHashSet<>();
                final IgniteCache<K, V> cache = Ignition.localIgnite().<K, V> getOrCreateCache(this.cacheName);
                cache.localEntries(CachePeekMode.ALL).forEach(entry -> {
                    final K key = entry.getKey();
                    localKeys.add(key);
                });
                return localKeys;
            });

            allCacheKeys.forEach(singleCacheKeys -> keys.addAll(singleCacheKeys));
        }

        if (this.instanceLogger.isTraceEnabled())
        {
            this.instanceLogger.trace("Retrieved (local) keys {}", keys);
        }
        else
        {
            this.instanceLogger.debug("Retrieved {} (local) keys", keys.size());
        }

        return keys;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public V get(final K key)
    {
        this.instanceLogger.debug("Getting value for key {}", key);

        final V value = this.backingCache.get(key);

        this.instanceLogger.debug("Retrieved value {} for key {}", value, key);

        return value;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void put(final K key, final V value)
    {
        this.instanceLogger.debug("Putting value {} into cache with key {}", value, key);

        if (!this.informedUnserializableValueType && value != null && !(value instanceof Serializable))
        {
            this.instanceLogger.info("Value type {} is not implementing serializable", value.getClass(), new Exception());
            this.informedUnserializableValueType = true;
        }

        // TransactionalCache always wraps values in holder
        // need effective value for sentinel check
        Object effectiveValue = value;
        if (effectiveValue instanceof ValueHolder)
        {
            effectiveValue = ((ValueHolder<?>) effectiveValue).getValue();
        }

        if (value == null)
        {
            this.instanceLogger.debug("Call to put with null-value for key {} instead of proper remove", key);

            this.backingCache.remove(key);
        }
        else if (!this.allowSentinelsInBackingCache && (VALUE_NOT_FOUND.equals(effectiveValue) || VALUE_NULL.equals(effectiveValue)))
        {
            this.instanceLogger.debug(
                    "Call to put with sentinel-value for key {} will be treated as a remove as sentinel values are not allowed in backing cache",
                    key);

            this.backingCache.remove(key);
        }
        else
        {
            this.backingCache.put(key, value);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void remove(final K key)
    {
        this.instanceLogger.debug("Removing value for key {}", key);

        this.backingCache.remove(key);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void clear()
    {
        this.instanceLogger.debug("Clearing all data");

        this.backingCache.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMetrics getMetrics()
    {
        final IgniteBackedCacheMetrics metrics = new IgniteBackedCacheMetrics(this.backingCache);
        return metrics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size()
    {
        return this.backingCache.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int localSize()
    {
        return this.backingCache.localSize();
    }

}