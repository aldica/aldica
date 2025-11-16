/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.aldica.common.ignite.compute.CacheKeySetLookup;
import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.cache.TransactionalCache.ValueHolder;
import org.alfresco.repo.cache.lookup.EntityLookupCache;
import org.alfresco.util.ParameterCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteException;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteFutureTimeoutException;
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

    /**
     * This enum provides the supported modes of operation for an Ignite-backed cache instance.
     *
     * @author Axel Faust
     */
    public enum Mode
    {
        PARTITIONED,
        REPLICATED;
    }

    // value copied from EntityLookupCache (not accessible there)
    private static final Serializable VALUE_NULL = "@@VALUE_NULL@@";

    // value copied from EntityLookupCache (not accessible there)
    private static final Serializable VALUE_NOT_FOUND = "@@VALUE_NOT_FOUND@@";

    private final Logger instanceLogger;

    protected final Ignite grid;

    protected final Mode cacheMode;

    protected final String cacheName;

    protected final IgniteCache<K, V> backingCache;

    protected final boolean allowSentinelsInBackingCache;

    protected volatile boolean informedUnserializableValueType = false;

    protected final String invalidationTopic;

    protected final String bulkInvalidationTopic;

    /**
     * Creates a simple Ignite-backed cache that is capable of communicating with other grid nodes that also host an instance of the same
     * underlying cache.
     *
     * @param grid
     *     the Ignite grid instance to use for communication
     * @param cacheMode
     *     the mode of operation for this cache instance
     * @param backingCache
     *     the low-level Ignite cache instance
     * @param allowSentinelsInBackingCache
     *     {@code true} if sentinels for dummy values (defined by {@link EntityLookupCache}) are allowed to be stored in the cache
     */
    public SimpleIgniteBackedCache(final Ignite grid, final Mode cacheMode, final IgniteCache<K, V> backingCache,
            final boolean allowSentinelsInBackingCache)
    {
        ParameterCheck.mandatory("grid", grid);
        ParameterCheck.mandatory("cacheMode", cacheMode);
        ParameterCheck.mandatory("backingCache", backingCache);

        this.grid = grid;
        this.cacheMode = cacheMode;
        this.backingCache = backingCache;
        this.cacheName = backingCache.getName();
        this.allowSentinelsInBackingCache = allowSentinelsInBackingCache;

        this.invalidationTopic = this.cacheName + "-invalidate";
        this.bulkInvalidationTopic = this.cacheName + "-bulkInvalidate";

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
        if (this.cacheMode == Mode.REPLICATED)
        {
            // local lookup is sufficient for replicated cache
            // use withKeepBinary to avoid unnecessary deserialisation of values
            final IgniteCache<K, ?> cache = this.backingCache.withKeepBinary();
            cache.localEntries(CachePeekMode.ALL).forEach(entry -> {
                K key = entry.getKey();
                if (key instanceof BinaryObject)
                {
                    key = ((BinaryObject) key).deserialize();
                }
                keys.add(key);
            });
        }
        else
        {
            // partitioned cache collects all keys from all instances
            // using exclusively BinaryObject since companion grid members runs without key/value classes
            final ClusterGroup cacheNodes = this.grid.cluster().forCacheNodes(this.cacheName);

            final Collection<Collection<Object>> allCacheKeys = this.grid.compute(cacheNodes)
                    .broadcast(new CacheKeySetLookup(this.cacheName));

            final ClassLoader cldr = this.getClass().getClassLoader();
            @SuppressWarnings("unchecked")
            final Function<Object, K> mapper = cacheKey -> {
                final K key;
                if (cacheKey instanceof BinaryObject)
                {
                    key = ((BinaryObject) cacheKey).deserialize(cldr);
                }
                else
                {
                    key = (K) cacheKey;
                }
                return key;
            };
            allCacheKeys.stream().flatMap(Collection::stream).map(mapper).collect(Collectors.toCollection(() -> keys));
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

        final V value = this.getImpl(key);

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

    /**
     * Performs the actual retrieval of a single value from the backing cache.
     *
     * @param key
     *     the key to use in the lookup
     * @return the resolved value
     */
    @SuppressWarnings("unchecked")
    protected V getImpl(final K key)
    {
        // using withKeepBinary avoids any deserialisation happening in Ignite async threads
        // which might potentially block them with cascading lookups due to serialisation optimisations
        // (GridCacheAdapter.get internally uses getAsync, moving deserialisation to sys-stripe threads)
        final Object cacheValue = this.backingCache.withKeepBinary().get(key);

        final V value;
        if (cacheValue instanceof BinaryObject)
        {
            value = ((BinaryObject) cacheValue).deserialize();
        }
        else
        {
            value = (V) cacheValue;
        }
        return value;
    }

    protected <T> T waitUntilComplete(final IgniteFuture<T> fut, final long timeout, final String operation)
    {
        // using futures + timeouts for cache operations is necessary to avoid system workers
        // (e.g. discovery worker) from locking up if they happen to trigger a cache operation
        try
        {
            return fut.get(timeout, TimeUnit.MILLISECONDS);
        }
        catch (final IgniteFutureTimeoutException e)
        {
            this.instanceLogger.warn("Timed out waiting for cache {}-operation to complete", operation);
        }
        catch (final IgniteException e)
        {
            this.instanceLogger.error("Error waiting for cache {}-operation to complete", operation, e);
        }

        return null;
    }
}
