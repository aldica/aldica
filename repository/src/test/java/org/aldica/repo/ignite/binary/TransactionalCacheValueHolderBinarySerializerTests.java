/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.aldica.common.ignite.GridTestsBase;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ActionModel;
import org.alfresco.repo.cache.TransactionalCache.ValueHolder;
import org.alfresco.service.cmr.repository.NodeRef;
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

/**
 * @author Axel Faust
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class TransactionalCacheValueHolderBinarySerializerTests extends GridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionalCacheValueHolderBinarySerializerTests.class);

    private static final Constructor<ValueHolder> VALUE_HOLDER_CTOR;

    // copied from EntityLookupCache
    private static final Serializable VALUE_NULL = "@@VALUE_NULL@@";

    private static final Serializable VALUE_NOT_FOUND = "@@VALUE_NOT_FOUND@@";

    private static final QName[] QNAMES = { ContentModel.PROP_NAME, ContentModel.PROP_MODIFIED, ContentModel.PROP_CREATED,
            ContentModel.PROP_CREATOR, ContentModel.PROP_MODIFIER, ContentModel.PROP_CONTENT, ContentModel.PROP_CATEGORIES,
            ContentModel.PROP_CLIENT_CONTROLLED, ContentModel.PROP_VISIBILITY_MASK, ContentModel.PROP_INHERIT_FROM_ACL,
            ActionModel.PROP_PARAMETER_NAME, ActionModel.PROP_PARAMETER_VALUE };

    private static final String PROTOCOL_USER = "user";

    private static final String PROTOCOL_SYSTEM = "system";

    private static final String[] PROTOCOLS = { PROTOCOL_USER, PROTOCOL_SYSTEM, StoreRef.PROTOCOL_ARCHIVE, StoreRef.PROTOCOL_WORKSPACE };

    private static final String[] IDS = { "SpacesStore", "version2Store", "alfrescoUserStore", "system" };

    private static final Class[] CLASSES = { NodeRef.class, StoreRef.class, UUID.class, SecureRandom.class, Integer.class, Long.class,
            Map.class, Set.class, List.class, Date.class, Float.class, Double.class };

    static
    {
        try
        {
            final Constructor<ValueHolder> ctor = ValueHolder.class.getDeclaredConstructor(Object.class);
            ctor.setAccessible(true);
            VALUE_HOLDER_CTOR = ctor;
        }
        catch (final NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected static IgniteConfiguration createConfiguration(final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForCacheValueHolder = new BinaryTypeConfiguration();
        binaryTypeConfigurationForCacheValueHolder.setTypeName(ValueHolder.class.getName());
        final TransactionalCacheValueHolderBinarySerializer holderSerializer = new TransactionalCacheValueHolderBinarySerializer();
        holderSerializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForCacheValueHolder.setSerializer(holderSerializer);

        final BinaryTypeConfiguration binaryTypeConfigurationForStoreRef = new BinaryTypeConfiguration();
        binaryTypeConfigurationForStoreRef.setTypeName(StoreRef.class.getName());
        final StoreRefBinarySerializer storeSerializer = new StoreRefBinarySerializer();
        storeSerializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForStoreRef.setSerializer(storeSerializer);

        final BinaryTypeConfiguration binaryTypeConfigurationForNodeRef = new BinaryTypeConfiguration();
        binaryTypeConfigurationForNodeRef.setTypeName(NodeRef.class.getName());
        final NodeRefBinarySerializer nodeRefSerializer = new NodeRefBinarySerializer();
        nodeRefSerializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForNodeRef.setSerializer(nodeRefSerializer);

        final BinaryTypeConfiguration binaryTypeConfigurationForQName = new BinaryTypeConfiguration();
        binaryTypeConfigurationForQName.setTypeName(QName.class.getName());
        final QNameBinarySerializer qnameSerializer = new QNameBinarySerializer();
        qnameSerializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForQName.setSerializer(qnameSerializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForCacheValueHolder,
                binaryTypeConfigurationForStoreRef, binaryTypeConfigurationForNodeRef, binaryTypeConfigurationForQName));
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
        final IgniteConfiguration conf = createConfiguration(false, "values");

        referenceConf.setDataStorageConfiguration(conf.getDataStorageConfiguration());

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            final CacheConfiguration<Long, ValueHolder<?>> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("values");
            cacheConfig.setDataRegionName("values");
            final IgniteCache<Long, ValueHolder<?>> referenceCache = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, ValueHolder<?>> cache = grid.getOrCreateCache(cacheConfig);

            // negligible gains
            this.efficiencyImpl(referenceGrid, grid, referenceCache, cache, "aldica optimised", "Ignite default", 0.05);
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
        final IgniteConfiguration referenceConf = createConfiguration(false, "values");
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(true, "values");

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            final CacheConfiguration<Long, ValueHolder<?>> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("values");
            cacheConfig.setDataRegionName("values");
            final IgniteCache<Long, ValueHolder<?>> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, ValueHolder<?>> cache1 = grid.getOrCreateCache(cacheConfig);

            // minor additional gains
            this.efficiencyImpl(referenceGrid, grid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", 0.07);
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
            final CacheConfiguration<Long, ValueHolder<?>> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("cacheRegionKey");
            cacheConfig.setCacheMode(CacheMode.REPLICATED);
            final IgniteCache<Long, ValueHolder<?>> cache = grid.getOrCreateCache(cacheConfig);

            ValueHolder<?> controlValue;
            ValueHolder<?> cacheValue;

            controlValue = VALUE_HOLDER_CTOR.newInstance(new Object[] { null });
            cache.put(1l, controlValue);
            cacheValue = cache.get(1l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(VALUE_NULL);
            cache.put(2l, controlValue);
            cacheValue = cache.get(2l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(VALUE_NOT_FOUND);
            cache.put(3l, controlValue);
            cacheValue = cache.get(3l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(UUID.randomUUID().toString());
            cache.put(4l, controlValue);
            cacheValue = cache.get(4l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(Long.valueOf(-97546));
            cache.put(5l, controlValue);
            cacheValue = cache.get(5l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(Integer.valueOf(97546));
            cache.put(6l, controlValue);
            cacheValue = cache.get(6l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(Float.valueOf(-11.231234f));
            cache.put(7l, controlValue);
            cacheValue = cache.get(7l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(Double.valueOf(-11.231234d));
            cache.put(8l, controlValue);
            cacheValue = cache.get(8l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(Date.from(Instant.now()));
            cache.put(9l, controlValue);
            cacheValue = cache.get(9l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(NodeRef.class);
            cache.put(10l, controlValue);
            cacheValue = cache.get(10l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(ContentModel.PROP_NAME);
            cache.put(11l, controlValue);
            cacheValue = cache.get(11l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR
                    .newInstance(new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString()));
            cache.put(12l, controlValue);
            cacheValue = cache.get(12l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
            cache.put(13l, controlValue);
            cacheValue = cache.get(13l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);

            controlValue = VALUE_HOLDER_CTOR.newInstance(UUID.randomUUID());
            cache.put(14l, controlValue);
            cacheValue = cache.get(14l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertNotSame(controlValue, cacheValue);
        }
        catch (final InvocationTargetException | IllegalAccessException | InstantiationException e)
        {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("deprecation")
    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final IgniteCache<Long, ValueHolder<?>> referenceCache,
            final IgniteCache<Long, ValueHolder<?>> cache, final String serialisationType, final String referenceSerialisationType,
            final double marginFraction)
    {
        LOGGER.info(
                "Running TransactionalCache$ValueHolder serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        final SecureRandom rnJesus = new SecureRandom();

        final int msPerYear = 365 * 24 * 60 * 60 * 1000;
        final long msOffset = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();

        try
        {
            for (int idx = 0; idx < 100000; idx++)
            {
                Object value;

                switch (rnJesus.nextInt(14))
                {
                    case 1:
                        value = VALUE_NULL;
                        break;
                    case 2:
                        value = VALUE_NOT_FOUND;
                        break;
                    case 3:
                        value = UUID.randomUUID().toString();
                        break;
                    case 4:
                        value = Long.valueOf(rnJesus.nextLong());
                        break;
                    case 5:
                        value = Integer.valueOf(rnJesus.nextInt());
                        break;
                    case 6:
                        value = Float.valueOf(rnJesus.nextFloat());
                        break;
                    case 7:
                        value = Double.valueOf(rnJesus.nextDouble());
                        break;
                    case 8:
                        final int msOffsetDelta = rnJesus.nextInt(3 * msPerYear);
                        value = new Date(msOffset + msOffsetDelta);
                        break;
                    case 9:
                        value = CLASSES[rnJesus.nextInt(CLASSES.length)];
                        break;
                    case 10:
                        value = QNAMES[rnJesus.nextInt(QNAMES.length)];
                        break;
                    case 11:
                        value = new NodeRef(new StoreRef(PROTOCOLS[rnJesus.nextInt(PROTOCOLS.length)], IDS[rnJesus.nextInt(IDS.length)]),
                                UUID.randomUUID().toString());
                        break;
                    case 12:
                        value = new StoreRef(PROTOCOLS[rnJesus.nextInt(PROTOCOLS.length)], IDS[rnJesus.nextInt(IDS.length)]);
                        break;
                    case 13:
                        value = UUID.randomUUID();
                        break;
                    default:
                        value = null;
                }

                final ValueHolder<?> holder = VALUE_HOLDER_CTOR.newInstance(value);
                referenceCache.put(Long.valueOf(idx), holder);
                cache.put(Long.valueOf(idx), holder);
            }
        }
        catch (final InvocationTargetException | IllegalAccessException | InstantiationException e)
        {
            throw new RuntimeException(e);
        }

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
