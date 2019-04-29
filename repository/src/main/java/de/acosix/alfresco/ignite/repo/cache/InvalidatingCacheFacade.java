/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.repo.cache;

import java.io.Serializable;
import java.util.Collection;

import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.cache.TransactionalCache.ValueHolder;
import org.alfresco.repo.cache.lookup.EntityLookupCache;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.ParameterCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust
 */
public class InvalidatingCacheFacade<K extends Serializable, V> implements SimpleCache<K, V>, CacheWithMetrics
{

    private static final Logger LOGGER = LoggerFactory.getLogger(InvalidatingCacheFacade.class);

    // value copied from EntityLookupCache (not accessible there)
    private static final Serializable VALUE_NULL = "@@VALUE_NULL@@";

    // value copied from EntityLookupCache (not accessible there)
    private static final Serializable VALUE_NOT_FOUND = "@@VALUE_NOT_FOUND@@";

    private final Logger instanceLogger;

    protected final SimpleCache<K, V> backingCache;

    protected final String cacheName;

    protected final Ignite grid;

    protected final boolean alwaysInvalidateOnPut;

    protected final boolean allowSentinelsInBackingCache;

    protected final String invalidationTopic;

    protected final String bulkInvalidationTopic;

    protected final SimpleCacheMetrics localMetrics;

    /**
     *
     * Creates a facade for a local cache that uses Ignite-backed data grid for communication with other grid nodes concerning invalidation
     * of cache
     * entries.
     *
     * @param cacheName
     *            the name of the backing cache
     * @param backingCache
     *            the low-level local cache instance
     * @param grid
     *            the Ignite grid instance to use for communication
     * @param alwaysInvalidateOnPut
     *            {@code true} if this facade should always send invalidation messages to other nodes on the same data grid when values are
     *            put into the backing cache, {@code false} otherwise
     * @param allowSentinelsInBackingCache
     *            {@code true} if sentinels for dummy values (defined by {@link EntityLookupCache}) are allowed to be stored in the cache
     */
    public InvalidatingCacheFacade(final String cacheName, final SimpleCache<K, V> backingCache, final Ignite grid,
            final boolean alwaysInvalidateOnPut, final boolean allowSentinelsInBackingCache)
    {
        ParameterCheck.mandatoryString("cacheName", cacheName);
        ParameterCheck.mandatory("backingCache", backingCache);
        ParameterCheck.mandatory("grid", grid);

        this.cacheName = cacheName;
        this.backingCache = backingCache;
        this.grid = grid;
        this.alwaysInvalidateOnPut = alwaysInvalidateOnPut;
        this.allowSentinelsInBackingCache = allowSentinelsInBackingCache;
        this.invalidationTopic = cacheName + "-invalidate";
        this.bulkInvalidationTopic = cacheName + "-bulkInvalidate";

        this.instanceLogger = LoggerFactory.getLogger(this.getClass().getPackage().getName() + ".SimpleCacheInstance." + cacheName);

        grid.message().localListen(this.invalidationTopic, (uuid, key) -> {
            LOGGER.debug("Received regular invalidation message on topic {} for {}", this.invalidationTopic, key);
            this.instanceLogger.debug("Received regular invalidation message for {}", key);
            @SuppressWarnings("unchecked")
            final K typedKey = (K) key;
            this.backingCache.remove(typedKey);

            // keep listening
            return true;
        });

        grid.message().localListen(this.bulkInvalidationTopic, (uuid, col) -> {
            LOGGER.debug("Received bulk invalidation message on topic {} for {}", this.invalidationTopic, col);
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

        if (!(backingCache instanceof CacheWithMetrics))
        {
            this.localMetrics = new SimpleCacheMetrics();
        }
        else
        {
            this.localMetrics = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(final K key)
    {
        LOGGER.debug("Checking cache {} for containment of {}", this.cacheName, key);
        this.instanceLogger.debug("Checking for containment of {}", key);

        final boolean containsKey = this.backingCache.contains(key);

        LOGGER.debug("Cache {} contains key {}: {}", this.cacheName, key, containsKey);
        this.instanceLogger.debug("Cache contains key {}: {}", key, containsKey);

        return containsKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<K> getKeys()
    {
        LOGGER.debug("Retrieving all (local) keys from cache {}", this.cacheName);
        this.instanceLogger.debug("Retrieving all (local) keys");

        final Collection<K> keys = this.backingCache.getKeys();

        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("Retrieved (local) keys {} from cache {}", keys, this.cacheName);
        }
        else
        {
            LOGGER.debug("Retrieved {} (local) keys from cache {}", keys.size(), this.cacheName);
        }
        if (this.instanceLogger.isTraceEnabled())
        {
            this.instanceLogger.trace("Retrieved (local) keys {}", keys, this.cacheName);
        }
        else
        {
            this.instanceLogger.debug("Retrieved {} (local) keys", keys.size(), this.cacheName);
        }

        return keys;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(final K key)
    {
        LOGGER.debug("Getting value for key {} from cache {}", key, this.cacheName);
        this.instanceLogger.debug("Getting value for key {}", key);

        final V value = this.backingCache.get(key);
        if (this.localMetrics != null)
        {
            if (value != null)
            {
                LOGGER.trace("Cache hit in {} for key {} yields {}", this.cacheName, key, value);
                this.instanceLogger.trace("Cache hit for key {} yields {}", key, value);
                this.localMetrics.recordHit();
            }
            else
            {
                LOGGER.trace("Cache miss in {} for key {}", this.cacheName, key);
                this.instanceLogger.trace("Cache miss for key {}", key);
                this.localMetrics.recordMiss();
            }
        }

        LOGGER.debug("Retrieved value {} for key {} from cache {}", value, key, this.cacheName);
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
        LOGGER.debug("Putting value {} into cache {} with key {}", value, this.cacheName, key);
        this.instanceLogger.debug("Putting value {} into cache with key {}", value, key);

        final V oldValue = this.backingCache.get(key);

        // TransactionalCache always wraps values in holder
        // need effective value for sentinel check
        Object effectiveValue = value;
        if (effectiveValue instanceof ValueHolder)
        {
            effectiveValue = ((ValueHolder<?>) effectiveValue).getValue();
        }

        boolean invalidate = this.alwaysInvalidateOnPut;

        if (value == null)
        {
            LOGGER.debug("Call to put with null-value for key {} instead of proper remove (cache: {})", key, this.cacheName);
            this.instanceLogger.debug("Call to put with null-value for key {} instead of proper remove", key);

            this.backingCache.remove(key);
            invalidate = invalidate || oldValue != null;
        }
        else if (!this.allowSentinelsInBackingCache && (VALUE_NOT_FOUND.equals(effectiveValue) || VALUE_NULL.equals(effectiveValue)))
        {
            LOGGER.debug(
                    "Call to put with sentinel-value for key {} will be treated as a remove as sentinel values are not allowed in backing cache (cache: {})",
                    key, this.cacheName);
            this.instanceLogger.debug(
                    "Call to put with sentinel-value for key {} will be treated as a remove as sentinel values are not allowed in backing cache",
                    key);

            this.backingCache.remove(key);
            invalidate = invalidate || oldValue != null;
        }
        else
        {
            this.backingCache.put(key, value);
            invalidate = invalidate || (oldValue != null && !EqualsHelper.nullSafeEquals(oldValue, value));
        }

        if (invalidate)
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
        LOGGER.debug("Removing value for key {} from cache {}", key, this.cacheName);
        this.instanceLogger.debug("Removing value for key {}", key);

        this.backingCache.remove(key);

        this.sendInvalidationMessage(this.invalidationTopic, key);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void clear()
    {
        LOGGER.debug("Clearing all data from cache {}", this.cacheName);
        this.instanceLogger.debug("Clearing all data");

        final Collection<K> keys = this.getKeys();

        this.backingCache.clear();

        if (!keys.isEmpty())
        {
            this.sendInvalidationMessage(this.bulkInvalidationTopic, keys);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMetrics getMetrics()
    {
        CacheMetrics metrics;
        if (this.localMetrics != null)
        {
            metrics = this.localMetrics;
        }
        else
        {
            // safe cast since localMetrics is only initalised if interface is NOT implemented
            metrics = ((CacheWithMetrics) this.backingCache).getMetrics();
        }
        return metrics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size()
    {
        // safe cast since localMetrics is only initalised if interface is NOT implemented
        return this.localMetrics == null ? ((CacheWithMetrics) this.backingCache).size() : this.localSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int localSize()
    {
        // safe cast since localMetrics is only initalised if interface is NOT implemented
        return this.localMetrics == null ? ((CacheWithMetrics) this.backingCache).localSize() : this.backingCache.getKeys().size();
    }

    protected void sendInvalidationMessage(final String topic, final Object msg)
    {
        final Object msgLogLabel = (LOGGER.isDebugEnabled() || this.instanceLogger.isDebugEnabled())
                ? (msg instanceof Collection<?> ? (((Collection<?>) msg).size() + " keys") : msg)
                : null;

        final ClusterGroup remotes = this.grid.cluster().forServers().forRemotes();
        if (!remotes.nodes().isEmpty())
        {
            LOGGER.debug("Sending remote message on topic {} for {}", topic, msgLogLabel);
            this.instanceLogger.debug("Sending remote message on topic {} for {}", topic, msgLogLabel);
            this.grid.message(remotes).send(topic, msg);
        }
        else
        {
            LOGGER.debug("Not sending remote message on topic {} for {} as there are no remote nodes", topic, msgLogLabel);
            this.instanceLogger.debug("Not sending remote message on topic {} for {} as there are no remote nodes", topic, msgLogLabel);
        }
    }
}