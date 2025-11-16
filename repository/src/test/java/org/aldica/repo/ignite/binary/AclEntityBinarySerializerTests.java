/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.alfresco.repo.domain.permissions.AclEntity;
import org.alfresco.repo.security.permissions.ACLType;
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
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author Axel Faust
 */
public class AclEntityBinarySerializerTests extends AclGridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AclEntityBinarySerializerTests.class);

    protected static IgniteConfiguration createConfiguration(final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForAclEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForAclEntity.setTypeName(AclEntity.class.getName());
        final AclEntityBinarySerializer aclEntitySerializer = new AclEntityBinarySerializer();
        aclEntitySerializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForAclEntity.setSerializer(aclEntitySerializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForAclEntity));
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
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration defaultConf = createConfiguration(false, "comparison1");
            referenceConf.setDataStorageConfiguration(defaultConf.getDataStorageConfiguration());

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);

                final CacheConfiguration<Long, AclEntity> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.REPLICATED);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, AclEntity> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, AclEntity> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // minor optimisation by merging boolean flags into a byte - 4.5%
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica optimised", "Ignite default", 0.045);
            }
            finally
            {
                Ignition.stopAll(true);
            }
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

            final CacheConfiguration<Long, AclEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("comparison1");
            cacheConfig.setDataRegionName("comparison1");
            final IgniteCache<Long, AclEntity> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, AclEntity> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

            // reduction in overhead and better numerics - 14%
            this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", 0.14);
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
            final CacheConfiguration<Long, AclEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("aclEntity");
            cacheConfig.setCacheMode(CacheMode.REPLICATED);
            final IgniteCache<Long, AclEntity> cache = grid.getOrCreateCache(cacheConfig);

            AclEntity controlValue;
            AclEntity cacheValue;

            controlValue = new AclEntity();

            controlValue.setId(987654l);
            controlValue.setVersion(12l);
            controlValue.setAclId(UUID.randomUUID().toString());
            controlValue.setLatest(true);
            controlValue.setAclVersion(-123456789l);
            controlValue.setInherits(true);
            controlValue.setInheritsFrom(1642496l);
            controlValue.setAclType(ACLType.DEFINING);
            controlValue.setInheritedAcl(-64l);
            controlValue.setVersioned(true);
            controlValue.setRequiresVersion(true);
            controlValue.setAclChangeSetId(987654321l);

            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            // only shallow equals() in AclEntity
            // we do a deep check ourselves
            Assert.assertNotSame(controlValue, cacheValue);
            assertAclEntity(controlValue, cacheValue);

            controlValue = new AclEntity();

            controlValue.setId(Long.MIN_VALUE);
            controlValue.setVersion(-1l);
            controlValue.setAclId("asdfghjklöqwertzuiopüxcvbnm");
            controlValue.setLatest(false);
            controlValue.setAclVersion(Long.MAX_VALUE);
            controlValue.setInherits(false);
            controlValue.setInheritsFrom(null);
            controlValue.setAclType(ACLType.OLD);
            controlValue.setInheritedAcl(null);
            controlValue.setVersioned(false);
            controlValue.setRequiresVersion(false);
            controlValue.setAclChangeSetId(-987654321l);

            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            // only shallow equals() in AclEntity
            // we do a deep check ourselves
            Assert.assertNotSame(controlValue, cacheValue);
            assertAclEntity(controlValue, cacheValue);
        }
    }

    @SuppressWarnings("deprecation")
    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final IgniteCache<Long, AclEntity> referenceCache,
            final IgniteCache<Long, AclEntity> cache, final String serialisationType, final String referenceSerialisationType,
            final double marginFraction)
    {
        LOGGER.info(
                "Running AclEntity serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            final AclEntity value = randomAclEntity(rnJesus);

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
