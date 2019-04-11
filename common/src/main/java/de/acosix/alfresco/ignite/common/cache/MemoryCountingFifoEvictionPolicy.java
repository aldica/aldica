/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.common.cache;

import java.util.concurrent.atomic.LongAdder;

import org.apache.ignite.cache.eviction.EvictableEntry;
import org.apache.ignite.cache.eviction.fifo.FifoEvictionPolicy;

/**
 * Instances of this eviction policy add memory counting to the base FIFO handling.
 *
 * @author Axel Faust
 */
public class MemoryCountingFifoEvictionPolicy<K, V> extends FifoEvictionPolicy<K, V>
{

    private final LongAdder memSize = new LongAdder();

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEntryAccessed(final boolean rmv, final EvictableEntry<K, V> entry)
    {
        if (!rmv)
        {
            if (entry.meta() == null && entry.isCached())
            {
                this.memSize.add(entry.size());
            }
        }
        else
        {
            if (entry.meta() != null)
            {
                this.memSize.add(-entry.size());
            }
        }
        super.onEntryAccessed(rmv, entry);
    }

    /**
     * Retrieves the number of bytes currently used by cache entries.
     *
     * @return the current memory size of all cache entries
     */
    @Override
    public long getCurrentMemorySize()
    {
        return this.memSize.longValue();
    }
}
