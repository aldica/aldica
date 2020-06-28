/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.aldica.common.ignite.GridTestsBase;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.repo.content.filestore.FileContentUrlProvider;
import org.alfresco.repo.domain.encoding.EncodingDAO;
import org.alfresco.repo.domain.encoding.ibatis.EncodingDAOImpl;
import org.alfresco.repo.domain.locale.LocaleDAO;
import org.alfresco.repo.domain.locale.ibatis.LocaleDAOImpl;
import org.alfresco.repo.domain.mimetype.MimetypeDAO;
import org.alfresco.repo.domain.mimetype.ibatis.MimetypeDAOImpl;
import org.alfresco.repo.domain.node.ContentDataWithId;
import org.alfresco.service.cmr.repository.ContentData;
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
public class ContentDataBinarySerializerTests extends GridTestsBase
{

    private static final String[] MIMETYPES = { MimetypeMap.MIMETYPE_PDF, MimetypeMap.MIMETYPE_JSON, MimetypeMap.MIMETYPE_TEXT_PLAIN,
            MimetypeMap.MIMETYPE_OPENDOCUMENT_TEXT, MimetypeMap.MIMETYPE_OPENDOCUMENT_SPREADSHEET,
            MimetypeMap.MIMETYPE_OPENDOCUMENT_PRESENTATION };

    private static final String[] ENCODINGS = { StandardCharsets.UTF_8.name(), StandardCharsets.UTF_16.name(),
            StandardCharsets.ISO_8859_1.name() };

    private static final Locale[] LOCALES = { Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.CHINESE, Locale.JAPANESE, Locale.ITALIAN,
            Locale.SIMPLIFIED_CHINESE };

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentDataBinarySerializerTests.class);

    protected static GenericApplicationContext createApplicationContext()
    {
        final GenericApplicationContext appContext = new GenericApplicationContext();

        final MimetypeDAO mimetypeDAO = EasyMock.partialMockBuilder(MimetypeDAOImpl.class).addMockedMethod("getMimetype", Long.class)
                .addMockedMethod("getMimetype", String.class).createMock();
        final EncodingDAO encodingDAO = EasyMock.partialMockBuilder(EncodingDAOImpl.class).addMockedMethod("getEncoding", Long.class)
                .addMockedMethod("getEncoding", String.class).createMock();
        final LocaleDAO localeDAO = EasyMock.partialMockBuilder(LocaleDAOImpl.class).addMockedMethod("getLocalePair", Long.class)
                .addMockedMethod("getLocalePair", Locale.class).createMock();
        appContext.getBeanFactory().registerSingleton("mimetypeDAO", mimetypeDAO);
        appContext.getBeanFactory().registerSingleton("encodingDAO", encodingDAO);
        appContext.getBeanFactory().registerSingleton("localeDAO", localeDAO);
        appContext.refresh();

        for (int idx = 0; idx < MIMETYPES.length; idx++)
        {
            EasyMock.expect(mimetypeDAO.getMimetype(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), MIMETYPES[idx]));
            EasyMock.expect(mimetypeDAO.getMimetype(MIMETYPES[idx])).andStubReturn(new Pair<>(Long.valueOf(idx), MIMETYPES[idx]));
        }
        EasyMock.expect(mimetypeDAO.getMimetype(EasyMock.anyString())).andStubReturn(null);

        for (int idx = 0; idx < ENCODINGS.length; idx++)
        {
            EasyMock.expect(encodingDAO.getEncoding(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), ENCODINGS[idx]));
            EasyMock.expect(encodingDAO.getEncoding(ENCODINGS[idx])).andStubReturn(new Pair<>(Long.valueOf(idx), ENCODINGS[idx]));
        }
        EasyMock.expect(encodingDAO.getEncoding(EasyMock.anyString())).andStubReturn(null);

        for (int idx = 0; idx < LOCALES.length; idx++)
        {
            EasyMock.expect(localeDAO.getLocalePair(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), LOCALES[idx]));
            EasyMock.expect(localeDAO.getLocalePair(LOCALES[idx])).andStubReturn(new Pair<>(Long.valueOf(idx), LOCALES[idx]));
        }
        EasyMock.expect(localeDAO.getLocalePair(EasyMock.anyObject(Locale.class))).andStubReturn(null);

        EasyMock.replay(mimetypeDAO);
        EasyMock.replay(encodingDAO);
        EasyMock.replay(localeDAO);

        return appContext;
    }

    protected static IgniteConfiguration createConfiguration(final ApplicationContext applicationContext,
            final boolean idsWhenReasonable,
            final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final ContentDataBinarySerializer serializer = new ContentDataBinarySerializer();
        serializer.setApplicationContext(applicationContext);
        serializer.setUseIdsWhenReasonable(idsWhenReasonable);
        serializer.setUseRawSerialForm(serialForm);

        final BinaryTypeConfiguration binaryTypeConfigurationForContentData = new BinaryTypeConfiguration();
        binaryTypeConfigurationForContentData.setTypeName(ContentData.class.getName());
        binaryTypeConfigurationForContentData.setSerializer(serializer);

        final BinaryTypeConfiguration binaryTypeConfigurationForContentDataWithId = new BinaryTypeConfiguration();
        binaryTypeConfigurationForContentDataWithId.setTypeName(ContentDataWithId.class.getName());
        binaryTypeConfigurationForContentDataWithId.setSerializer(serializer);

        binaryConfiguration
                .setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForContentData, binaryTypeConfigurationForContentDataWithId));
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

                final CacheConfiguration<Long, ContentData> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.LOCAL);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, ContentData> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, ContentData> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // this case should actually be identical
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica optimised", "Ignite default", 0);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, ContentData> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, ContentData> cache2 = useIdGrid.getOrCreateCache(cacheConfig);

                // replacing 3 non-trivial fields with IDs is substantial - 23%
                this.efficiencyImpl(referenceGrid, useIdGrid, referenceCache2, cache2, "aldica optimised (ID substitution)",
                        "Ignite default", 0.23);

                cacheConfig.setName("comparison3");
                cacheConfig.setDataRegionName("comparison3");
                final IgniteCache<Long, ContentData> referenceCache3 = defaultGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, ContentData> cache3 = useIdGrid.getOrCreateCache(cacheConfig);

                // replacing 3 non-trivial fields with IDs is substantial - 23%
                this.efficiencyImpl(defaultGrid, useIdGrid, referenceCache3, cache3, "aldica optimised (ID substitution)",
                        "aldica optimised", 0.23);
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

                final CacheConfiguration<Long, ContentData> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.LOCAL);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, ContentData> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, ContentData> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // saving potential is limited - 1.5%
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", 0.015);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, ContentData> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, ContentData> cache2 = useIdGrid.getOrCreateCache(cacheConfig);

                // replacing 3 non-trivial fields with IDs is substantial - 27%
                this.efficiencyImpl(referenceGrid, useIdGrid, referenceCache2, cache2, "aldica raw serial (ID substitution)",
                        "aldica optimised", 0.27);

                cacheConfig.setName("comparison3");
                cacheConfig.setDataRegionName("comparison3");
                final IgniteCache<Long, ContentData> referenceCache3 = defaultGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, ContentData> cache3 = useIdGrid.getOrCreateCache(cacheConfig);

                // replacing 3 non-trivial fields with IDs is substantial - 25%
                this.efficiencyImpl(defaultGrid, useIdGrid, referenceCache3, cache3, "aldica raw serial (ID substitution)",
                        "aldica raw serial", 0.25);
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
            final CacheConfiguration<Long, ContentData> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("contentData");
            cacheConfig.setCacheMode(CacheMode.LOCAL);
            final IgniteCache<Long, ContentData> cache = grid.getOrCreateCache(cacheConfig);

            ContentData controlValue;
            ContentData cacheValue;

            // default Alfresco classes are inaccessible (package-protected visibility)
            final FileContentUrlProvider urlProvider = () -> FileContentStore.STORE_PROTOCOL + "://" + UUID.randomUUID().toString();

            controlValue = new ContentData(urlProvider.createNewFileStoreUrl(), MimetypeMap.MIMETYPE_PDF, 123l,
                    StandardCharsets.UTF_8.name(), Locale.ENGLISH);

            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertFalse(controlValue == cacheValue);

            // test values not in any mocked DAOs
            controlValue = new ContentData(urlProvider.createNewFileStoreUrl(), MimetypeMap.MIMETYPE_EXCEL, 123l,
                    StandardCharsets.US_ASCII.name(), Locale.GERMANY);

            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertFalse(controlValue == cacheValue);

            // test null values for reference elements
            controlValue = new ContentData(urlProvider.createNewFileStoreUrl(), null, 123l, null, null);

            cache.put(3l, controlValue);

            cacheValue = cache.get(3l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertFalse(controlValue == cacheValue);

            // test content data with ID extension
            controlValue = new ContentData(urlProvider.createNewFileStoreUrl(), MimetypeMap.MIMETYPE_PDF, 123l,
                    StandardCharsets.UTF_8.name(), Locale.ENGLISH);
            controlValue = new ContentDataWithId(controlValue, 4l);

            cache.put(4l, controlValue);

            cacheValue = cache.get(4l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertFalse(controlValue == cacheValue);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite defaultGrid, final IgniteCache<Long, ContentData> referenceCache,
            final IgniteCache<Long, ContentData> cache, final String serialisationType,
            final String referenceSerialisationType,
            final double marginFraction)
    {
        LOGGER.info(
                "Running ContentData serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        // default Alfresco classes are inaccessible (package-protected visibility)
        final FileContentUrlProvider urlProvider = () -> FileContentStore.STORE_PROTOCOL + "://" + UUID.randomUUID().toString();

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            ContentData value = new ContentData(urlProvider.createNewFileStoreUrl(), MIMETYPES[rnJesus.nextInt(
                    MIMETYPES.length)],
                    rnJesus.nextInt(Integer.MAX_VALUE), ENCODINGS[rnJesus.nextInt(ENCODINGS.length)],
                    LOCALES[rnJesus.nextInt(LOCALES.length)]);

            final boolean withId = rnJesus.nextBoolean();
            if (withId)
            {
                value = new ContentDataWithId(value, Long.valueOf(idx));
            }

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
