/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.trans;

import java.io.Serializable;

import org.alfresco.repo.cache.TransactionalCache;

/**
 * Instances of this class can be used as drop-in replacements for the Alfresco base class when transformation of keys / values is required
 * or desired between the cached and externally exposed data types.
 *
 * @param <K>
 *            the type of the keys for the values to be managed in this cache
 * @param <CV>
 *            the type of the values to be managed in this cache
 *
 * @author Axel Faust
 */
public class ObjectTransformingTransactionalCache<K extends Serializable, CV extends Serializable> extends TransactionalCache<K, CV>
{

    // can't really use the correct type parameters here due to EV != K, yet having to use K for both
    @SuppressWarnings("rawtypes")
    protected CacheObjectTransformer keyTransformer;

    // can't really use the correct type parameters here due to EV != CV, yet having to use CV for both
    @SuppressWarnings("rawtypes")
    protected CacheObjectTransformer valueTransformer;

    /**
     * @param keyTransformer
     *            the keyTransformer to set
     */
    public void setKeyTransformer(@SuppressWarnings("rawtypes") final CacheObjectTransformer keyTransformer)
    {
        this.keyTransformer = keyTransformer;
    }

    /**
     * @param valueTransformer
     *            the valueTransformer to set
     */
    public void setValueTransformer(@SuppressWarnings("rawtypes") final CacheObjectTransformer valueTransformer)
    {
        this.valueTransformer = valueTransformer;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void put(final K key, final CV value)
    {
        final K realKey;
        final CV realValue;

        if (this.keyTransformer != null)
        {
            realKey = (K) this.keyTransformer.transformToCacheValue(key);
        }
        else
        {
            realKey = key;
        }
        if (this.valueTransformer != null)
        {
            realValue = (CV) this.valueTransformer.transformToCacheValue(value);
        }
        else
        {
            realValue = value;
        }
        super.put(realKey, realValue);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public CV get(final K key)
    {
        final K realKey;
        if (this.keyTransformer != null)
        {
            realKey = (K) this.keyTransformer.transformToCacheValue(key);
        }
        else
        {
            realKey = key;
        }

        final CV value = super.get(realKey);
        final CV realValue = (CV) this.valueTransformer.transformToExternalValue(value);
        return realValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj)
    {
        // no customisations currently
        return super.equals(obj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        // no customisations currently
        return super.hashCode();
    }

}
