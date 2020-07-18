/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.cache.NodePropertiesCacheMap;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.repo.content.filestore.FileContentUrlProvider;
import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.contentdata.ibatis.ContentDataDAOImpl;
import org.alfresco.repo.domain.node.ContentDataWithId;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.repo.domain.qname.ibatis.QNameDAOImpl;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
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
public class NodePropertiesBinarySerializerTests extends GridTestsBase
{

    private static final QName[] PROP_QNAMES = { ContentModel.PROP_NAME, ContentModel.PROP_MODIFIED, ContentModel.PROP_CREATED,
            ContentModel.PROP_CREATOR, ContentModel.PROP_MODIFIER, ContentModel.PROP_CONTENT, ContentModel.PROP_CATEGORIES };

    private static final String[] MIMETYPES = { MimetypeMap.MIMETYPE_PDF, MimetypeMap.MIMETYPE_JSON, MimetypeMap.MIMETYPE_TEXT_PLAIN,
            MimetypeMap.MIMETYPE_OPENDOCUMENT_TEXT, MimetypeMap.MIMETYPE_OPENDOCUMENT_SPREADSHEET,
            MimetypeMap.MIMETYPE_OPENDOCUMENT_PRESENTATION };

    private static final String[] ENCODINGS = { StandardCharsets.UTF_8.name(), StandardCharsets.UTF_16.name(),
            StandardCharsets.ISO_8859_1.name() };

    private static final Locale[] LOCALES = { Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH, Locale.CHINESE, Locale.JAPANESE, Locale.ITALIAN,
            Locale.SIMPLIFIED_CHINESE };

    private static final int UNIQUE_CONTENT_DATA_COUNT = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(NodePropertiesBinarySerializerTests.class);

    protected static GenericApplicationContext createApplicationContext()
    {
        final GenericApplicationContext appContext = new GenericApplicationContext();

        final QNameDAO qnameDAO = EasyMock.partialMockBuilder(QNameDAOImpl.class).addMockedMethod("getQName", Long.class)
                .addMockedMethod("getQName", QName.class).createMock();
        final ContentDataDAO contentDataDAO = EasyMock.partialMockBuilder(ContentDataDAOImpl.class)
                .addMockedMethod("getContentData", Long.class).createMock();
        appContext.getBeanFactory().registerSingleton("qnameDAO", qnameDAO);
        appContext.getBeanFactory().registerSingleton("contentDataDAO", contentDataDAO);
        appContext.refresh();

        for (int idx = 0; idx < PROP_QNAMES.length; idx++)
        {
            EasyMock.expect(qnameDAO.getQName(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), PROP_QNAMES[idx]));
            EasyMock.expect(qnameDAO.getQName(PROP_QNAMES[idx])).andStubReturn(new Pair<>(Long.valueOf(idx), PROP_QNAMES[idx]));
        }
        EasyMock.expect(qnameDAO.getQName(EasyMock.anyObject(QName.class))).andStubReturn(null);

        // default Alfresco classes are inaccessible (package-protected visibility)
        final FileContentUrlProvider urlProvider = () -> FileContentStore.STORE_PROTOCOL + "://" + UUID.randomUUID().toString();
        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < UNIQUE_CONTENT_DATA_COUNT; idx++)
        {
            final ContentDataWithId value = new ContentDataWithId(new ContentData(urlProvider
                    .createNewFileStoreUrl(),
                    MIMETYPES[rnJesus.nextInt(MIMETYPES.length)], rnJesus.nextInt(Integer.MAX_VALUE),
                    ENCODINGS[rnJesus.nextInt(ENCODINGS.length)], LOCALES[rnJesus.nextInt(LOCALES.length)]), Long.valueOf(idx));

            EasyMock.expect(contentDataDAO.getContentData(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), value));
        }

        EasyMock.replay(qnameDAO);
        EasyMock.replay(contentDataDAO);

        return appContext;
    }

    protected static IgniteConfiguration createConfiguration(final ApplicationContext applicationContext, final boolean idsWhenReasonable,
            final boolean idsWhenPossible, final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final NodePropertiesBinarySerializer serializer = new NodePropertiesBinarySerializer();
        serializer.setApplicationContext(applicationContext);
        serializer.setUseIdsWhenReasonable(idsWhenReasonable);
        serializer.setUseIdsWhenPossible(idsWhenPossible);
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);

        final BinaryTypeConfiguration binaryTypeConfigurationForNodePropertiesCacheMap = new BinaryTypeConfiguration();
        binaryTypeConfigurationForNodePropertiesCacheMap.setTypeName(NodePropertiesCacheMap.class.getName());
        binaryTypeConfigurationForNodePropertiesCacheMap.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForNodePropertiesCacheMap));
        conf.setBinaryConfiguration(binaryConfiguration);

        final DataStorageConfiguration dataConf = new DataStorageConfiguration();
        final List<DataRegionConfiguration> regionConfs = new ArrayList<>();
        for (final String regionName : regionNames)
        {
            final DataRegionConfiguration regionConf = new DataRegionConfiguration();
            regionConf.setName(regionName);
            // all regions are 10-250 MiB
            regionConf.setInitialSize(10 * 1024 * 1024);
            regionConf.setMaxSize(250 * 1024 * 1024);
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
            final IgniteConfiguration conf = createConfiguration(null, false, false, false);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Test
    public void defaultFormQNameIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, false, false);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Test
    public void defaultFormQNameAndContentDataIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, true, false);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Test
    public void defaultFormEfficiency()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration defaultConf = createConfiguration(null, false, false, false, "comparison1", "comparison2",
                    "comparison3", "comparison4", "comparison5", "comparison6");
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, false, false, "comparison1", "comparison2",
                    "comparison3", "comparison4", "comparison5", "comparison6");
            final IgniteConfiguration useAllIdConf = createConfiguration(appContext, true, true, false, "comparison1", "comparison2",
                    "comparison3", "comparison4", "comparison5", "comparison6");

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");
            useAllIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-allIdSubstitution");

            referenceConf.setDataStorageConfiguration(defaultConf.getDataStorageConfiguration());

            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);
                final Ignite useAllIdGrid = Ignition.start(useAllIdConf);

                final CacheConfiguration<Long, NodePropertiesCacheMap> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.LOCAL);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // default uses HashMap.writeObject and Serializable all the way through, which is already very efficient
                // without ID substitution, our serialisation cannot come close - -73%
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, contentDataDAO, "aldica optimised",
                        "Ignite default", -0.73);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache2 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // replacing full QName with ID saves a lot and overcomes base disadvantage
                // 23%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, referenceCache2, cache2, contentDataDAO,
                        "aldica optimised (QName ID substitution)", "Ignite default", 0.23);

                cacheConfig.setName("comparison3");
                cacheConfig.setDataRegionName("comparison3");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache3 = defaultGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache3 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // savings are more pronounced compared to our own base
                // 55%
                this.efficiencyImpl(defaultGrid, useQNameIdGrid, referenceCache3, cache3, contentDataDAO,
                        "aldica optimised (QName ID substitution)", "aldica optimised", 0.55);

                cacheConfig.setName("comparison4");
                cacheConfig.setDataRegionName("comparison4");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache4 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache4 = useAllIdGrid.getOrCreateCache(cacheConfig);

                // savings should be more pronounced with both QName and ContentDataWithId replaced
                // 54%
                this.efficiencyImpl(referenceGrid, useAllIdGrid, referenceCache4, cache4,
                        contentDataDAO,
                        "aldica optimised (QName + ContentData ID substitution)", "Ignite default", 0.54);

                cacheConfig.setName("comparison5");
                cacheConfig.setDataRegionName("comparison5");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache5 = defaultGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache5 = useAllIdGrid.getOrCreateCache(cacheConfig);

                // savings are extremely more pronounced compared to our own base
                // 73%
                this.efficiencyImpl(defaultGrid, useAllIdGrid, referenceCache5, cache5, contentDataDAO,
                        "aldica optimised (QName + ContentData ID substitution)", "aldica optimised", 0.73);

                cacheConfig.setName("comparison6");
                cacheConfig.setDataRegionName("comparison6");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache6 = useQNameIdGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache6 = useAllIdGrid.getOrCreateCache(cacheConfig);

                // ContentDataWithId is quite complex, so significant savings in addition to QName ID substitution if sparse metadata
                // 39%
                this.efficiencyImpl(useQNameIdGrid, useAllIdGrid, referenceCache6, cache6, contentDataDAO,
                        "aldica optimised (QName + ContentData ID substitution)", "aldica optimised (QName ID substitution)", 0.39);
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
            final IgniteConfiguration conf = createConfiguration(null, false, false, true);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Test
    public void rawSerialFormQNameIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, false, true);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Test
    public void rawSerialFormQNameAndContentDataIdSubstitutionCorrectness()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration conf = createConfiguration(appContext, true, true, true);
            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);
            this.correctnessImpl(conf, contentDataDAO);
        }
    }

    @Test
    public void rawSerialFormEfficiency()
    {
        try (final GenericApplicationContext appContext = createApplicationContext())
        {
            final IgniteConfiguration referenceConf = createConfiguration(null, false, false, false, "comparison1", "comparison2",
                    "comparison3", "comparison4", "comparison5", "comparison6");
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration defaultConf = createConfiguration(null, false, false, true, "comparison1", "comparison2",
                    "comparison3", "comparison4", "comparison5", "comparison6");
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, false, true, "comparison1", "comparison2",
                    "comparison3", "comparison4", "comparison5", "comparison6");
            final IgniteConfiguration useAllIdConf = createConfiguration(appContext, true, true, true, "comparison1", "comparison2",
                    "comparison3", "comparison4", "comparison5", "comparison6");

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");
            useAllIdConf.setIgniteInstanceName(useAllIdConf.getIgniteInstanceName() + "-allIdSubstitution");

            final ContentDataDAO contentDataDAO = appContext.getBean("contentDataDAO", ContentDataDAO.class);

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);
                final Ignite useAllIdGrid = Ignition.start(useAllIdConf);

                final CacheConfiguration<Long, NodePropertiesCacheMap> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.LOCAL);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // virtually no difference as map handling has almost no overhead to reduce
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, contentDataDAO, "aldica raw serial",
                        "aldica optimised", -0.01);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache2 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // QNames are expensive due to namespace + local name
                // 61%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, referenceCache2, cache2, contentDataDAO,
                        "aldica raw serial (ID substitution)", "aldica optimised", 0.61);

                cacheConfig.setName("comparison3");
                cacheConfig.setDataRegionName("comparison3");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache3 = defaultGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache3 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // 61%
                this.efficiencyImpl(defaultGrid, useQNameIdGrid, referenceCache3, cache3, contentDataDAO,
                        "aldica raw serial (ID substitution)", "aldica raw serial", 0.61);

                cacheConfig.setName("comparison4");
                cacheConfig.setDataRegionName("comparison4");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache4 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache4 = useAllIdGrid.getOrCreateCache(cacheConfig);

                // savings should be more pronounced with both QName and ContentDataWithId replaced
                // 81%
                this.efficiencyImpl(referenceGrid, useAllIdGrid, referenceCache4, cache4, contentDataDAO,
                        "aldica raw serial (QName + ContentData ID substitution)", "aldica optimised", 0.81);

                cacheConfig.setName("comparison5");
                cacheConfig.setDataRegionName("comparison5");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache5 = defaultGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache5 = useAllIdGrid.getOrCreateCache(cacheConfig);

                // 81%
                this.efficiencyImpl(defaultGrid, useAllIdGrid, referenceCache5, cache5, contentDataDAO,
                        "aldica raw serial (QName + ContentData ID substitution)", "aldica raw serial", 0.81);

                cacheConfig.setName("comparison6");
                cacheConfig.setDataRegionName("comparison6");
                final IgniteCache<Long, NodePropertiesCacheMap> referenceCache6 = useQNameIdGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodePropertiesCacheMap> cache6 = useAllIdGrid.getOrCreateCache(cacheConfig);

                // ContentDataWithId is quite complex, so significant savings in addition to QName ID substitution if sparse metadata
                // 52%
                this.efficiencyImpl(useQNameIdGrid, useAllIdGrid, referenceCache6, cache6, contentDataDAO,
                        "aldica raw serial (QName + ContentData ID substitution)", "aldica raw serial (QName ID substitution)", 0.52);
            }
            finally
            {
                Ignition.stopAll(true);
            }
        }
    }

    protected void correctnessImpl(final IgniteConfiguration conf, final ContentDataDAO contentDataDAO)
    {
        try (Ignite grid = Ignition.start(conf))
        {
            final CacheConfiguration<Long, NodePropertiesCacheMap> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("contentData");
            cacheConfig.setCacheMode(CacheMode.LOCAL);
            final IgniteCache<Long, NodePropertiesCacheMap> cache = grid.getOrCreateCache(cacheConfig);

            NodePropertiesCacheMap controlValue;
            NodePropertiesCacheMap cacheValue;
            ArrayList<NodeRef> categories;

            controlValue = new NodePropertiesCacheMap(Collections.emptyMap());
            controlValue.put(ContentModel.PROP_CREATOR, "admin");
            controlValue.put(ContentModel.PROP_CREATED,
                    Date.from(LocalDateTime.of(2020, Month.JANUARY, 1, 6, 0, 0).toInstant(ZoneOffset.UTC)));
            controlValue.put(ContentModel.PROP_MODIFIER, "editor");
            controlValue.put(ContentModel.PROP_MODIFIED,
                    Date.from(LocalDateTime.of(2020, Month.JULY, 1, 23, 12, 45).toInstant(ZoneOffset.UTC)));
            controlValue.put(ContentModel.PROP_NAME, UUID.randomUUID().toString());
            controlValue.put(ContentModel.PROP_CONTENT, contentDataDAO.getContentData(1l));

            categories = new ArrayList<>();
            categories.add(new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString()));
            categories.add(new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString()));
            controlValue.put(ContentModel.PROP_CATEGORIES, categories);

            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertFalse(controlValue == cacheValue);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite defaultGrid,
            final IgniteCache<Long, NodePropertiesCacheMap> referenceCache, final IgniteCache<Long, NodePropertiesCacheMap> cache,
            final ContentDataDAO contentDataDAO, final String serialisationType,
            final String referenceSerialisationType,
            final double marginFraction)
    {
        LOGGER.info(
                "Running NodePropertiesCacheMap serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        final int msPerYear = 365 * 24 * 60 * 60 * 1000;
        final long msOffset = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            final NodePropertiesCacheMap value = new NodePropertiesCacheMap();

            final int msOffsetCreated = rnJesus.nextInt(msPerYear / 2);
            final int msOffsetModified = msOffsetCreated + rnJesus.nextInt(msPerYear - msOffsetCreated);

            value.put(ContentModel.PROP_CREATOR, "admin" + (idx % 100));
            value.put(ContentModel.PROP_CREATED, new Date(msOffset + msOffsetCreated));
            value.put(ContentModel.PROP_MODIFIER, "editor" + (idx % 100));
            value.put(ContentModel.PROP_MODIFIED, new Date(msOffset + msOffsetModified));
            value.put(ContentModel.PROP_NAME, UUID.randomUUID().toString());
            value.put(ContentModel.PROP_CONTENT,
                    contentDataDAO.getContentData(Long.valueOf(rnJesus.nextInt(UNIQUE_CONTENT_DATA_COUNT))).getSecond());

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
