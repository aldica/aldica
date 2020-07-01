/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.io.Serializable;
import java.util.Set;

import org.alfresco.service.namespace.QName;
import org.apache.ignite.binary.BinarySerializer;

/**
 * Instances of this class wrap regular node aspects set in a thin facade in order to enable an optimised {@link BinarySerializer} to be
 * configured specifically to handle node aspects.
 *
 * @author Axel Faust
 */
public class NodeAspectsTransformer implements CacheValueTransformer<Serializable, Serializable>
{

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Serializable transformToExternalValue(final Serializable cacheValue)
    {
        // we use a wrapped / sub-classed map in cache representation, keeping all value contracts intact
        // can just pass the cache value as-is
        return cacheValue;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Serializable transformToCacheValue(final Serializable externalValue)
    {
        Serializable cacheValue = externalValue;
        if (cacheValue instanceof Set<?>)
        {
            @SuppressWarnings("unchecked")
            final Set<QName> externalSet = (Set<QName>) cacheValue;
            cacheValue = new NodeAspectsCacheSet(externalSet);
        }
        return cacheValue;
    }
}
