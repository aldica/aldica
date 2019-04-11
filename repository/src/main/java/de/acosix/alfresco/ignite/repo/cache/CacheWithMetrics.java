/*
 * Copyright 2016 - 2019 Acosix GmbH
 */
package de.acosix.alfresco.ignite.repo.cache;

/**
 * Instances of this interface are caches that are capable of providing additional metrics data about their usage.
 *
 * @author Axel Faust
 */
public interface CacheWithMetrics
{

    /**
     * Retrieves a (snapshot) object containing metrics for this caches usage since its initialisation.
     *
     * @return this cache's metrics
     */
    CacheMetrics getMetrics();

    /**
     * Retrieves the number of items currently in the cache overall.
     *
     * @return the number of items
     */
    int size();

    /**
     * Retrieves the number of items currently in the cache, limited to items available to the current JVM.
     *
     * @return the number of items
     */
    int localSize();

}
