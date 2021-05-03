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
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.ParameterCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
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

    /**
     * This enum provides the supported modes of operation for an Ignite-backed cache instance.
     *
     * @author Axel Faust
     */
    public static enum Mode
    {

        LOCAL(true, false, false),
        LOCAL_INVALIDATING_ON_CHANGE(true, true, false),
        LOCAL_INVALIDATING(true, true, true),
        PARTITIONED(false, false, false),
        REPLICATED(true, false, false);

        private final boolean consideredFullCache;

        private final boolean handleInvalidations;

        private final boolean alwaysInvalidateOnPut;

        private Mode(final boolean consideredFullCache, final boolean handleInvalidations, final boolean alwaysInvalidateOnPut)
        {
            this.consideredFullCache = consideredFullCache;
            this.handleInvalidations = handleInvalidations;
            this.alwaysInvalidateOnPut = alwaysInvalidateOnPut;
        }

        /**
         * @return the consideredFullCache
         */
        public boolean isConsideredFullCache()
        {
            return this.consideredFullCache;
        }

        /**
         * @return the handleInvalidations
         */
        public boolean isHandleInvalidations()
        {
            return this.handleInvalidations;
        }

        /**
         * @return the alwaysInvalidateOnPut
         */
        public boolean isAlwaysInvalidateOnPut()
        {
            return this.alwaysInvalidateOnPut;
        }

        /**
         * Retrieves the appropriate variant of local cache mode to use for the specified invalidation parameters.
         *
         * @param invalidate
         *            {@code true} if invalidations should be handled by the mode
         * @param alwaysInvalidateOnPut
         *            {@code true} if invalidations handled by the mode should be performed on every put operation
         * @return the appropriate cache mode
         */
        public static Mode getLocalCacheMode(final boolean invalidate, final boolean alwaysInvalidateOnPut)
        {
            Mode result = Mode.LOCAL;
            if (invalidate)
            {
                result = alwaysInvalidateOnPut ? Mode.LOCAL_INVALIDATING : Mode.LOCAL_INVALIDATING_ON_CHANGE;
            }
            return result;
        }
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
     *            the Ignite grid instance to use for communication
     * @param cacheMode
     *            the mode of operation for this cache instance
     * @param backingCache
     *            the low-level Ignite cache instance
     * @param allowSentinelsInBackingCache
     *            {@code true} if sentinels for dummy values (defined by {@link EntityLookupCache}) are allowed to be stored in the cache
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

        if (cacheMode.isHandleInvalidations())
        {
            grid.message().localListen(this.invalidationTopic, (uuid, key) -> {
                this.instanceLogger.debug("Received invalidation message for {}", key);
                @SuppressWarnings("unchecked")
                final K typedKey = (K) key;
                this.backingCache.remove(typedKey);

                // keep listening
                return true;
            });

            grid.message().localListen(this.bulkInvalidationTopic, (uuid, col) -> {
                this.instanceLogger.debug("Received bulk invalidation message for {}", col);
                if (col instanceof Collection<?>)
                {
                    @SuppressWarnings("unchecked")
                    final Collection<K> keyCollection = (Collection<K>) col;
                    keyCollection.forEach(key -> {
                        this.backingCache.remove(key);
                    });
                }
                // keep listening
                return true;
            });
        }
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
        if (this.cacheMode.isConsideredFullCache())
        {
            // local lookup is sufficient for local / replicated cache
            // use withKeepBinary to avoid unnecessary deseralisation of values
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
            final ClusterGroup cacheNodes = this.grid.cluster().forCacheNodes(this.cacheName);

            final Collection<Collection<K>> allCacheKeys = this.grid.compute(cacheNodes).broadcast(() -> {
                final Collection<K> localKeys = new LinkedHashSet<>();
                // use withKeepBinary to avoid unnecessary deseralisation of values
                final IgniteCache<K, V> cache = Ignition.localIgnite().<K, V> getOrCreateCache(this.cacheName).withKeepBinary();
                cache.localEntries(CachePeekMode.ALL).forEach(entry -> {
                    K key = entry.getKey();
                    if (key instanceof BinaryObject)
                    {
                        key = ((BinaryObject) key).deserialize();
                    }
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

        boolean invalidate = this.cacheMode.isAlwaysInvalidateOnPut();
        if (value == null)
        {
            this.instanceLogger.debug("Call to put with null-value for key {} instead of proper remove", key);

            invalidate = this.backingCache.remove(key) || invalidate;
        }
        else if (!this.allowSentinelsInBackingCache && (VALUE_NOT_FOUND.equals(effectiveValue) || VALUE_NULL.equals(effectiveValue)))
        {
            this.instanceLogger.debug(
                    "Call to put with sentinel-value for key {} will be treated as a remove as sentinel values are not allowed in backing cache",
                    key);

            invalidate = this.backingCache.remove(key) || invalidate;
        }
        else
        {
            final V oldValue = this.getAndPutImpl(key, value);
            invalidate = invalidate
                    || (this.cacheMode.isHandleInvalidations() && oldValue != null && !EqualsHelper.nullSafeEquals(oldValue, value));
        }

        if (this.cacheMode.isHandleInvalidations() && invalidate)
        {
            this.sendInvalidationMessage(this.invalidationTopic, key);
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
     *            the key to use in the lookup
     * @return the resolved value
     */
    @SuppressWarnings("unchecked")
    protected V getImpl(final K key)
    {
        // using withKeepBinary avoids and deserialisation happening in Ignite async threads, which might potentially block them with
        // cascading lookups due to serialisation optimisations
        final IgniteCache<K, Object> cache = this.backingCache.withKeepBinary();
        final Object cacheValue = cache.get(key);

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

    /**
     * Performs the actual retrieval and update of a single entry in the backing cache.
     *
     * @param key
     *            the key to use in the update
     * @param newValue
     *            the new value to store for the key
     * @return the resolved previous value, may be {@code null} if no entry existed
     */
    @SuppressWarnings("unchecked")
    protected V getAndPutImpl(final K key, final V newValue)
    {
        // using withKeepBinary avoids and deserialisation happening in Ignite async threads, which might potentially block them with
        // cascading lookups due to serialisation optimisations
        final IgniteCache<K, Object> cache = this.backingCache.withKeepBinary();
        final Object cacheValue = cache.getAndPut(key, newValue);

        final V oldValue;
        if (cacheValue instanceof BinaryObject)
        {
            oldValue = ((BinaryObject) cacheValue).deserialize();
        }
        else
        {
            oldValue = (V) cacheValue;
        }
        return oldValue;
    }

    protected void sendInvalidationMessage(final String topic, final Object msg)
    {
        final Object msgLogLabel = this.instanceLogger.isDebugEnabled()
                ? (msg instanceof Collection<?> ? (((Collection<?>) msg).size() + " keys") : msg)
                : null;

        final ClusterGroup remotes = this.grid.cluster().forServers().forRemotes();
        if (!remotes.nodes().isEmpty())
        {
            this.instanceLogger.debug("Sending remote message on topic {} for {}", topic, msgLogLabel);
            this.grid.message(remotes).send(topic, msg);
        }
        else
        {
            this.instanceLogger.debug("Not sending remote message on topic {} for {} as there are no remote nodes", topic, msgLogLabel);
        }
    }
}
