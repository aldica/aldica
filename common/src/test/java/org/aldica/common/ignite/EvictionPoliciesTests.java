/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite;

import org.aldica.common.ignite.cache.MemoryCountingEvictionPolicy;
import org.aldica.common.ignite.cache.MemoryCountingEvictionPolicyFactory;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.eviction.EvictionPolicy;
import org.apache.ignite.cache.eviction.fifo.FifoEvictionPolicy;
import org.apache.ignite.cache.eviction.fifo.FifoEvictionPolicyFactory;
import org.apache.ignite.cache.eviction.lru.LruEvictionPolicy;
import org.apache.ignite.cache.eviction.lru.LruEvictionPolicyFactory;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.processors.cache.GridCacheEvictionManager;
import org.apache.ignite.internal.processors.cache.IgniteCacheProxy;
import org.junit.Assert;
import org.junit.Test;

/**
 * The tests in this class mostly exist to validate the known / expected behaviour of the default Ignite eviction policies. Only
 * {@link #onlyMemoryCountingEvictionPolicy() the test for the no-op memory counting eviction policy} affects an implementation of this
 * project (which would typically not be used in a regular deployment anyway, as an on-heap cache without limit-based eviction is
 * dangerous).
 *
 * @author Axel Faust
 */
public class EvictionPoliciesTests extends GridTestsBase
{

    @Test
    public void onlyMemoryCountingEvictionPolicy()
    {
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<Long, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final MemoryCountingEvictionPolicyFactory<Long, String> evictionPolicyFactory = new MemoryCountingEvictionPolicyFactory<>();
            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCacheProxy<Long, String> cache = (IgniteCacheProxy<Long, String>) grid.getOrCreateCache(cacheConfig);
            final GridCacheEvictionManager cacheEvictionManager = (GridCacheEvictionManager) cache.context().evicts();
            Assert.assertNotNull(cacheEvictionManager);
            final EvictionPolicy<?, ?> evictionPolicy = cacheEvictionManager.getEvictionPolicy();

            Assert.assertTrue(evictionPolicy instanceof MemoryCountingEvictionPolicy<?, ?>);

            final MemoryCountingEvictionPolicy<?, ?> memoryCountingPolicy = (MemoryCountingEvictionPolicy<?, ?>) evictionPolicy;

            Assert.assertEquals(0, memoryCountingPolicy.getCurrentMemorySize());

            cache.put(Long.valueOf(0), "Test0");

            final long singleElementSize = memoryCountingPolicy.getCurrentMemorySize();
            Assert.assertEquals(1, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertNotEquals(0, singleElementSize);

            cache.put(Long.valueOf(1), "Test1");
            cache.put(Long.valueOf(2), "Test2");
            cache.put(Long.valueOf(3), "Test3");
            cache.put(Long.valueOf(4), "Test4");
            cache.put(Long.valueOf(5), "Test5");
            cache.put(Long.valueOf(6), "Test6");
            cache.put(Long.valueOf(7), "Test7");
            cache.put(Long.valueOf(8), "Test8");
            cache.put(Long.valueOf(9), "Test9");

            Assert.assertEquals(10, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(10 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void lruMemoryCountingEvictionPolicyWithEntryCountBasedBatchEviction()
    {
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<Long, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final LruEvictionPolicyFactory<Long, String> evictionPolicyFactory = new LruEvictionPolicyFactory<>();
            evictionPolicyFactory.setMaxSize(90);
            evictionPolicyFactory.setBatchSize(10);

            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCacheProxy<Long, String> cache = (IgniteCacheProxy<Long, String>) grid.getOrCreateCache(cacheConfig);
            final GridCacheEvictionManager cacheEvictionManager = (GridCacheEvictionManager) cache.context().evicts();
            Assert.assertNotNull(cacheEvictionManager);
            final EvictionPolicy<?, ?> evictionPolicy = cacheEvictionManager.getEvictionPolicy();

            Assert.assertTrue(evictionPolicy instanceof LruEvictionPolicy<?, ?>);

            final LruEvictionPolicy<?, ?> memoryCountingPolicy = (LruEvictionPolicy<?, ?>) evictionPolicy;

            Assert.assertEquals(0, memoryCountingPolicy.getCurrentMemorySize());

            cache.put(Long.valueOf(0), "Test00");

            final long singleElementSize = memoryCountingPolicy.getCurrentMemorySize();

            Assert.assertEquals(1, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertNotEquals(0, singleElementSize);

            cache.put(Long.valueOf(1), "Test01");
            cache.put(Long.valueOf(2), "Test02");
            cache.put(Long.valueOf(3), "Test03");
            cache.put(Long.valueOf(4), "Test04");
            cache.put(Long.valueOf(5), "Test05");
            cache.put(Long.valueOf(6), "Test06");
            cache.put(Long.valueOf(7), "Test07");
            cache.put(Long.valueOf(8), "Test08");
            cache.put(Long.valueOf(9), "Test09");

            Assert.assertEquals(10, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(10 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // add 90 more entries which should trigger eviction
            for (int idx = 0; idx < 89; idx++)
            {
                final int no = 10 + idx;
                cache.put(Long.valueOf(no), "Test" + no);
            }

            // we added 10 + 89 elements
            // Ignite only effectively starts eviction if count exceeds/equals the sum of max and batchSize, so we should have 99 elements
            Assert.assertEquals(99, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(99 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // access entries 0-9
            for (int idx = 0; idx < 10; idx++)
            {
                Assert.assertEquals("Test0" + idx, cache.get(Long.valueOf(idx)));
            }

            cache.put(Long.valueOf(99), "Test99");

            // eviction should have been triggered
            Assert.assertEquals(90, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(90 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // 10 elements starting from 10 should be removed by now, since entries 0-9 were recently used
            for (int idx = 10; idx < 20; idx++)
            {
                Assert.assertNotEquals("Test" + idx, cache.localPeek(Long.valueOf(idx), CachePeekMode.ONHEAP));
            }
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void lruMemoryCountingEvictionPolicyWithEntryCountBasedNonBatchEviction()
    {
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<Long, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final LruEvictionPolicyFactory<Long, String> evictionPolicyFactory = new LruEvictionPolicyFactory<>();
            evictionPolicyFactory.setMaxSize(90);

            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCacheProxy<Long, String> cache = (IgniteCacheProxy<Long, String>) grid.getOrCreateCache(cacheConfig);
            final GridCacheEvictionManager cacheEvictionManager = (GridCacheEvictionManager) cache.context().evicts();
            Assert.assertNotNull(cacheEvictionManager);
            final EvictionPolicy<?, ?> evictionPolicy = cacheEvictionManager.getEvictionPolicy();

            Assert.assertTrue(evictionPolicy instanceof LruEvictionPolicy<?, ?>);

            final LruEvictionPolicy<?, ?> memoryCountingPolicy = (LruEvictionPolicy<?, ?>) evictionPolicy;

            Assert.assertEquals(0, memoryCountingPolicy.getCurrentMemorySize());

            cache.put(Long.valueOf(0), "Test00");

            final long singleElementSize = memoryCountingPolicy.getCurrentMemorySize();

            Assert.assertEquals(1, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertNotEquals(0, singleElementSize);

            cache.put(Long.valueOf(1), "Test01");
            cache.put(Long.valueOf(2), "Test02");
            cache.put(Long.valueOf(3), "Test03");
            cache.put(Long.valueOf(4), "Test04");
            cache.put(Long.valueOf(5), "Test05");
            cache.put(Long.valueOf(6), "Test06");
            cache.put(Long.valueOf(7), "Test07");
            cache.put(Long.valueOf(8), "Test08");
            cache.put(Long.valueOf(9), "Test09");

            Assert.assertEquals(10, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(10 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // add 90 more entries which should trigger eviction
            for (int idx = 0; idx < 80; idx++)
            {
                final int no = 10 + idx;
                cache.put(Long.valueOf(no), "Test" + no);
            }

            // we added 10 + 80 elements
            // Ignite only effectively starts eviction if count exceeds the max by 1, so we should have 90 elements now
            Assert.assertEquals(90, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(90 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // access entries 0-9
            for (int idx = 0; idx < 10; idx++)
            {
                Assert.assertEquals("Test0" + idx, cache.get(Long.valueOf(idx)));
            }

            cache.put(Long.valueOf(90), "Test90");

            // eviction should have been triggered, so we should still have 90 elements
            Assert.assertEquals(90, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(90 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // 10th element should be removed by now, since entries 0-9 were recently used
            Assert.assertNotEquals("Test10", cache.localPeek(Long.valueOf(10), CachePeekMode.ONHEAP));
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void lruMemoryCountingEvictionPolicyWithMemoryBasedNonBatchEviction()
    {
        // we use a <Long, Long> cache as Long values are all identical in size
        // so we don't have to do the same "value space shaping" we did with the simpler entry count tests to ensure the size of one cache
        // entry also applies to all cache entries
        final int memoryLimit = 10000;

        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<Long, Long> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final LruEvictionPolicyFactory<Long, Long> evictionPolicyFactory = new LruEvictionPolicyFactory<>();
            evictionPolicyFactory.setMaxMemorySize(memoryLimit);

            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCacheProxy<Long, Long> cache = (IgniteCacheProxy<Long, Long>) grid.getOrCreateCache(cacheConfig);
            final GridCacheEvictionManager cacheEvictionManager = (GridCacheEvictionManager) cache.context().evicts();
            Assert.assertNotNull(cacheEvictionManager);
            final EvictionPolicy<?, ?> evictionPolicy = cacheEvictionManager.getEvictionPolicy();

            Assert.assertTrue(evictionPolicy instanceof LruEvictionPolicy<?, ?>);

            final LruEvictionPolicy<?, ?> memoryCountingPolicy = (LruEvictionPolicy<?, ?>) evictionPolicy;

            Assert.assertEquals(0, memoryCountingPolicy.getCurrentMemorySize());

            cache.put(Long.valueOf(0), Long.valueOf(0));

            final long singleElementSize = memoryCountingPolicy.getCurrentMemorySize();

            Assert.assertEquals(1, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertNotEquals(0, singleElementSize);

            cache.put(Long.valueOf(1), Long.valueOf(1));
            cache.put(Long.valueOf(2), Long.valueOf(2));
            cache.put(Long.valueOf(3), Long.valueOf(3));
            cache.put(Long.valueOf(4), Long.valueOf(4));
            cache.put(Long.valueOf(5), Long.valueOf(5));
            cache.put(Long.valueOf(6), Long.valueOf(6));
            cache.put(Long.valueOf(7), Long.valueOf(7));
            cache.put(Long.valueOf(8), Long.valueOf(8));
            cache.put(Long.valueOf(9), Long.valueOf(9));

            Assert.assertEquals(10, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(10 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            final int maxSafeEntryCount = (int) (memoryLimit / singleElementSize);
            for (int idx = 10; idx < maxSafeEntryCount; idx++)
            {
                cache.put(Long.valueOf(idx), Long.valueOf(idx));
            }

            Assert.assertEquals(maxSafeEntryCount, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(maxSafeEntryCount * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // access entries 0-9
            for (int idx = 0; idx < 10; idx++)
            {
                Assert.assertEquals(Long.valueOf(idx), cache.get(Long.valueOf(idx)));
            }

            cache.put(Long.valueOf(maxSafeEntryCount), Long.valueOf(maxSafeEntryCount));

            // eviction should have been triggered, so we should still be at the max safe amount
            Assert.assertEquals(maxSafeEntryCount, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(maxSafeEntryCount * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // 10th element should be removed by now, since entries 0-9 were recently used
            Assert.assertNotEquals(Long.valueOf(10), cache.localPeek(Long.valueOf(10), CachePeekMode.ONHEAP));
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void fifoMemoryCountingEvictionPolicyWithEntryCountBasedBatchEviction()
    {
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<Long, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final FifoEvictionPolicyFactory<Long, String> evictionPolicyFactory = new FifoEvictionPolicyFactory<>();
            evictionPolicyFactory.setMaxSize(90);
            evictionPolicyFactory.setBatchSize(10);

            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCacheProxy<Long, String> cache = (IgniteCacheProxy<Long, String>) grid.getOrCreateCache(cacheConfig);
            final GridCacheEvictionManager cacheEvictionManager = (GridCacheEvictionManager) cache.context().evicts();
            Assert.assertNotNull(cacheEvictionManager);
            final EvictionPolicy<?, ?> evictionPolicy = cacheEvictionManager.getEvictionPolicy();

            Assert.assertTrue(evictionPolicy instanceof FifoEvictionPolicy<?, ?>);

            final FifoEvictionPolicy<?, ?> memoryCountingPolicy = (FifoEvictionPolicy<?, ?>) evictionPolicy;

            Assert.assertEquals(0, memoryCountingPolicy.getCurrentMemorySize());

            cache.put(Long.valueOf(0), "Test00");

            final long singleElementSize = memoryCountingPolicy.getCurrentMemorySize();

            Assert.assertEquals(1, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertNotEquals(0, singleElementSize);

            cache.put(Long.valueOf(1), "Test01");
            cache.put(Long.valueOf(2), "Test02");
            cache.put(Long.valueOf(3), "Test03");
            cache.put(Long.valueOf(4), "Test04");
            cache.put(Long.valueOf(5), "Test05");
            cache.put(Long.valueOf(6), "Test06");
            cache.put(Long.valueOf(7), "Test07");
            cache.put(Long.valueOf(8), "Test08");
            cache.put(Long.valueOf(9), "Test09");

            Assert.assertEquals(10, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(10 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // add 90 more entries which should trigger eviction
            for (int idx = 0; idx < 89; idx++)
            {
                final int no = 10 + idx;
                cache.put(Long.valueOf(no), "Test" + no);
            }

            // we added 10 + 89 elements
            // Ignite only effectively starts eviction if count exceeds/equals the sum of max and batchSize, so we should have 99 elements
            Assert.assertEquals(99, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(99 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // access entries 0-9
            for (int idx = 0; idx < 10; idx++)
            {
                Assert.assertEquals("Test0" + idx, cache.get(Long.valueOf(idx)));
            }

            cache.put(Long.valueOf(99), "Test99");

            // eviction should have been triggered
            Assert.assertEquals(90, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(90 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // first 10 elements should be removed by now, regardless of last use
            for (int idx = 0; idx < 10; idx++)
            {
                Assert.assertNotEquals("Test0" + idx, cache.localPeek(Long.valueOf(idx), CachePeekMode.ONHEAP));
            }
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void fifoMemoryCountingEvictionPolicyWithEntryCountBasedNonBatchEviction()
    {
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<Long, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final FifoEvictionPolicyFactory<Long, String> evictionPolicyFactory = new FifoEvictionPolicyFactory<>();
            evictionPolicyFactory.setMaxSize(90);

            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCacheProxy<Long, String> cache = (IgniteCacheProxy<Long, String>) grid.getOrCreateCache(cacheConfig);
            final GridCacheEvictionManager cacheEvictionManager = (GridCacheEvictionManager) cache.context().evicts();
            Assert.assertNotNull(cacheEvictionManager);
            final EvictionPolicy<?, ?> evictionPolicy = cacheEvictionManager.getEvictionPolicy();

            Assert.assertTrue(evictionPolicy instanceof FifoEvictionPolicy<?, ?>);

            final FifoEvictionPolicy<?, ?> memoryCountingPolicy = (FifoEvictionPolicy<?, ?>) evictionPolicy;

            Assert.assertEquals(0, memoryCountingPolicy.getCurrentMemorySize());

            cache.put(Long.valueOf(0), "Test00");

            final long singleElementSize = memoryCountingPolicy.getCurrentMemorySize();

            Assert.assertEquals(1, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertNotEquals(0, singleElementSize);

            cache.put(Long.valueOf(1), "Test01");
            cache.put(Long.valueOf(2), "Test02");
            cache.put(Long.valueOf(3), "Test03");
            cache.put(Long.valueOf(4), "Test04");
            cache.put(Long.valueOf(5), "Test05");
            cache.put(Long.valueOf(6), "Test06");
            cache.put(Long.valueOf(7), "Test07");
            cache.put(Long.valueOf(8), "Test08");
            cache.put(Long.valueOf(9), "Test09");

            Assert.assertEquals(10, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(10 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // add 90 more entries which should trigger eviction
            for (int idx = 0; idx < 80; idx++)
            {
                final int no = 10 + idx;
                cache.put(Long.valueOf(no), "Test" + no);
            }

            // we added 10 + 80 elements
            // Ignite only effectively starts eviction if count exceeds the max by 1, so we should have 90 elements now
            Assert.assertEquals(90, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(90 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // access entries 0-9
            for (int idx = 0; idx < 10; idx++)
            {
                Assert.assertEquals("Test0" + idx, cache.get(Long.valueOf(idx)));
            }

            cache.put(Long.valueOf(90), "Test90");

            // eviction should have been triggered, so we should still have 90 elements
            Assert.assertEquals(90, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(90 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // first element should be removed by now, regardless of last used entries
            Assert.assertNotEquals("Test00", cache.localPeek(Long.valueOf(0), CachePeekMode.ONHEAP));
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void fifoMemoryCountingEvictionPolicyWithMemoryBasedNonBatchEviction()
    {
        // we use a <Long, Long> cache as Long values are all identical in size
        // so we don't have to do the same "value space shaping" we did with the simpler entry count tests to ensure the size of one cache
        // entry also applies to all cache entries
        final int memoryLimit = 10000;

        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<Long, Long> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final FifoEvictionPolicyFactory<Long, Long> evictionPolicyFactory = new FifoEvictionPolicyFactory<>();
            evictionPolicyFactory.setMaxMemorySize(memoryLimit);

            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCacheProxy<Long, Long> cache = (IgniteCacheProxy<Long, Long>) grid.getOrCreateCache(cacheConfig);
            final GridCacheEvictionManager cacheEvictionManager = (GridCacheEvictionManager) cache.context().evicts();
            Assert.assertNotNull(cacheEvictionManager);
            final EvictionPolicy<?, ?> evictionPolicy = cacheEvictionManager.getEvictionPolicy();

            Assert.assertTrue(evictionPolicy instanceof FifoEvictionPolicy<?, ?>);

            final FifoEvictionPolicy<?, ?> memoryCountingPolicy = (FifoEvictionPolicy<?, ?>) evictionPolicy;

            Assert.assertEquals(0, memoryCountingPolicy.getCurrentMemorySize());

            cache.put(Long.valueOf(0), Long.valueOf(0));

            final long singleElementSize = memoryCountingPolicy.getCurrentMemorySize();

            Assert.assertEquals(1, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertNotEquals(0, singleElementSize);

            cache.put(Long.valueOf(1), Long.valueOf(1));
            cache.put(Long.valueOf(2), Long.valueOf(2));
            cache.put(Long.valueOf(3), Long.valueOf(3));
            cache.put(Long.valueOf(4), Long.valueOf(4));
            cache.put(Long.valueOf(5), Long.valueOf(5));
            cache.put(Long.valueOf(6), Long.valueOf(6));
            cache.put(Long.valueOf(7), Long.valueOf(7));
            cache.put(Long.valueOf(8), Long.valueOf(8));
            cache.put(Long.valueOf(9), Long.valueOf(9));

            Assert.assertEquals(10, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(10 * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            final int maxSafeEntryCount = (int) (memoryLimit / singleElementSize);
            for (int idx = 10; idx < maxSafeEntryCount; idx++)
            {
                cache.put(Long.valueOf(idx), Long.valueOf(idx));
            }

            Assert.assertEquals(maxSafeEntryCount, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(maxSafeEntryCount * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // access entries 0-9
            for (int idx = 0; idx < 10; idx++)
            {
                Assert.assertEquals(Long.valueOf(idx), cache.get(Long.valueOf(idx)));
            }

            cache.put(Long.valueOf(maxSafeEntryCount), Long.valueOf(maxSafeEntryCount));

            // eviction should have been triggered, so we should still be at the max safe amount
            Assert.assertEquals(maxSafeEntryCount, cache.sizeLong(CachePeekMode.ONHEAP));
            Assert.assertEquals(maxSafeEntryCount * singleElementSize, memoryCountingPolicy.getCurrentMemorySize());

            // first element should be removed by now, regardless of last used entries
            Assert.assertNotEquals(Long.valueOf(0), cache.localPeek(Long.valueOf(0), CachePeekMode.ONHEAP));
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }
}
