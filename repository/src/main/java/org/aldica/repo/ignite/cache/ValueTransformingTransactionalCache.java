/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.io.Serializable;

import org.alfresco.repo.cache.TransactionalCache;
import org.alfresco.util.PropertyCheck;

/**
 * Instances of this class can be used as drop-in replacements for the Alfresco base class when transformation of values is required or
 * desired between the cached and externally exposed data types.
 *
 * @param <K>
 *            the type of the keys for the values to be managed in this cache
 * @param <CV>
 *            the type of the values to be managed in this cache
 *
 * @author Axel Faust
 */
public class ValueTransformingTransactionalCache<K extends Serializable, CV extends Serializable> extends TransactionalCache<K, CV>
{

    // can't really use the correct type parameters here due to EV != CV, yet having to use CV for both
    @SuppressWarnings("rawtypes")
    protected CacheValueTransformer valueTransformer;

    /**
     * @param valueTransformer
     *            the valueTransformer to set
     */
    public void setValueTransformer(@SuppressWarnings("rawtypes") final CacheValueTransformer valueTransformer)
    {
        this.valueTransformer = valueTransformer;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() throws Exception
    {
        PropertyCheck.mandatory(this, "valueTransformer", this.valueTransformer);
        super.afterPropertiesSet();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void put(final K key, final CV value)
    {
        // we have to wrangle the type - we know it will be something different
        // internal implementation does not care and we make sure on get() that it is corrected again
        @SuppressWarnings("unchecked")
        final CV realValue = (CV) this.valueTransformer.transformToCacheValue(value);
        super.put(key, realValue);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public CV get(final K keyIn)
    {
        final CV value = super.get(keyIn);
        // we know CV from super.get() will have been something different due to put() override
        @SuppressWarnings("unchecked")
        final CV realValue = (CV) this.valueTransformer.transformToExternalValue(value);
        return realValue;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o)
    {
        // no customisations currently
        return super.equals(o);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        // no customisations currently
        return super.hashCode();
    }
}
