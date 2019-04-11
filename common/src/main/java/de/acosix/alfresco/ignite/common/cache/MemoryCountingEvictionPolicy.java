/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.common.cache;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.atomic.LongAdder;

import org.apache.ignite.cache.eviction.EvictableEntry;
import org.apache.ignite.cache.eviction.EvictionPolicy;
import org.apache.ignite.internal.util.tostring.GridToStringBuilder;

/**
 * Instances of this eviction policy implementation do nothing except counting the memory used by current entries in the cache.
 *
 * @author Axel Faust
 */
public class MemoryCountingEvictionPolicy<K, V> implements EvictionPolicy<K, V>, Externalizable
{

    private static final long serialVersionUID = 0L;

    private final LongAdder memSize = new LongAdder();

    /**
     * Default constructor
     */
    public MemoryCountingEvictionPolicy()
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEntryAccessed(final boolean rmv, final EvictableEntry<K, V> entry)
    {
        if (!rmv)
        {
            if (entry.putMetaIfAbsent(Boolean.TRUE) == null && entry.isCached())
            {
                this.memSize.add(entry.size());
            }
        }
        else
        {
            final boolean wasAdded = entry.removeMeta(Boolean.TRUE);
            if (wasAdded)
            {
                this.memSize.add(-entry.size());
            }
        }
    }

    /**
     * Retrieves the number of bytes currently used by cache entries.
     *
     * @return the current memory size of all cache entries
     */
    public long getCurrentMemorySize()
    {
        return this.memSize.longValue();
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(final ObjectOutput out) throws IOException
    {
        // NO-OP - no state to write
    }

    /** {@inheritDoc} */
    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException
    {
        // NO-OP - no state to read
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
        return GridToStringBuilder.toString(MemoryCountingEvictionPolicy.class, this);
    }
}
