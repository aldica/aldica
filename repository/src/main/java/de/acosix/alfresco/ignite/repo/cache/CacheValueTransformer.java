/*
 * Copyright 2016 - 2019 Acosix GmbH
 */
package de.acosix.alfresco.ignite.repo.cache;

import java.io.Serializable;

/**
 * Instances of this interface are used to transform data values for more efficient use within Alfresco caches, e.g. by deduplicating data
 * read from caches and swapping complex data values with resolvable identity information to reduce the footprint in serialised form.
 *
 * @author Axel Faust
 *
 * @param <EV>
 *            the type of the value as exposed to external clients
 * @param <CV>
 *            the type of the value held inside the cache
 */
public interface CacheValueTransformer<EV extends Serializable, CV extends Serializable>
{

    /**
     * Transforms a value stored within the cache to a value that should be exposed to external clients of the cache.
     *
     * @param cacheValue
     *            the value stored in the cache
     * @return the value to be exposed externally
     */
    EV transformToExternalValue(CV cacheValue);

    /**
     * Transform a value provided by an external client to a value that should be stored inside the cache.
     *
     * @param externalValue
     *            the value provided by an external client
     * @return the value to be stored in the cache
     */
    CV transformToCacheValue(EV externalValue);
}
