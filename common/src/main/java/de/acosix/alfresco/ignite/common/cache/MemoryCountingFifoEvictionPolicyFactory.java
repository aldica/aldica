/*
 * Copyright 2016 - 2019 Acosix GmbH
 */
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
