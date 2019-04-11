/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.repo.cache;

import java.io.Serializable;
import java.util.Collection;

import org.alfresco.repo.cache.SimpleCache;
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

    protected final SimpleCache<K, V> backingCache;

    protected final String cacheName;

    protected final Ignite grid;

    protected final boolean alwaysInvalidateOnPut;

    protected final String invalidationTopic;

    protected final String bulkInvalidationTopic;

    protected final SimpleCacheMetrics localMetrics;

    public InvalidatingCacheFacade(final SimpleCache<K, V> backingCache, final String cacheName, final Ignite grid,
            final boolean alwaysInvalidateOnPut)
    {
        ParameterCheck.mandatory("backingCache", backingCache);
        ParameterCheck.mandatoryString("cacheName", cacheName);
        ParameterCheck.mandatory("grid", grid);

        this.backingCache = backingCache;
        this.cacheName = cacheName;
        this.grid = grid;
        this.alwaysInvalidateOnPut = alwaysInvalidateOnPut;
        this.invalidationTopic = cacheName + "-invalidate";
        this.bulkInvalidationTopic = cacheName + "-bulkInvalidate";

        grid.message().localListen(this.invalidationTopic, (uuid, key) -> {
            LOGGER.debug("Received message on topic {} for {}", this.invalidationTopic, key);
            @SuppressWarnings("unchecked")
            final K typedKey = (K) key;
            this.backingCache.remove(typedKey);

            // keep listening
            return true;
        });

        grid.message().localListen(this.bulkInvalidationTopic, (uuid, col) -> {
            LOGGER.debug("Received message on topic {} for {}", this.invalidationTopic, col);
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
        final boolean containsKey = this.backingCache.contains(key);
        LOGGER.debug("Cache {} contains key {}: {}", this.cacheName, key, containsKey);
        return containsKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<K> getKeys()
    {
        LOGGER.debug("Retrieving all (local) keys from cache {}", this.cacheName);
        final Collection<K> keys = this.backingCache.getKeys();
        LOGGER.debug("Retrieved (local) keys {} from cache {}", keys, this.cacheName);
        return keys;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(final K key)
    {
        LOGGER.debug("Getting value for key {} from cache {}", key, this.cacheName);

        final V value = this.backingCache.get(key);
        if (this.localMetrics != null)
        {
            if (value != null)
            {
                LOGGER.trace("Cache hit in {} for key {} yields {}", this.cacheName, key, value);
                this.localMetrics.recordHit();
            }
            else
            {
                LOGGER.trace("Cache miss in {} for key {}", this.cacheName, key);
                this.localMetrics.recordMiss();
            }
        }

        LOGGER.debug("Retrieved value {} for key {} from cache {}", value, key, this.cacheName);

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

        final V oldValue = this.backingCache.get(key);
        final boolean invalidate = this.alwaysInvalidateOnPut || (oldValue != null && !EqualsHelper.nullSafeEquals(oldValue, value));

        this.backingCache.put(key, value);

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
        final ClusterGroup remotes = this.grid.cluster().forServers().forRemotes();
        if (!remotes.nodes().isEmpty())
        {
            LOGGER.debug("Sending remote message on topic {} for {}", topic, msg);
            this.grid.message(remotes).send(topic, msg);
        }
        else
        {
            LOGGER.debug("Not sending remote message on topic {} for {} as there are no remote nodes", topic, msg);
        }
    }
}