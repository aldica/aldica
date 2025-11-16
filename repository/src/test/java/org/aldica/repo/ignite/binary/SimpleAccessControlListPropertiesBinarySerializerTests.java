/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.alfresco.repo.security.permissions.ACLType;
import org.alfresco.repo.security.permissions.SimpleAccessControlListProperties;
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
public class SimpleAccessControlListPropertiesBinarySerializerTests extends AclGridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleAccessControlListPropertiesBinarySerializerTests.class);

    protected static IgniteConfiguration createConfiguration(final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForAclProperties = new BinaryTypeConfiguration();
        binaryTypeConfigurationForAclProperties.setTypeName(SimpleAccessControlListProperties.class.getName());
        final SimpleAccessControlListPropertiesBinarySerializer aclPropertiesSerializer = new SimpleAccessControlListPropertiesBinarySerializer();
        aclPropertiesSerializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForAclProperties.setSerializer(aclPropertiesSerializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForAclProperties));
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

            final CacheConfiguration<Long, SimpleAccessControlListProperties> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("comparison1");
            cacheConfig.setDataRegionName("comparison1");
            final IgniteCache<Long, SimpleAccessControlListProperties> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, SimpleAccessControlListProperties> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

            // minor optimisation by merging boolean flags into a byte - 6%
            this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica optimised", "Ignite default", 0.06);
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

            final CacheConfiguration<Long, SimpleAccessControlListProperties> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("comparison1");
            cacheConfig.setDataRegionName("comparison1");
            final IgniteCache<Long, SimpleAccessControlListProperties> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, SimpleAccessControlListProperties> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

            // reduction in overhead and better numerics - 9%
            this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", 0.09);
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
            final CacheConfiguration<Long, SimpleAccessControlListProperties> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("simpleAccessControlListProperties");
            cacheConfig.setCacheMode(CacheMode.REPLICATED);
            final IgniteCache<Long, SimpleAccessControlListProperties> cache = grid.getOrCreateCache(cacheConfig);

            SimpleAccessControlListProperties controlValue;
            SimpleAccessControlListProperties cacheValue;

            controlValue = new SimpleAccessControlListProperties();

            controlValue.setId(987654l);
            controlValue.setAclId(UUID.randomUUID().toString());
            controlValue.setLatest(true);
            controlValue.setAclVersion(-123456789l);
            controlValue.setInherits(true);
            controlValue.setAclType(ACLType.DEFINING);
            controlValue.setVersioned(true);
            controlValue.setAclChangeSetId(987654321l);

            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            // no equals() in SimpleAccessControlListProperties
            // we do a deep check ourselves
            Assert.assertNotSame(controlValue, cacheValue);
            assertAclProperties(controlValue, cacheValue);

            controlValue = new SimpleAccessControlListProperties();

            controlValue.setId(Long.MIN_VALUE);
            controlValue.setAclId("asdfghjklöqwertzuiopüxcvbnm");
            controlValue.setLatest(false);
            controlValue.setAclVersion(Long.MAX_VALUE);
            controlValue.setInherits(false);
            controlValue.setAclType(ACLType.OLD);
            controlValue.setVersioned(false);
            controlValue.setAclChangeSetId(-987654321l);

            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            // no equals() in SimpleAccessControlListProperties
            // we do a deep check ourselves
            Assert.assertNotSame(controlValue, cacheValue);
            assertAclProperties(controlValue, cacheValue);
        }
    }

    @SuppressWarnings("deprecation")
    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid,
            final IgniteCache<Long, SimpleAccessControlListProperties> referenceCache,
            final IgniteCache<Long, SimpleAccessControlListProperties> cache, final String serialisationType,
            final String referenceSerialisationType, final double marginFraction)
    {
        LOGGER.info(
                "Running SimpleAccessControlListProperties serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            final SimpleAccessControlListProperties value = randomAclProperties(rnJesus);

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
