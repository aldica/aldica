/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.cache;

import org.apache.ignite.cache.eviction.AbstractEvictionPolicy;
import org.apache.ignite.cache.eviction.EvictableEntry;

/**
 * Instances of this eviction policy implementation do nothing except counting memory used by current entries in the cache. Configuring any
 * instance to {@link #setMaxMemorySize(long) limit the memory size} or {@link #setMaxSize(int) limit the raw number of entries} of a cache
 * will have no effect.
 *
 * @author Axel Faust
 */
public class MemoryCountingEvictionPolicy<K, V> extends AbstractEvictionPolicy<K, V>
{

    private static final long serialVersionUID = 0L;

    /**
     * Default constructor
     */
    public MemoryCountingEvictionPolicy()
    {
        // NO-OP
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected int getCurrentSize()
    {
        // does not matter
        return 0;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected int shrink0()
    {
        // we don't shrink
        return 0;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected boolean removeMeta(final Object meta)
    {
        // we don't maintain a queue
        return false;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected boolean touch(final EvictableEntry<K, V> entry)
    {
        this.memSize.add(entry.size());
        entry.putMetaIfAbsent(Boolean.TRUE);
        // we don't maintain a queue
        return false;
    }

}
