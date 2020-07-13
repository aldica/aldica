/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.aldica.common.ignite.GridTestsBase;
import org.alfresco.service.cmr.repository.StoreRef;
import org.apache.ignite.DataRegionMetrics;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataPageEvictionMode;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust
 */
public class StoreRefBinarySerializerTests extends GridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreRefBinarySerializerTests.class);

    private static final int MODE_FULLY_RANDOM = 0;

    private static final int MODE_RANDOM_ID = 1;

    private static final String PROTOCOL_USER = "user";

    private static final String PROTOCOL_SYSTEM = "system";

    private static final String[] PROTOCOLS = { PROTOCOL_USER, PROTOCOL_SYSTEM, StoreRef.PROTOCOL_ARCHIVE, StoreRef.PROTOCOL_WORKSPACE };

    protected static IgniteConfiguration createConfiguration(final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForStoreRef = new BinaryTypeConfiguration();
        binaryTypeConfigurationForStoreRef.setTypeName(StoreRef.class.getName());
        final StoreRefBinarySerializer serializer = new StoreRefBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseOptimisedString(serialForm);
        serializer.setUseVariableLengthPrimitives(serialForm);
        binaryTypeConfigurationForStoreRef.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForStoreRef));
        conf.setBinaryConfiguration(binaryConfiguration);

        final DataStorageConfiguration dataConf = new DataStorageConfiguration();
        final List<DataRegionConfiguration> regionConfs = new ArrayList<>();
        for (final String regionName : regionNames)
        {
            final DataRegionConfiguration regionConf = new DataRegionConfiguration();
            regionConf.setName(regionName);
            // all regions are 10-100 MiB
            regionConf.setInitialSize(10 * 1024 * 1024);
            regionConf.setMaxSize(100 * 1024 * 1024);
            regionConf.setPageEvictionMode(DataPageEvictionMode.RANDOM_2_LRU);
            regionConf.setMetricsEnabled(true);
            regionConfs.add(regionConf);
        }
        dataConf.setDataRegionConfigurations(regionConfs.toArray(new DataRegionConfiguration[0]));
        conf.setDataStorageConfiguration(dataConf);

        return conf;
    }

    @Test
    public void defaultFormCorrectness()
    {
        final IgniteConfiguration conf = createConfiguration(false);
        this.correctnessImpl(conf);
    }

    @Test
    public void defaultFormEfficiency()
    {
        final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(false, "fullyRandom", "randomId");

        referenceConf.setDataStorageConfiguration(conf.getDataStorageConfiguration());

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            final CacheConfiguration<Long, StoreRef> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            cacheConfig.setName("fullyRandom");
            cacheConfig.setDataRegionName("fullyRandom");
            final IgniteCache<Long, StoreRef> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, StoreRef> cache1 = grid.getOrCreateCache(cacheConfig);

            // in this case aldica optimised is actually less efficient than Ignite default due to extra byte for type flag
            // verify it is still within 4% even in this unlikely worst case
            this.efficiencyImpl(referenceGrid, grid, referenceCache1, cache1, "aldica optimised", "Ignite default", MODE_FULLY_RANDOM,
                    -0.04);

            cacheConfig.setName("randomId");
            cacheConfig.setDataRegionName("randomId");
            final IgniteCache<Long, StoreRef> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, StoreRef> cache2 = grid.getOrCreateCache(cacheConfig);

            // fixed, short store protocols are small fraction of overall store reference with UUID as identifier
            // savings are thus limited in our test, but definitely more pronounced in real life
            this.efficiencyImpl(referenceGrid, grid, referenceCache2, cache2, "aldica optimised", "Ignite default", MODE_RANDOM_ID, 0.09);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void rawSerialFormCorrectness()
    {
        final IgniteConfiguration conf = createConfiguration(true);
        this.correctnessImpl(conf);
    }

    @Test
    public void rawSerialFormEfficiency()
    {
        final IgniteConfiguration referenceConf = createConfiguration(false, "fullyRandom", "randomId");
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(true, "fullyRandom", "randomId");

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            final CacheConfiguration<Long, StoreRef> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            cacheConfig.setName("fullyRandom");
            cacheConfig.setDataRegionName("fullyRandom");
            final IgniteCache<Long, StoreRef> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, StoreRef> cache1 = grid.getOrCreateCache(cacheConfig);
            // saving potential is limited - 6%
            this.efficiencyImpl(referenceGrid, grid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", MODE_FULLY_RANDOM,
                    0.06);

            cacheConfig.setName("randomId");
            cacheConfig.setDataRegionName("randomId");
            final IgniteCache<Long, StoreRef> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, StoreRef> cache2 = grid.getOrCreateCache(cacheConfig);

            // saving potential is limited - 4% (fewer fields optimised away than in fully random case)
            this.efficiencyImpl(referenceGrid, grid, referenceCache2, cache2, "aldica raw serial", "aldica optimised", MODE_RANDOM_ID,
                    0.04);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    protected void correctnessImpl(final IgniteConfiguration conf)
    {
        try (Ignite grid = Ignition.start(conf))
        {
            final CacheConfiguration<Long, StoreRef> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("storeRef");
            cacheConfig.setCacheMode(CacheMode.LOCAL);
            final IgniteCache<Long, StoreRef> cache = grid.getOrCreateCache(cacheConfig);

            StoreRef controlValue;
            StoreRef cacheValue;

            controlValue = new StoreRef(StoreRef.PROTOCOL_WORKSPACE, "node1");
            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved
            Assert.assertFalse(controlValue == cacheValue);
            Assert.assertFalse(controlValue.getIdentifier() == cacheValue.getIdentifier());
            // well-known protocol should use same value
            Assert.assertTrue(controlValue.getProtocol() == cacheValue.getProtocol());

            controlValue = new StoreRef("my", "store");
            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved
            Assert.assertFalse(controlValue == cacheValue);
            Assert.assertFalse(controlValue.getIdentifier() == cacheValue.getIdentifier());
            Assert.assertFalse(controlValue.getProtocol() == cacheValue.getProtocol());
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final IgniteCache<Long, StoreRef> referenceCache,
            final IgniteCache<Long, StoreRef> cache, final String serialisationType, final String referenceSerialisationType,
            final int mode, final double marginFraction)
    {
        String modeStr = "unknown";
        switch (mode)
        {
            case MODE_FULLY_RANDOM:
                modeStr = "fully random values";
                break;
            case MODE_RANDOM_ID:
                modeStr = "random store IDs";
                break;
        }

        LOGGER.info(
                "Running StoreRef serialisation benchmark of 100k instances with {}, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                modeStr, referenceSerialisationType, serialisationType, marginFraction);

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            StoreRef value = null;
            switch (mode)
            {
                case MODE_FULLY_RANDOM:
                    value = new StoreRef(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                    break;
                case MODE_RANDOM_ID:
                    value = new StoreRef(PROTOCOLS[rnJesus.nextInt(PROTOCOLS.length)], UUID.randomUUID().toString());
                    break;
            }
            referenceCache.put(Long.valueOf(idx), value);
            cache.put(Long.valueOf(idx), value);
        }

        @SuppressWarnings("unchecked")
        final String regionName = cache.getConfiguration(CacheConfiguration.class).getDataRegionName();
        final DataRegionMetrics referenceMetrics = referenceGrid.dataRegionMetrics(regionName);
        final DataRegionMetrics metrics = grid.dataRegionMetrics(regionName);

        // sufficient to compare used pages - byte-exact memory usage cannot be determined due to potential partial page fill
        final long referenceTotalUsedPages = referenceMetrics.getTotalUsedPages();
        final long totalUsedPages = metrics.getTotalUsedPages();
        final long allowedMax = referenceTotalUsedPages - (long) (marginFraction * referenceTotalUsedPages);
        LOGGER.info("Benchmark resulted in {} vs {} (expected max of {}) total used pages", referenceTotalUsedPages, totalUsedPages,
                allowedMax);
        Assert.assertTrue(totalUsedPages <= allowedMax);
    }
}
