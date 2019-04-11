/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.common.cache;

import org.apache.ignite.cache.eviction.fifo.FifoEvictionPolicyFactory;

/**
 * @author Axel Faust
 */
public class MemoryCountingFifoEvictionPolicyFactory<K, V> extends FifoEvictionPolicyFactory<K, V>
{

    private static final long serialVersionUID = -4345056504269279360L;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public MemoryCountingFifoEvictionPolicy<K, V> create()
    {
        final MemoryCountingFifoEvictionPolicy<K, V> policy = new MemoryCountingFifoEvictionPolicy<>();

        policy.setBatchSize(this.getBatchSize());
        policy.setMaxMemorySize(this.getMaxMemorySize());
        policy.setMaxSize(this.getMaxSize());

        return policy;
    }
}
