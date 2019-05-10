/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

/**
 * Instances of this interface provide data for a particular cache instance regarding absolute and relative access metrics, hits vs misses,
 * as well as evictions.
 *
 * @author Axel Faust
 */
public interface CacheMetrics
{

    /**
     * Retrieves the amount of read accesses to this cache.
     *
     * @return the number of read accesses
     */
    long getCacheGets();

    /**
     * Retrieves the number of read accesses that returned an existing cache entry.
     *
     * @return the number of cache hits
     */
    long getCacheHits();

    /**
     * Retrieves the relative percentage of read accesses that returned an existing cache entry.
     *
     * @return the cache hit percentage
     */
    double getCacheHitPercentage();

    /**
     * Retrieves the number of read accesses for non-existing cache entries.
     *
     * @return the number of cache misses
     */
    long getCacheMisses();

    /**
     * Retrieves the relative percentage of read accesses for non-existing cache entries.
     *
     * @return the cache miss percentage
     */
    double getCacheMissPercentage();

    /**
     * Retrieves the number of cache entries that have been evicted from the cache, e.g. as part of time-to-live schemes.
     *
     * @return the number of entries evicted from the cache
     */
    long getCacheEvictions();

    // TODO Add additional stats (e.g. puts + timing) when supported enough by Alfresco or 3rd party caches for inclusion in view
}
