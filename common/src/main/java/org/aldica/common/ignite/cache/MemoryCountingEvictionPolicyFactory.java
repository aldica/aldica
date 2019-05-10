/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.cache;

import org.apache.ignite.cache.eviction.AbstractEvictionPolicyFactory;

/**
 * Instances of this factory create eviction policy instances which do nothing except counting the memory used by current entries in the
 * cache. Configuring any instance to {@link #setMaxMemorySize(long) limit the memory size} or {@link #setMaxSize(int) limit the raw number
 * of entries} of a cache will have no effect.
 *
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
