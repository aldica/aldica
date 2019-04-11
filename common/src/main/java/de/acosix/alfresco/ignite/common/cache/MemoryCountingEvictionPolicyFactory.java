/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
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
