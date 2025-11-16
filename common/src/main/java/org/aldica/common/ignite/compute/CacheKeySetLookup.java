/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.compute;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.lang.IgniteCallable;

/**
 * Instances of this are used to perform lookups of cache key sets in a specified (distributed) cache.
 *
 * @author Axel Faust
 */
public class CacheKeySetLookup implements IgniteCallable<Collection<Object>>
{

    private static final long serialVersionUID = -6657096690132084427L;

    private final String cacheName;

    /**
     * Creates a new instance of this class.
     *
     * @param cacheName
     *     the name of the cache in which to collect cache keys
     */
    public CacheKeySetLookup(final String cacheName)
    {
        this.cacheName = cacheName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Object> call() throws Exception
    {
        final Collection<Object> localKeys = new LinkedHashSet<>();
        // use withKeepBinary to avoid distributed deserialisation of values
        // (especially important when running in companion app)
        final IgniteCache<?, ?> cache = Ignition.localIgnite().<Object, Object> getOrCreateCache(this.cacheName).withKeepBinary();
        cache.localEntries(CachePeekMode.ALL).forEach(entry -> {
            final Object key = entry.getKey();
            localKeys.add(key);
        });
        return localKeys;
    }

}
