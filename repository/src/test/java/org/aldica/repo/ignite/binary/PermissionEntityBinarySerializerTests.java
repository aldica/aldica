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
import org.alfresco.repo.domain.permissions.PermissionEntity;
import org.alfresco.service.cmr.security.PermissionService;
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
public class PermissionEntityBinarySerializerTests extends GridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionEntityBinarySerializerTests.class);

    protected static IgniteConfiguration createConfiguration(final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForPermissionEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForPermissionEntity.setTypeName(PermissionEntity.class.getName());
        final PermissionEntityBinarySerializer permissionEntitySerializer = new PermissionEntityBinarySerializer();
        permissionEntitySerializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForPermissionEntity.setSerializer(permissionEntitySerializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForPermissionEntity));
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

        final IgniteConfiguration defaultConf = createConfiguration(false, "comparison1");
        referenceConf.setDataStorageConfiguration(defaultConf.getDataStorageConfiguration());

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite defaultGrid = Ignition.start(defaultConf);

            final CacheConfiguration<Long, PermissionEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("comparison1");
            cacheConfig.setDataRegionName("comparison1");
            final IgniteCache<Long, PermissionEntity> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, PermissionEntity> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

            // virtually no difference
            this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica optimised", "Ignite default", 0.00);
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
        final IgniteConfiguration referenceConf = createConfiguration(false, "comparison1");
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

        final IgniteConfiguration defaultConf = createConfiguration(true, "comparison1");
        referenceConf.setDataStorageConfiguration(defaultConf.getDataStorageConfiguration());

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite defaultGrid = Ignition.start(defaultConf);

            final CacheConfiguration<Long, PermissionEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("comparison1");
            cacheConfig.setDataRegionName("comparison1");
            final IgniteCache<Long, PermissionEntity> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, PermissionEntity> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

            // small overhead reduction and better numerics - 7% 
            this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", 0.07);
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
            final CacheConfiguration<Long, PermissionEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("permissionEntity");
            cacheConfig.setCacheMode(CacheMode.REPLICATED);
            final IgniteCache<Long, PermissionEntity> cache = grid.getOrCreateCache(cacheConfig);

            PermissionEntity controlValue;
            PermissionEntity cacheValue;

            controlValue = new PermissionEntity();

            controlValue.setId(987654l);
            controlValue.setVersion(12l);
            controlValue.setTypeQNameId(Long.MAX_VALUE);
            controlValue.setName(PermissionService.COORDINATOR);

            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            // only shallow equals() in PermissionEntity
            // we do a deep check ourselves
            Assert.assertNotSame(controlValue, cacheValue);
            Assert.assertEquals(controlValue.getId(), cacheValue.getId());
            Assert.assertEquals(controlValue.getVersion(), cacheValue.getVersion());
            Assert.assertEquals(controlValue.getTypeQNameId(), cacheValue.getTypeQNameId());
            Assert.assertEquals(controlValue.getName(), cacheValue.getName());

            controlValue = new PermissionEntity();

            controlValue.setId(Long.MAX_VALUE);
            controlValue.setVersion(-12l);
            controlValue.setTypeQNameId(Long.MIN_VALUE);
            controlValue.setName(UUID.randomUUID().toString());

            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            // only shallow equals() in PermissionEntity
            // we do a deep check ourselves
            Assert.assertNotSame(controlValue, cacheValue);
            Assert.assertEquals(controlValue.getId(), cacheValue.getId());
            Assert.assertEquals(controlValue.getVersion(), cacheValue.getVersion());
            Assert.assertEquals(controlValue.getTypeQNameId(), cacheValue.getTypeQNameId());
            Assert.assertEquals(controlValue.getName(), cacheValue.getName());
        }
    }

    @SuppressWarnings("deprecation")
    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final IgniteCache<Long, PermissionEntity> referenceCache,
            final IgniteCache<Long, PermissionEntity> cache, final String serialisationType, final String referenceSerialisationType,
            final double marginFraction)
    {
        LOGGER.info(
                "Running PermissionEntity serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            final PermissionEntity value = new PermissionEntity();

            value.setId(rnJesus.nextLong());
            value.setVersion(Long.valueOf(rnJesus.nextInt(100)));
            value.setTypeQNameId(rnJesus.nextLong());
            value.setName(UUID.randomUUID().toString());

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
