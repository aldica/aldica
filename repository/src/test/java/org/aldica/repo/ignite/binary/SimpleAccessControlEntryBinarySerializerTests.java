/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.permissions.ACEType;
import org.alfresco.repo.security.permissions.SimpleAccessControlEntry;
import org.alfresco.repo.security.permissions.impl.SimplePermissionReference;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
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
public class SimpleAccessControlEntryBinarySerializerTests extends AclGridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleAccessControlEntryBinarySerializerTests.class);

    protected static IgniteConfiguration createConfiguration(final ApplicationContext applicationContext, final boolean idsWhenReasonable,
            final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

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

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForAce,
                binaryTypeConfigurationForSimplePermissionReference, binaryTypeConfigurationForQName));
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

                final CacheConfiguration<Long, SimpleAccessControlEntry> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.REPLICATED);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, SimpleAccessControlEntry> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, SimpleAccessControlEntry> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // optimised QName does a lot - 34%
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica optimised", "Ignite default", 0.34);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, SimpleAccessControlEntry> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, SimpleAccessControlEntry> cache2 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // replacing QName with ID - 45%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, referenceCache2, cache2, "aldica optimised (QName ID substitution)",
                        "Ignite default", 0.45);
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

                final CacheConfiguration<Long, SimpleAccessControlEntry> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.REPLICATED);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, SimpleAccessControlEntry> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, SimpleAccessControlEntry> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // little improvements - 3%
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", 0.03);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, SimpleAccessControlEntry> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, SimpleAccessControlEntry> cache2 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // little improvements - 3%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, referenceCache2, cache2, "aldica raw serial (ID substitution)",
                        "aldica optimised", 0.03);

                cacheConfig.setName("comparison3");
                cacheConfig.setDataRegionName("comparison3");
                final IgniteCache<Long, SimpleAccessControlEntry> referenceIdCache3 = referenceIdGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, SimpleAccessControlEntry> cache3 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // little improvements - 3%
                this.efficiencyImpl(referenceIdGrid, useQNameIdGrid, referenceIdCache3, cache3, "aldica raw serial (ID substitution)",
                        "aldica optimised (ID substitution)", 0.03);
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
            final CacheConfiguration<Long, SimpleAccessControlEntry> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("simpleAccessControlEntry");
            cacheConfig.setCacheMode(CacheMode.REPLICATED);
            final IgniteCache<Long, SimpleAccessControlEntry> cache = grid.getOrCreateCache(cacheConfig);

            SimpleAccessControlEntry controlValue;
            SimpleAccessControlEntry cacheValue;

            controlValue = new SimpleAccessControlEntry();
            controlValue.setAceType(ACEType.OBJECT);
            controlValue.setAccessStatus(AccessStatus.ALLOWED);
            controlValue.setAuthority("GROUP_site_xyc_Consumer");
            controlValue.setPermission(
                    SimplePermissionReference.getPermissionReference(ContentModel.TYPE_CMOBJECT, PermissionService.CONSUMER));
            controlValue.setPosition(-1);
            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            // no equals() in SimpleAccessControlEntry
            // we do a deep check ourselves
            assertAce(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = new SimpleAccessControlEntry();
            controlValue.setAceType(ACEType.CHILDREN);
            controlValue.setAccessStatus(AccessStatus.DENIED);
            controlValue.setAuthority(UUID.randomUUID().toString());
            controlValue.setPermission(
                    SimplePermissionReference.getPermissionReference(QName.createQName("custom.model", "mytype"), "CustomPermission"));
            controlValue.setPosition(Integer.MAX_VALUE);
            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            assertAce(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);
        }
    }

    @SuppressWarnings("deprecation")
    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid,
            final IgniteCache<Long, SimpleAccessControlEntry> referenceCache, final IgniteCache<Long, SimpleAccessControlEntry> cache,
            final String serialisationType, final String referenceSerialisationType, final double marginFraction)
    {
        LOGGER.info(
                "Running SimpleAccessControlEntry serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            final SimpleAccessControlEntry value = this.randomAce(rnJesus);
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
