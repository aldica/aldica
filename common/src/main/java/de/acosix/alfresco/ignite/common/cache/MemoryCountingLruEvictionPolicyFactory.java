/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.common.cache;

import org.apache.ignite.cache.eviction.lru.LruEvictionPolicyFactory;

/**
 * @author Axel Faust
 */
public class MemoryCountingLruEvictionPolicyFactory<K, V> extends LruEvictionPolicyFactory<K, V>
{

    private static final long serialVersionUID = 6002735293810063644L;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public MemoryCountingLruEvictionPolicy<K, V> create()
    {
        final MemoryCountingLruEvictionPolicy<K, V> policy = new MemoryCountingLruEvictionPolicy<>();

        policy.setBatchSize(this.getBatchSize());
        policy.setMaxMemorySize(this.getMaxMemorySize());
        policy.setMaxSize(this.getMaxSize());

        return policy;
    }
}
