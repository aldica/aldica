/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.repo.cache;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;

import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.util.ParameterCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleIgniteBackedCache.class);

    protected final String gridName;

    protected final IgniteCache<K, V> backingCache;

    protected volatile boolean informedUnserializableValueType = false;

    /**
     * Creates a simple Ignite-backed cache that is capable of communicating with other grid nodes that also host an instance of the same
     * underlying cache.
     *
     * @param gridName
     *            the name of the Ignite grid to use for communicating with other grid nodes
     * @param backingCache
     *            the low-level Ignite cache instance
     */
    public SimpleIgniteBackedCache(final String gridName, final IgniteCache<K, V> backingCache)
    {
        ParameterCheck.mandatoryString("gridName", gridName);
        ParameterCheck.mandatory("backingCache", backingCache);
        this.gridName = gridName;
        this.backingCache = backingCache;
    }

    /**
     * Creates a simple Ignite-backed cache that should not interact with its corresponding instances on other grid nodes.
     *
     * @param backingCache
     *            the low-level Ignite cache instance
     */
    public SimpleIgniteBackedCache(final IgniteCache<K, V> backingCache)
    {
        ParameterCheck.mandatory("backingCache", backingCache);
        this.gridName = null;
        this.backingCache = backingCache;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean contains(final K key)
    {
        LOGGER.debug("Checking cache {} for containment of {}", this.backingCache.getName(), key);
        final boolean containsKey = this.backingCache.containsKey(key);
        LOGGER.debug("Cache {} contains key {}: {}", this.backingCache.getName(), key, containsKey);
        return containsKey;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Collection<K> getKeys()
    {
        LOGGER.debug("Retrieving all (local) keys from cache {}", this.backingCache.getName());

        final Collection<K> keys = new LinkedHashSet<>();
        if (this.gridName == null)
        {
            this.backingCache.localEntries(CachePeekMode.ALL).forEach(entry -> {
                final K key = entry.getKey();
                keys.add(key);
            });
        }
        else
        {
            final Ignite ignite = Ignition.ignite(this.gridName);
            final ClusterGroup cacheNodes = ignite.cluster().forCacheNodes(this.backingCache.getName());

            final String cacheName = this.backingCache.getName();
            final Collection<Collection<K>> allCacheKeys = ignite.compute(cacheNodes).broadcast(() -> {
                final Collection<K> localKeys = new LinkedHashSet<>();
                final IgniteCache<K, V> cache = Ignition.localIgnite().<K, V> getOrCreateCache(cacheName);
                cache.localEntries(CachePeekMode.ALL).forEach(entry -> {
                    final K key = entry.getKey();
                    localKeys.add(key);
                });
                return localKeys;
            });

            allCacheKeys.forEach(singleCacheKeys -> keys.addAll(singleCacheKeys));
        }
        LOGGER.debug("Retrieved keys {} from cache {}", keys, this.backingCache.getName());

        return keys;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public V get(final K key)
    {
        LOGGER.debug("Getting value for key {} from cache {}", key, this.backingCache.getName());
        final V value = this.backingCache.get(key);
        LOGGER.debug("Retrieved value {} for key {} from cache {}", value, key, this.backingCache.getName());
        return value;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void put(final K key, final V value)
    {
        LOGGER.debug("Putting value {} into cache {} with key {}", value, this.backingCache.getName(), key);
        if (!this.informedUnserializableValueType && value != null && !(value instanceof Serializable))
        {
            LOGGER.info("Value type {} is not implementing serializable (cache: {})", value.getClass(), this.backingCache.getName(),
                    new Exception());
            this.informedUnserializableValueType = true;
        }

        if (value == null)
        {
            LOGGER.debug("Call to put with null-value for key {} instead of proper remove (cache: {})", key, this.backingCache.getName());
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
        LOGGER.debug("Removing value for key {} from cache {}", key, this.backingCache.getName());
        this.backingCache.remove(key);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void clear()
    {
        LOGGER.debug("Clearing all data from cache {}", this.backingCache.getName());
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
