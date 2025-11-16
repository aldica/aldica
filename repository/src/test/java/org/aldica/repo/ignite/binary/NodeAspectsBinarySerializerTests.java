/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.cache.NodeAspectsCacheSet;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.repo.domain.qname.ibatis.QNameDAOImpl;
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
public class NodeAspectsBinarySerializerTests extends GridTestsBase
{

    private static final QName CUSTOM_ASPECT = QName.createQName("myCustomModel", "myCustomAspect");

    @SuppressWarnings("deprecation")
    private static final QName[] QNAMES = { ContentModel.ASPECT_ANULLABLE, ContentModel.ASPECT_ARCHIVE_LOCKABLE,
            ContentModel.ASPECT_ARCHIVE_ROOT, ContentModel.ASPECT_ARCHIVED, ContentModel.ASPECT_ARCHIVED_ASSOCS,
            ContentModel.ASPECT_ATTACHABLE, ContentModel.ASPECT_AUDITABLE, ContentModel.ASPECT_AUTHOR, ContentModel.ASPECT_CASCADE_UPDATE,
            ContentModel.ASPECT_CHECKED_OUT, ContentModel.ASPECT_CLASSIFIABLE, ContentModel.ASPECT_CMIS_CREATED_CHECKEDOUT,
            ContentModel.ASPECT_CMIS_UPDATE_CONTEXT, ContentModel.ASPECT_COPIEDFROM, ContentModel.ASPECT_COUNTABLE,
            ContentModel.ASPECT_DUBLINCORE, ContentModel.ASPECT_EMAILED, ContentModel.ASPECT_FAILED_THUMBNAIL_SOURCE,
            ContentModel.ASPECT_FIVESTAR_RATING_SCHEME_ROLLUPS, ContentModel.ASPECT_GEN_CLASSIFIABLE, ContentModel.ASPECT_GEOGRAPHIC,
            ContentModel.ASPECT_GEOGRAPHIC, ContentModel.ASPECT_HIDDEN, ContentModel.ASPECT_INCOMPLETE, ContentModel.ASPECT_INDEX_CONTROL,
            ContentModel.ASPECT_LIKES_RATING_SCHEME_ROLLUPS, ContentModel.ASPECT_LOCALIZED, ContentModel.ASPECT_LOCKABLE,
            ContentModel.ASPECT_MULTILINGUAL_DOCUMENT, ContentModel.ASPECT_MULTILINGUAL_EMPTY_TRANSLATION, ContentModel.ASPECT_NO_CONTENT,
            ContentModel.ASPECT_OWNABLE, ContentModel.ASPECT_PENDING_FIX_ACL, ContentModel.ASPECT_PERSON_DISABLED,
            ContentModel.ASPECT_PREFERENCES, ContentModel.ASPECT_RATEABLE, ContentModel.ASPECT_REFERENCEABLE,
            ContentModel.ASPECT_REFERENCES_NODE, ContentModel.ASPECT_REFERENCING, ContentModel.ASPECT_ROOT, ContentModel.ASPECT_SOFT_DELETE,
            ContentModel.ASPECT_STORE_SELECTOR, ContentModel.ASPECT_SYNDICATION, ContentModel.ASPECT_TAGGABLE, ContentModel.ASPECT_TAGSCOPE,
            ContentModel.ASPECT_TEMPLATABLE, ContentModel.ASPECT_TEMPORARY, ContentModel.ASPECT_THUMBNAIL_MODIFICATION,
            ContentModel.ASPECT_THUMBNAILED, ContentModel.ASPECT_TITLED, ContentModel.ASPECT_UNDELETABLE, ContentModel.ASPECT_UNMOVABLE,
            ContentModel.ASPECT_VERSIONABLE, ContentModel.ASPECT_WEBDAV_NO_CONTENT, ContentModel.ASPECT_WEBDAV_OBJECT,
            ContentModel.ASPECT_WEBSCRIPTABLE, ContentModel.ASPECT_WORKING_COPY, CUSTOM_ASPECT };

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeAspectsBinarySerializerTests.class);

    protected static GenericApplicationContext createApplicationContext()
    {
        final GenericApplicationContext appContext = new GenericApplicationContext();

        final QNameDAO qnameDAO = EasyMock.partialMockBuilder(QNameDAOImpl.class).addMockedMethod("getQName", Long.class)
                .addMockedMethod("getQName", QName.class).createMock();
        appContext.getBeanFactory().registerSingleton("qnameDAO", qnameDAO);
        appContext.refresh();

        for (int idx = 0; idx < QNAMES.length; idx++)
        {
            EasyMock.expect(qnameDAO.getQName(Long.valueOf(idx))).andStubReturn(new Pair<>(Long.valueOf(idx), QNAMES[idx]));
            EasyMock.expect(qnameDAO.getQName(QNAMES[idx])).andStubReturn(new Pair<>(Long.valueOf(idx), QNAMES[idx]));
        }

        EasyMock.replay(qnameDAO);

        return appContext;
    }

    protected static IgniteConfiguration createConfiguration(final ApplicationContext applicationContext, final boolean idsWhenReasonable,
            final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final NodeAspectsBinarySerializer aspectSerializer = new NodeAspectsBinarySerializer();
        aspectSerializer.setApplicationContext(applicationContext);
        aspectSerializer.setUseIdsWhenReasonable(idsWhenReasonable);
        aspectSerializer.setUseRawSerialForm(serialForm);

        final QNameBinarySerializer qnameSerializer = new QNameBinarySerializer();
        qnameSerializer.setUseRawSerialForm(serialForm);

        final BinaryTypeConfiguration binaryTypeConfigurationForNodeAspectsCacheSet = new BinaryTypeConfiguration();
        binaryTypeConfigurationForNodeAspectsCacheSet.setTypeName(NodeAspectsCacheSet.class.getName());
        binaryTypeConfigurationForNodeAspectsCacheSet.setSerializer(aspectSerializer);

        final BinaryTypeConfiguration binaryTypeConfigurationForQName = new BinaryTypeConfiguration();
        binaryTypeConfigurationForQName.setTypeName(QName.class.getName());
        binaryTypeConfigurationForQName.setSerializer(qnameSerializer);

        binaryConfiguration
                .setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForNodeAspectsCacheSet, binaryTypeConfigurationForQName));
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
            final IgniteConfiguration conf = createConfiguration(null, false, false);
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

            final IgniteConfiguration defaultConf = createConfiguration(null, false, false, "comparison1", "comparison2", "comparison3",
                    "comparison4", "comparison5", "comparison6");
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, false, "comparison1", "comparison2",
                    "comparison3", "comparison4", "comparison5", "comparison6");

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");

            referenceConf.setDataStorageConfiguration(defaultConf.getDataStorageConfiguration());

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);

                final CacheConfiguration<Long, NodeAspectsCacheSet> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.REPLICATED);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, NodeAspectsCacheSet> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodeAspectsCacheSet> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // quite less efficient, despite extra QNameBinarySerializer - not meant for use
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica optimised", "Ignite default", -0.23);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, NodeAspectsCacheSet> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodeAspectsCacheSet> cache2 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // replacing full QName with ID saves a lot and overcomes base disadvantage - 69%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, referenceCache2, cache2, "aldica optimised (QName ID substitution)",
                        "Ignite default", 0.69);

                cacheConfig.setName("comparison3");
                cacheConfig.setDataRegionName("comparison3");
                final IgniteCache<Long, NodeAspectsCacheSet> referenceCache3 = defaultGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodeAspectsCacheSet> cache3 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // savings are more pronounced compared to our own base - 75%
                this.efficiencyImpl(defaultGrid, useQNameIdGrid, referenceCache3, cache3, "aldica optimised (QName ID substitution)",
                        "aldica optimised", 0.75);
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
            final IgniteConfiguration conf = createConfiguration(null, false, true);
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
            final IgniteConfiguration referenceConf = createConfiguration(null, false, false, "comparison1", "comparison2", "comparison3");
            referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

            final IgniteConfiguration referenceIdConf = createConfiguration(appContext, true, false, "comparison4", "comparison5");
            referenceIdConf.setIgniteInstanceName(referenceIdConf.getIgniteInstanceName() + "-referenceId");

            final IgniteConfiguration defaultConf = createConfiguration(null, false, true, "comparison1", "comparison2", "comparison3",
                    "comparison4", "comparison5");
            final IgniteConfiguration useQNameIdConf = createConfiguration(appContext, true, true, "comparison1", "comparison2",
                    "comparison3", "comparison4", "comparison5");

            useQNameIdConf.setIgniteInstanceName(useQNameIdConf.getIgniteInstanceName() + "-qnameIdSubstitution");

            try
            {
                final Ignite referenceGrid = Ignition.start(referenceConf);
                final Ignite referenceIdGrid = Ignition.start(referenceIdConf);
                final Ignite defaultGrid = Ignition.start(defaultConf);
                final Ignite useQNameIdGrid = Ignition.start(useQNameIdConf);

                final CacheConfiguration<Long, NodeAspectsCacheSet> cacheConfig = new CacheConfiguration<>();
                cacheConfig.setCacheMode(CacheMode.REPLICATED);

                cacheConfig.setName("comparison1");
                cacheConfig.setDataRegionName("comparison1");
                final IgniteCache<Long, NodeAspectsCacheSet> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodeAspectsCacheSet> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

                // baseline for regular form is quite bad, despite extra QNameBinarySerializer - 65.5%
                this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", 0.655);

                cacheConfig.setName("comparison2");
                cacheConfig.setDataRegionName("comparison2");
                final IgniteCache<Long, NodeAspectsCacheSet> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodeAspectsCacheSet> cache2 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // QNames are expensive due to namespace + local name - 89%
                this.efficiencyImpl(referenceGrid, useQNameIdGrid, referenceCache2, cache2, "aldica raw serial (ID substitution)",
                        "aldica optimised", 0.89);

                cacheConfig.setName("comparison3");
                cacheConfig.setDataRegionName("comparison3");
                final IgniteCache<Long, NodeAspectsCacheSet> referenceCache3 = defaultGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodeAspectsCacheSet> cache3 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // 68%
                this.efficiencyImpl(defaultGrid, useQNameIdGrid, referenceCache3, cache3, "aldica raw serial (ID substitution)",
                        "aldica raw serial", 0.68);

                cacheConfig.setName("comparison4");
                cacheConfig.setDataRegionName("comparison4");
                final IgniteCache<Long, NodeAspectsCacheSet> referenceIdCache4 = referenceIdGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodeAspectsCacheSet> cache4 = defaultGrid.getOrCreateCache(cacheConfig);

                // our ID substituted regular form should be better than the raw serial form - -38%
                this.efficiencyImpl(referenceIdGrid, defaultGrid, referenceIdCache4, cache4, "aldica raw serial",
                        "aldica optimised (ID substitution)", -0.38);

                cacheConfig.setName("comparison5");
                cacheConfig.setDataRegionName("comparison5");
                final IgniteCache<Long, NodeAspectsCacheSet> referenceIdCache5 = referenceIdGrid.getOrCreateCache(cacheConfig);
                final IgniteCache<Long, NodeAspectsCacheSet> cache5 = useQNameIdGrid.getOrCreateCache(cacheConfig);

                // our low QName IDs compress extremely well in raw serial form - 56%
                this.efficiencyImpl(referenceIdGrid, useQNameIdGrid, referenceIdCache5, cache5, "aldica raw serial (ID substitution)",
                        "aldica optimised (ID substitution)", 0.56);
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
            final CacheConfiguration<Long, NodeAspectsCacheSet> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("nodeAspects");
            cacheConfig.setCacheMode(CacheMode.REPLICATED);
            final IgniteCache<Long, NodeAspectsCacheSet> cache = grid.getOrCreateCache(cacheConfig);

            NodeAspectsCacheSet controlValue;
            NodeAspectsCacheSet cacheValue;

            controlValue = new NodeAspectsCacheSet(Collections.emptySet());
            // more than 8 aspect
            for (int i = 0; i < 12; i++)
            {
                controlValue.add(QNAMES[i]);
            }

            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = new NodeAspectsCacheSet(Collections.emptySet());
            // custom aspect
            controlValue.add(CUSTOM_ASPECT);

            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, cacheValue);
        }
    }

    @SuppressWarnings("deprecation")
    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite defaultGrid,
            final IgniteCache<Long, NodeAspectsCacheSet> referenceCache, final IgniteCache<Long, NodeAspectsCacheSet> cache,
            final String serialisationType, final String referenceSerialisationType, final double marginFraction)
    {
        LOGGER.info(
                "Running NodeAspectsCacheSet serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            final NodeAspectsCacheSet value = new NodeAspectsCacheSet();

            final int aspectCount = QNAMES.length / 2 + rnJesus.nextInt(QNAMES.length / 2);

            for (int j = 0; j < aspectCount; j++)
            {
                value.add(QNAMES[rnJesus.nextInt(QNAMES.length)]);
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
