/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import org.apache.ignite.IgniteCache;

/**
 * @author Axel Faust
 */
public class IgniteBackedCacheMetrics implements CacheMetrics
{

    protected final IgniteCache<?, ?> backingCache;

    protected final org.apache.ignite.cache.CacheMetrics metrics;

    public IgniteBackedCacheMetrics(final IgniteCache<?, ?> backingCache)
    {
        this.backingCache = backingCache;
        this.metrics = this.backingCache.localMetrics();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCacheGets()
    {
        final long cacheGets = this.metrics.getCacheGets();
        return cacheGets;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCacheHits()
    {
        final long cacheHits = this.metrics.getCacheHits();
        return cacheHits;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getCacheHitPercentage()
    {
        double cacheHitPercentage;
        if (this.getCacheGets() == 0)
        {
            cacheHitPercentage = 100;
        }
        else
        {
            cacheHitPercentage = this.metrics.getCacheHitPercentage();
        }
        return cacheHitPercentage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCacheMisses()
    {
        final long cacheMisses = this.metrics.getCacheMisses();
        return cacheMisses;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getCacheMissPercentage()
    {
        final double cacheMissPercentage;
        if (this.getCacheGets() == 0)
        {
            cacheMissPercentage = 0;
        }
        else
        {
            cacheMissPercentage = this.metrics.getCacheMissPercentage();
        }
        return cacheMissPercentage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCacheEvictions()
    {
        final long cacheEvictions = this.metrics.getCacheEvictions();
        return cacheEvictions;
    }

}
