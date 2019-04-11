/*
 * Copyright 2016 - 2019 Acosix GmbH
 */
package de.acosix.alfresco.ignite.repo.cache;

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
