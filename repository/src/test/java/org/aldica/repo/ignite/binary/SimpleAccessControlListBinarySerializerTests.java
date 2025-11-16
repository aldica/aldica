/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.alfresco.repo.domain.permissions.AclEntity;
import org.alfresco.repo.security.permissions.AccessControlEntry;
import org.alfresco.repo.security.permissions.AccessControlListProperties;
import org.alfresco.repo.security.permissions.SimpleAccessControlEntry;
import org.alfresco.repo.security.permissions.SimpleAccessControlList;
import org.alfresco.repo.security.permissions.SimpleAccessControlListProperties;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author Axel Faust
 */
public class SimpleAccessControlListBinarySerializerTests extends AclGridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleAccessControlListBinarySerializerTests.class);

    protected static IgniteConfiguration createConfiguration(final ApplicationContext applicationContext, final boolean idsWhenReasonable,
            final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForAcl = new BinaryTypeConfiguration();
        binaryTypeConfigurationForAcl.setTypeName(SimpleAccessControlList.class.getName());
        final SimpleAccessControlListBinarySerializer aclSerializer = new SimpleAccessControlListBinarySerializer();
        aclSerializer.setApplicationContext(applicationContext);
        aclSerializer.setUseRawSerialForm(serialForm);
        aclSerializer.setUseIdsWhenReasonable(idsWhenReasonable);
        binaryTypeConfigurationForAcl.setSerializer(aclSerializer);

        final BinaryTypeConfiguration binaryTypeConfigurationForAclProperties = new BinaryTypeConfiguration();
        binaryTypeConfigurationForAclProperties.setTypeName(SimpleAccessControlListProperties.class.getName());
        final SimpleAccessControlListPropertiesBinarySerializer aclPropertiesSerializer = new SimpleAccessControlListPropertiesBinarySerializer();
        aclPropertiesSerializer.setApplicationContext(applicationContext);
        aclPropertiesSerializer.setUseRawSerialForm(serialForm);
        aclPropertiesSerializer.setUseIdsWhenReasonable(idsWhenReasonable);
        binaryTypeConfigurationForAclProperties.setSerializer(aclPropertiesSerializer);

        final BinaryTypeConfiguration binaryTypeConfigurationForAclEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForAclEntity.setTypeName(AclEntity.class.getName());
        final AclEntityBinarySerializer aclEntitySerializer = new AclEntityBinarySerializer();
        aclEntitySerializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForAclEntity.setSerializer(aclEntitySerializer);

        final BinaryTypeConfiguration binaryTypeConfigurationForAce = new BinaryTypeConfiguration();
        binaryTypeConfigurationForAce.setTypeName(SimpleAccessControlEntry.class.getName());
        final SimpleAccessControlEntryBinarySerializer aceSerializer = new SimpleAccessControlEntryBinarySerializer();
        aceSerializer.setApplicationContext(applicationContext);
        aceSerializer.setUseRawSerialForm(serialForm);
        aceSerializer.setUseIdsWhenReasonable(idsWhenReasonable);
        binaryTypeConfigurationForAce.setSerializer(aceSerializer);

        final BinaryTypeConfiguration binaryTypeConfigurationForSimplePermissionReference = new BinaryTypeConfiguration();
        binaryTypeConfigurationForSimplePermissionReference.setTypeName(StoreRef.class.getName());
        final SimplePermissionReferenceBinarySerializer permissionSerializer = new SimplePermissionReferenceBinarySerializer();
        permissionSerializer.setApplicationContext(applicationContext);
        permissionSerializer.setUseRawSerialForm(serialForm);
        permissionSerializer.setUseIdsWhenReasonable(idsWhenReasonable);
        binaryTypeConfigurationForSimplePermissionReference.setSerializer(permissionSerializer);

        final BinaryTypeConfiguration binaryTypeConfigurationForQName = new BinaryTypeConfiguration();
        binaryTypeConfigurationForQName.setTypeName(QName.class.getName());
        final QNameBinarySerializer qnameSerializer = new QNameBinarySerializer();
        qnameSerializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForQName.setSerializer(qnameSerializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForAcl, binaryTypeConfigurationForAclProperties,
                binaryTypeConfigurationForAclEntity, binaryTypeConfigurationForAce, binaryTypeConfigurationForSimplePermissionReference,
                binaryTypeConfigurationForQName));
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
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, false, false);
            this.correctnessImpl(conf);
        }
    }

    @Test
    public void defaultFormQNameIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, false);
            this.correctnessImpl(conf);
        }
    }

    @Test
    public void defaultFormEfficiency()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration defaultConf = createConfiguration(appContext, false, false, "comparison1", "comparison2");
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, false, "comparison1", "comparison2");

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");
            referenceConf.setDataStorageConfiguration(defaultConf.getDataStorageConfiguration());

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);

                final CacheConfiguration<Long, SimpleAccessControlList> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.REPLICATED);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, SimpleAccessControlList> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, SimpleAccessControlList> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // some indirect optimisation via component objects (QName etc.) - 9%
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica optimised", "Ignite default", 0.09);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, SimpleAccessControlList> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, SimpleAccessControlList> cache2 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // QName ID substitution (also via component objects) - 26%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, referenceCache2, cache2, "aldica optimised (QName ID substitution)",
                        "Ignite default", 0.26);
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
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, false, true);
            this.correctnessImpl(conf);
        }
    }

    @Test
    public void rawSerialFormQNameIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, true);
            this.correctnessImpl(conf);
        }
    }

    @Test
    public void rawSerialFormEfficiency()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration referenceConf = createConfiguration(appContext, false, false, "comparison1", "comparison2",
                    "comparison3");
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration referenceIdConf = createConfiguration(appContext, true, false, "comparison3");
            referenceIdConf.setIgniteInstanceName(referenceIdConf.getIgniteInstanceName() + "-referenceId");

            final IgniteConfiguration defaultConf = createConfiguration(appContext, false, true, "comparison1", "comparison2",
                    "comparison3");
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, true, "comparison1", "comparison2",
                    "comparison3");

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite referenceIdGrid = Ignition.start(referenceIdConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);

                final CacheConfiguration<Long, SimpleAccessControlList> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.REPLICATED);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, SimpleAccessControlList> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, SimpleAccessControlList> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // fully flattened/inlined structure has a significant edge - 45%
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", 0.45);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, SimpleAccessControlList> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, SimpleAccessControlList> cache2 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // QName ID substitution adds marginal improvement - 48%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, referenceCache2, cache2, "aldica raw serial (ID substitution)",
                        "aldica optimised", 0.48);

                cacheConfig.setName("comparison3");
                cacheConfig.setDataRegionName("comparison3");
                final IgniteCache<Long, SimpleAccessControlList> referenceIdCache3 = referenceIdGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, SimpleAccessControlList> cache3 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // QName ID substitution in regular form brings it only a bit closer - 36%
                this.efficiencyImpl(referenceIdGrid, useQNameIdGrid, referenceIdCache3, cache3, "aldica raw serial (ID substitution)",
                        "aldica optimised (ID substitution)", 0.36);
            }
            finally
            {
                Ignition.stopAll(true);
            }
        }
    }

    protected void correctnessImpl(final IgniteConfiguration conf)
    {
        try (Ignite grid = Ignition.start(conf))
        {
            final CacheConfiguration<Long, SimpleAccessControlList> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("simpleAccessControlList");
            cacheConfig.setCacheMode(CacheMode.REPLICATED);
            final IgniteCache<Long, SimpleAccessControlList> cache = grid.getOrCreateCache(cacheConfig);

            final SecureRandom rnJesus = new SecureRandom();

            SimpleAccessControlList controlValue;
            AccessControlListProperties controlProperties;
            AclEntity controlAcl;
            List<AccessControlEntry> controlEntries;
            SimpleAccessControlList cacheValue;
            AccessControlListProperties cacheProperties;
            List<AccessControlEntry> cacheEntries;

            // case with AclEntity

            controlValue = new SimpleAccessControlList();

            controlProperties = controlAcl = randomAclEntity(rnJesus);
            controlValue.setProperties(controlProperties);

            controlEntries = new ArrayList<>();
            controlEntries.add(randomAce(rnJesus, 0));
            controlEntries.add(randomAce(rnJesus, 1));
            controlEntries.add(randomAce(rnJesus, 2));
            controlValue.setEntries(controlEntries);

            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            // no equals() in SimpleAccessControlList
            // we do a deep check ourselves
            Assert.assertNotSame(controlValue, cacheValue);

            cacheProperties = cacheValue.getProperties();
            Assert.assertNotNull(cacheProperties);
            Assert.assertEquals(controlProperties instanceof AclEntity, cacheProperties instanceof AclEntity);
            assertAclEntity(controlAcl, (AclEntity) cacheProperties);

            cacheEntries = cacheValue.getEntries();
            Assert.assertNotNull(cacheEntries);
            Assert.assertEquals(controlEntries.size(), cacheEntries.size());
            for (int i = 0; i < controlEntries.size(); i++)
            {
                Assert.assertEquals(controlEntries.get(i) instanceof SimpleAccessControlEntry,
                        cacheEntries.get(i) instanceof SimpleAccessControlEntry);
                assertAce((SimpleAccessControlEntry) controlEntries.get(i), (SimpleAccessControlEntry) cacheEntries.get(i));
            }

            Assert.assertNull(cacheValue.getCachedSimpleNodePermissionEntry());

            // case without AclEntity

            controlValue = new SimpleAccessControlList();

            controlProperties = randomAclProperties(rnJesus);
            controlValue.setProperties(controlProperties);

            controlEntries = new ArrayList<>();
            controlEntries.add(randomAce(rnJesus, -1));
            controlEntries.add(randomAce(rnJesus, -2));
            controlEntries.add(randomAce(rnJesus, -3));
            controlValue.setEntries(controlEntries);

            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            // no equals() in SimpleAccessControlList
            // we do a deep check ourselves
            Assert.assertNotSame(controlValue, cacheValue);

            cacheProperties = cacheValue.getProperties();
            Assert.assertNotNull(cacheProperties);
            Assert.assertEquals(controlProperties instanceof SimpleAccessControlListProperties,
                    cacheProperties instanceof SimpleAccessControlListProperties);
            assertAclProperties((SimpleAccessControlListProperties) controlProperties, (SimpleAccessControlListProperties) cacheProperties);

            cacheEntries = cacheValue.getEntries();
            Assert.assertNotNull(cacheEntries);
            Assert.assertEquals(controlEntries.size(), cacheEntries.size());
            for (int i = 0; i < controlEntries.size(); i++)
            {
                Assert.assertEquals(controlEntries.get(i) instanceof SimpleAccessControlEntry,
                        cacheEntries.get(i) instanceof SimpleAccessControlEntry);
                assertAce((SimpleAccessControlEntry) controlEntries.get(i), (SimpleAccessControlEntry) cacheEntries.get(i));
            }

            Assert.assertNull(cacheValue.getCachedSimpleNodePermissionEntry());
        }
    }

    @SuppressWarnings("deprecation")
    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid,
            final IgniteCache<Long, SimpleAccessControlList> referenceCache, final IgniteCache<Long, SimpleAccessControlList> cache,
            final String serialisationType, final String referenceSerialisationType, final double marginFraction)
    {
        LOGGER.info(
                "Running SimpleAccessControlList serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            final SimpleAccessControlList value = new SimpleAccessControlList();

            final AccessControlListProperties aclProperties = rnJesus.nextBoolean() ? this.randomAclEntity(rnJesus)
                    : this.randomAclProperties(rnJesus);
            value.setProperties(aclProperties);

            final int entryCount = rnJesus.nextInt(10);
            final List<AccessControlEntry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++)
            {
                final SimpleAccessControlEntry ace = this.randomAce(rnJesus, i);
                entries.add(ace);
            }
            value.setEntries(entries);

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
