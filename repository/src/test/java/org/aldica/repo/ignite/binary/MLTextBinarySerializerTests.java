/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.aldica.common.ignite.GridTestsBase;
import org.alfresco.repo.domain.locale.LocaleDAO;
import org.alfresco.repo.domain.locale.ibatis.LocaleDAOImpl;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.util.Pair;
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
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author Axel Faust
 */
public class MLTextBinarySerializerTests extends GridTestsBase
{

    private static final Locale[] LOCALES = { Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.CHINESE, Locale.JAPANESE, Locale.ITALIAN,
            Locale.SIMPLIFIED_CHINESE, Locale.US, Locale.UK, Locale.GERMANY };

    private static final Logger LOGGER = LoggerFactory.getLogger(MLTextBinarySerializerTests.class);

    protected static GenericApplicationContext createApplicationContext()
    {
        final GenericApplicationContext appContext = new GenericApplicationContext();

        final LocaleDAO localeDAO = EasyMock.partialMockBuilder(LocaleDAOImpl.class).addMockedMethod("getLocalePair", Long.class)
                .addMockedMethod("getLocalePair", Locale.class).createMock();
        appContext.getBeanFactory().registerSingleton("localeDAO", localeDAO);
        appContext.refresh();

        for (int idx = 0; idx < LOCALES.length; idx++)
        {
            EasyMock.expect(localeDAO.getLocalePair(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), LOCALES[idx]));
            EasyMock.expect(localeDAO.getLocalePair(LOCALES[idx])).andStubReturn(new Pair<>(Long.valueOf(idx), LOCALES[idx]));
        }
        EasyMock.expect(localeDAO.getLocalePair(EasyMock.anyObject(Locale.class))).andStubReturn(null);

        EasyMock.replay(localeDAO);

        return appContext;
    }

    protected static IgniteConfiguration createConfiguration(final ApplicationContext applicationContext,
            final boolean idsWhenReasonable,
            final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForMLText = new BinaryTypeConfiguration();
        binaryTypeConfigurationForMLText.setTypeName(MLText.class.getName());
        final MLTextBinarySerializer serializer = new MLTextBinarySerializer();
        serializer.setApplicationContext(applicationContext);
        serializer.setUseIdsWhenReasonable(idsWhenReasonable);
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForMLText.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForMLText));
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
        final IgniteConfiguration conf = createConfiguration(null, false, false);
        this.correctnessImpl(conf);
    }

    @Test
    public void defaultFormIdSubstitutionCorrectness()
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

            final IgniteConfiguration defaultConf = createConfiguration(null, false, false, "comparison1", "comparison2", "comparison3");
            final IgniteConfiguration useIdConf = createConfiguration(appContext, true, false, "comparison1", "comparison2", "comparison3");
            useIdConf.setIgniteInstanceName(useIdConf.getIgniteInstanceName() + "-idSubstitution");

            referenceConf.setDataStorageConfiguration(defaultConf.getDataStorageConfiguration());

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useIdGrid = Ignition.start(useIdConf);

                final CacheConfiguration<Long, MLText> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.LOCAL);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, MLText> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, MLText> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // since our optimised serialisation replaces Locale with textual representation, we can provide a significant benefit - 27%
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica optimised", "Ignite default", 0.27);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, MLText> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, MLText> cache2 = useIdGrid.getOrCreateCache(cacheConfig);

                // ID substitution should have roughly similar benefit when language AND country variant are used - 27%
                this.efficiencyImpl(referenceGrid, useIdGrid, referenceCache2, cache2, "aldica optimised (ID substitution)",
                        "Ignite default", 0.27);

                cacheConfig.setName("comparison3");
                cacheConfig.setDataRegionName("comparison3");
                final IgniteCache<Long, MLText> referenceCache3 = defaultGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, MLText> cache3 = useIdGrid.getOrCreateCache(cacheConfig);

                // since switching Locale for its textual representation in default optimisation
                // ID substitution provides equal benefits for Locale instances with language AND country variant
                // ID substitution provides less benefits for Locale instances with only language
                this.efficiencyImpl(defaultGrid, useIdGrid, referenceCache3, cache3,
                        "aldica optimised (ID substitution)",
                        "aldica optimised", -0.01);
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
        final IgniteConfiguration conf = createConfiguration(null, false, true);
        this.correctnessImpl(conf);
    }

    @Test
    public void rawSerialFormIdSubstitutionCorrectness()
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
            final IgniteConfiguration referenceConf = createConfiguration(null, false, false, "comparison1", "comparison2", "comparison3");
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration defaultConf = createConfiguration(null, false, true, "comparison1", "comparison2", "comparison3");
            final IgniteConfiguration useIdConf = createConfiguration(appContext, true, true, "comparison1", "comparison2", "comparison3");
            useIdConf.setIgniteInstanceName(useIdConf.getIgniteInstanceName() + "-idSubstitution");

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useIdGrid = Ignition.start(useIdConf);

                final CacheConfiguration<Long, MLText> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.LOCAL);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, MLText> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, MLText> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // normally, for objects with a single field, there is no benefit in raw serial form
                // but MLText is able to use String optimisations via variable length primitives - 9%
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", 0.09);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, MLText> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, MLText> cache2 = useIdGrid.getOrCreateCache(cacheConfig);

                // aldica default already provides decent improvements by using Locale textual representation
                // serial form with ID substitution can still provide benefits via variable length primitives for ID substitution - 13%
                this.efficiencyImpl(referenceGrid, useIdGrid, referenceCache2, cache2, "aldica raw serial (ID substitution)",
                        "aldica optimised", 0.13);

                cacheConfig.setName("comparison3");
                cacheConfig.setDataRegionName("comparison3");
                final IgniteCache<Long, MLText> referenceCache3 = defaultGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, MLText> cache3 = useIdGrid.getOrCreateCache(cacheConfig);

                // only benefit comes from variable length primitive long being more efficient than short text - 4%
                this.efficiencyImpl(defaultGrid, useIdGrid, referenceCache3, cache3, "aldica raw serial (ID substitution)",
                        "aldica raw serial", 0.04);
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
            final CacheConfiguration<Long, MLText> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("mlText");
            cacheConfig.setCacheMode(CacheMode.LOCAL);
            final IgniteCache<Long, MLText> cache = grid.getOrCreateCache(cacheConfig);

            MLText controlValue;
            MLText cacheValue;

            controlValue = new MLText(Locale.ENGLISH, "English text");
            controlValue.addValue(Locale.GERMAN, "German text");

            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertFalse(controlValue == cacheValue);

            // test unsupported locale
            controlValue = new MLText(Locale.UK, "English text");
            controlValue.addValue(Locale.GERMANY, "German text");

            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertFalse(controlValue == cacheValue);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite defaultGrid,
            final IgniteCache<Long, MLText> referenceCache,
            final IgniteCache<Long, MLText> cache, final String serialisationType, final String referenceSerialisationType,
            final double marginFraction)
    {
        LOGGER.info(
                "Running MLText serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        for (int idx = 0; idx < 100000; idx++)
        {
            final MLText value = new MLText(Locale.US, UUID.randomUUID().toString());
            value.addValue(Locale.GERMANY, UUID.randomUUID().toString());
            value.addValue(Locale.UK, UUID.randomUUID().toString());

            referenceCache.put(Long.valueOf(idx), value);
            cache.put(Long.valueOf(idx), value);
        }

        @SuppressWarnings("unchecked")
        final String regionName = cache.getConfiguration(CacheConfiguration.class).getDataRegionName();
        final DataRegionMetrics referenceMetrics = referenceGrid.dataRegionMetrics(regionName);
        final DataRegionMetrics metrics = defaultGrid.dataRegionMetrics(regionName);

        // sufficient to compare used pages - byte-exact memory usage cannot be determined due to potential partial page fill
        final long referenceTotalUsedPages = referenceMetrics.getTotalUsedPages();
        final long totalUsedPages = metrics.getTotalUsedPages();
        final long allowedMax = referenceTotalUsedPages - (long) (marginFraction * referenceTotalUsedPages);
        LOGGER.info("Benchmark resulted in {} vs {} (expected max of {}) total used pages", referenceTotalUsedPages, totalUsedPages,
                allowedMax);
        Assert.assertTrue(totalUsedPages <= allowedMax);
    }
}
