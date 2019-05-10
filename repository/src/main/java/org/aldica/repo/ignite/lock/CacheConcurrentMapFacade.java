/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.lock;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.alfresco.repo.lock.mem.AbstractLockStore;
import org.alfresco.repo.lock.mem.LockStore;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.ParameterCheck;
import org.apache.ignite.IgniteCache;

/**
 * Instances of this class act as a {@link ConcurrentMap} compatible facade to an Ignite cache. This is required in order to reuse the
 * Alfresco {@link AbstractLockStore lock store base implementation}.
 *
 * The operations {@link #containsValue(Object) containsValue}, {@link #keySet()}, {@link #values()} and {@link #entrySet()} are
 * {@link UnsupportedOperationException unsupported} by this
 * facade as data in Ignite caches may be distributed in a cluster and accessing all entries is an extremely expensive operation and
 * atomicity cannot be guaranteed as would be required by a concurrent map. {@link #keySet()} is actually being used by
 * {@link LockStore#getNodes()} but that operation itself is not called.
 *
 * @author Axel Faust
 */
public class CacheConcurrentMapFacade<K, V> implements ConcurrentMap<K, V>
{

    protected final IgniteCache<K, V> igniteCache;

    protected final Class<K> keyClass;

    public CacheConcurrentMapFacade(final IgniteCache<K, V> igniteCache, final Class<K> keyClass)
    {
        ParameterCheck.mandatory("igniteCache", igniteCache);
        ParameterCheck.mandatory("keyClass", keyClass);
        this.igniteCache = igniteCache;
        this.keyClass = keyClass;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size()
    {
        final int size = this.igniteCache.size();
        return size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty()
    {
        boolean isEmpty;

        // shortcut by only checking local cache size
        final int localSize = this.igniteCache.localSize();
        if (localSize != 0)
        {
            isEmpty = false;
        }
        else
        {
            final int size = this.size();
            isEmpty = size == 0;
        }
        return isEmpty;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(final Object key)
    {
        final boolean contained;
        if (this.keyClass.isInstance(key))
        {
            contained = this.igniteCache.containsKey(this.keyClass.cast(key));
        }
        else
        {
            contained = false;
        }
        return contained;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(final Object value)
    {
        throw new UnsupportedOperationException("Cannot check for containment of a specific value by the potentially distributed cache");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(final Object key)
    {
        final V value;
        if (this.keyClass.isInstance(key))
        {
            value = this.igniteCache.get(this.keyClass.cast(key));
        }
        else
        {
            value = null;
        }
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V put(final K key, final V value)
    {
        final V oldValue = this.igniteCache.getAndPut(key, value);
        return oldValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V remove(final Object key)
    {
        final V removed;
        if (this.keyClass.isInstance(key))
        {
            removed = this.igniteCache.getAndRemove(this.keyClass.cast(key));
        }
        else
        {
            removed = null;
        }
        return removed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putAll(final Map<? extends K, ? extends V> m)
    {
        this.igniteCache.putAll(m);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear()
    {
        this.igniteCache.removeAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<K> keySet()
    {
        throw new UnsupportedOperationException("Cannot provide a set view of keys mapped by the potentially distributed cache");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<V> values()
    {
        throw new UnsupportedOperationException("Cannot provide a set view of values mapped by the potentially distributed cache");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet()
    {
        throw new UnsupportedOperationException("Cannot provide a set view of entries mapped by the potentially distributed cache");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V putIfAbsent(final K key, final V value)
    {
        return this.igniteCache.getAndPutIfAbsent(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(final Object key, final Object value)
    {
        final boolean removed;
        if (this.keyClass.isInstance(key))
        {
            // can't call remove(K, V) since we only have (Object, Object)
            final K ckey = this.keyClass.cast(key);
            final V oldValue = this.igniteCache.get(ckey);
            if (EqualsHelper.nullSafeEquals(oldValue, value))
            {
                removed = this.igniteCache.remove(ckey, oldValue);
            }
            else
            {
                removed = false;
            }
        }
        else
        {
            removed = false;
        }
        return removed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean replace(final K key, final V oldValue, final V newValue)
    {
        final boolean replaced = this.igniteCache.replace(key, oldValue, newValue);
        return replaced;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V replace(final K key, final V value)
    {
        final V oldValue = this.igniteCache.getAndReplace(key, value);
        return oldValue;
    }

}
