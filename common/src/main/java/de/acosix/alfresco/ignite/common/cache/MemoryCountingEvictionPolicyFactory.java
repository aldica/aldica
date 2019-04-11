/*
 * Copyright 2016 - 2019 Acosix GmbH
 *
 */
package de.acosix.alfresco.ignite.common.cache;

import org.apache.ignite.cache.eviction.AbstractEvictionPolicyFactory;

/**
 * @author Axel Faust
 */
public class MemoryCountingEvictionPolicyFactory<K, V> extends AbstractEvictionPolicyFactory<MemoryCountingEvictionPolicy<K, V>>
{

    private static final long serialVersionUID = -5624824420055547167L;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public MemoryCountingEvictionPolicy<K, V> create()
    {
        return new MemoryCountingEvictionPolicy<>();
    }

}
