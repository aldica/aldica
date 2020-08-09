/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.util;

import java.io.Serializable;
import java.util.Collection;

import org.alfresco.repo.cache.SimpleCache;

/**
 * Instances of this class facade real caches and capture keys/values during {@link #put(Serializable, Object) put-} and
 * {@link #get(Serializable) get-}operations so they can be accessed by unit tests, if an Alfresco class / component does not provide the
 * means for the value type to be handled directly.
 *
 * @author Axel Faust
 */
public class CapturingCacheFacade<K extends Serializable, V> implements SimpleCache<K, V>
{

    private final SimpleCache<K, V> delegate;

    private K lastKey;

    private V lastValue;

    public CapturingCacheFacade(final SimpleCache<K, V> delegate)
    {
        this.delegate = delegate;
    }

    /**
     * @return the lastKey
     */
    public K getLastKey()
    {
        return this.lastKey;
    }

    /**
     * @return the lastValue
     */
    public V getLastValue()
    {
        return this.lastValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(final K key)
    {
        return this.delegate.contains(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<K> getKeys()
    {
        return this.delegate.getKeys();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(final K key)
    {
        final V value = this.delegate.get(key);
        this.lastKey = key;
        this.lastValue = value;

        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(final K key, final V value)
    {
        this.lastKey = key;
        this.lastValue = value;

        this.delegate.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(final K key)
    {
        this.delegate.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear()
    {
        this.delegate.clear();
    }

}
