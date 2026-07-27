/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.aldica.common.ignite.GridTestsBase;
import org.alfresco.repo.domain.node.AuditablePropertiesEntity;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.repo.domain.node.StoreEntity;
import org.alfresco.repo.domain.node.TransactionEntity;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.util.ISO8601DateFormat;
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
public class NodeEntityBinarySerializerTests extends GridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeEntityBinarySerializerTests.class);

    protected static IgniteConfiguration createConfiguration(final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final NodeEntityBinarySerializer entitySerializer = new NodeEntityBinarySerializer();
        entitySerializer.setUseRawSerialForm(serialForm);

        final BinaryTypeConfiguration binaryTypeConfigurationForNodeEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForNodeEntity.setTypeName(NodeEntity.class.getName());
        binaryTypeConfigurationForNodeEntity.setSerializer(entitySerializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForNodeEntity));
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

            final CacheConfiguration<Long, NodeEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("values");
            cacheConfig.setDataRegionName("values");
            final IgniteCache<Long, NodeEntity> referenceCache = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, NodeEntity> cache = grid.getOrCreateCache(cacheConfig);

            // value inlining + selective serialisation - 27.5%
            this.efficiencyImpl(referenceGrid, grid, referenceCache, cache, "aldica optimised", "Ignite default", 0.275);
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
        final IgniteConfiguration referenceConf = createConfiguration(1, true, null);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(true, "values");

        referenceConf.setDataStorageConfiguration(conf.getDataStorageConfiguration());
        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            final CacheConfiguration<Long, NodeEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("values");
            cacheConfig.setDataRegionName("values");
            final IgniteCache<Long, NodeEntity> referenceCache = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, NodeEntity> cache = grid.getOrCreateCache(cacheConfig);

            // known value substitution + numeric optimisations - 49%
            this.efficiencyImpl(referenceGrid, grid, referenceCache, cache, "aldica raw serial", "aldica optimised", 0.49);
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
            final CacheConfiguration<Long, NodeEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("nodeEntity");
            cacheConfig.setCacheMode(CacheMode.REPLICATED);
            final IgniteCache<Long, NodeEntity> cache = grid.getOrCreateCache(cacheConfig);

            NodeEntity controlValue;
            StoreEntity controlStore;
            TransactionEntity controlTxn;
            AuditablePropertiesEntity controlAuditable;

            NodeEntity cacheValue;
            StoreEntity cacheStore;
            TransactionEntity cacheTxn;
            AuditablePropertiesEntity cacheAuditable;

            controlValue = new NodeEntity();
            controlValue.setId(123456789l);
            controlValue.setVersion(234l);

            controlStore = new StoreEntity();
            controlStore.setId(12l);
            controlStore.setVersion(1l);
            controlStore.setProtocol("system");
            controlStore.setIdentifier("system");
            controlValue.setStore(controlStore);

            controlValue.setUuid(UUID.randomUUID().toString());
            controlValue.setTypeQNameId(6541l);
            controlValue.setLocaleId(-6541l);
            controlValue.setAclId(9876879l);

            controlTxn = new TransactionEntity();
            controlTxn.setId(1223412l);
            controlTxn.setVersion(1l);
            controlTxn.setCommitTimeMs(System.currentTimeMillis());
            controlTxn.setChangeTxnId(UUID.randomUUID().toString());
            controlValue.setTransaction(controlTxn);

            controlAuditable = new AuditablePropertiesEntity();
            controlAuditable.setAuditAccessed("2025-01-01T01:23:45.678Z");
            controlAuditable.setAuditModified("2025-02-03T01:23:45.678Z");
            controlAuditable.setAuditModifier("janedoe");
            controlAuditable.setAuditCreated("2025-03-04T01:23:45.678Z");
            controlAuditable.setAuditCreator("johndoe");
            controlValue.setAuditableProperties(controlAuditable);

            controlValue.setShardKey("somekey");
            controlValue.setExplicitShardId(1234);

            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertEquals(controlValue.getId(), cacheValue.getId());
            Assert.assertEquals(controlValue.getVersion(), cacheValue.getVersion());

            cacheStore = cacheValue.getStore();
            Assert.assertNotNull(cacheStore);
            // selective serialisation
            Assert.assertNull(cacheStore.getVersion());
            Assert.assertNull(cacheStore.getRootNode());
            Assert.assertEquals(controlStore.getId(), controlStore.getId());
            Assert.assertEquals(controlStore.getProtocol(), controlStore.getProtocol());
            Assert.assertEquals(controlStore.getIdentifier(), controlStore.getIdentifier());

            Assert.assertEquals(controlValue.getUuid(), cacheValue.getUuid());
            Assert.assertEquals(controlValue.getTypeQNameId(), cacheValue.getTypeQNameId());
            Assert.assertEquals(controlValue.getLocaleId(), cacheValue.getLocaleId());
            Assert.assertEquals(controlValue.getAclId(), cacheValue.getAclId());

            cacheTxn = cacheValue.getTransaction();
            // selective serialisation
            Assert.assertNull(cacheTxn.getVersion());
            Assert.assertNull(cacheTxn.getCommitTimeMs());
            Assert.assertEquals(controlTxn.getId(), cacheTxn.getId());
            Assert.assertEquals(controlTxn.getChangeTxnId(), cacheTxn.getChangeTxnId());

            cacheAuditable = cacheValue.getAuditableProperties();
            Assert.assertEquals(controlAuditable.getAuditAccessed(), cacheAuditable.getAuditAccessed());
            Assert.assertEquals(controlAuditable.getAuditModified(), cacheAuditable.getAuditModified());
            Assert.assertEquals(controlAuditable.getAuditModifier(), cacheAuditable.getAuditModifier());
            Assert.assertEquals(controlAuditable.getAuditCreated(), cacheAuditable.getAuditCreated());
            Assert.assertEquals(controlAuditable.getAuditCreator(), cacheAuditable.getAuditCreator());

            Assert.assertNull(cacheValue.getShardKey());
            Assert.assertNull(cacheValue.getExplicitShardId());

            // check neither node nor auditable properties entity are locked
            try
            {
                cacheValue.incrementVersion();
                Assert.fail("Cache value should be locked");
            }
            catch (final IllegalStateException ignored)
            {

            }
            try
            {
                cacheAuditable.setAuditCreated("someone");
                Assert.fail("Cache value component should be locked");
            }
            catch (final IllegalStateException ignored)
            {

            }

            controlValue = new NodeEntity();
            controlValue.setId(-165498l);
            controlValue.setVersion(-1l);

            controlStore = new StoreEntity();
            controlStore.setId(-1l);
            controlStore.setVersion(Long.MAX_VALUE);
            controlStore.setProtocol(StoreRef.PROTOCOL_WORKSPACE);
            controlStore.setIdentifier(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.getIdentifier());
            controlValue.setStore(controlStore);

            controlValue.setUuid(UUID.randomUUID().toString());
            controlValue.setTypeQNameId(Long.MAX_VALUE);
            controlValue.setLocaleId(Long.MIN_VALUE);
            controlValue.setAclId(null);

            controlTxn = new TransactionEntity();
            controlTxn.setId(-1223412l);
            controlTxn.setVersion(Long.MAX_VALUE);
            controlTxn.setCommitTimeMs(System.currentTimeMillis());
            controlTxn.setChangeTxnId(UUID.randomUUID().toString());
            controlValue.setTransaction(controlTxn);

            controlValue.setShardKey("somekey");
            controlValue.setExplicitShardId(1234);

            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            Assert.assertEquals(controlValue, cacheValue);
            Assert.assertEquals(controlValue.getId(), cacheValue.getId());
            Assert.assertEquals(controlValue.getVersion(), cacheValue.getVersion());

            cacheStore = cacheValue.getStore();
            Assert.assertNotNull(cacheStore);
            // selective serialisation
            Assert.assertNull(cacheStore.getVersion());
            Assert.assertNull(cacheStore.getRootNode());
            Assert.assertEquals(controlStore.getId(), controlStore.getId());
            Assert.assertEquals(controlStore.getProtocol(), controlStore.getProtocol());
            Assert.assertEquals(controlStore.getIdentifier(), controlStore.getIdentifier());

            Assert.assertEquals(controlValue.getUuid(), cacheValue.getUuid());
            Assert.assertEquals(controlValue.getTypeQNameId(), cacheValue.getTypeQNameId());
            Assert.assertEquals(controlValue.getLocaleId(), cacheValue.getLocaleId());
            Assert.assertEquals(controlValue.getAclId(), cacheValue.getAclId());

            cacheTxn = cacheValue.getTransaction();
            // selective serialisation
            Assert.assertNull(cacheTxn.getVersion());
            Assert.assertNull(cacheTxn.getCommitTimeMs());
            Assert.assertEquals(controlTxn.getId(), cacheTxn.getId());
            Assert.assertEquals(controlTxn.getChangeTxnId(), cacheTxn.getChangeTxnId());

            Assert.assertNull(cacheValue.getAuditableProperties());
        }
    }

    @SuppressWarnings("deprecation")
    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite defaultGrid, final IgniteCache<Long, NodeEntity> referenceCache,
            final IgniteCache<Long, NodeEntity> cache, final String serialisationType, final String referenceSerialisationType,
            final double marginFraction)
    {
        LOGGER.info(
                "Running NodeEntity serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        final String[] protocols = { StoreRef.PROTOCOL_WORKSPACE, StoreRef.PROTOCOL_ARCHIVE, "user", "system" };
        final String[] identifiers = { "SpacesStore", "version2Store", "alfrescoUserStore", "system" };

        final int msPerYear = 365 * 24 * 60 * 60 * 1000;
        final long msOffset = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            final int msOffsetCreated = rnJesus.nextInt(msPerYear / 2);
            final int msOffsetModified = msOffsetCreated + rnJesus.nextInt(msPerYear - msOffsetCreated);
            final int msOffsetAccessed = msOffsetModified + rnJesus.nextInt(msPerYear - msOffsetModified);

            final NodeEntity value = new NodeEntity();
            value.setId(Long.valueOf(idx));
            value.setVersion(Long.valueOf(rnJesus.nextInt(10)));

            final StoreEntity store = new StoreEntity();
            store.setId(Long.valueOf(rnJesus.nextInt(6)));
            store.setVersion(1l);
            store.setProtocol(protocols[rnJesus.nextInt(protocols.length)]);
            store.setIdentifier(identifiers[rnJesus.nextInt(identifiers.length)]);
            value.setStore(store);

            value.setUuid(UUID.randomUUID().toString());
            value.setTypeQNameId(6541l);
            value.setLocaleId(-6541l);
            value.setAclId(9876879l);

            final TransactionEntity txn = new TransactionEntity();
            txn.setId(rnJesus.nextLong());
            txn.setVersion(1l);
            txn.setCommitTimeMs(msOffset + msOffsetModified);
            txn.setChangeTxnId(UUID.randomUUID().toString());
            value.setTransaction(txn);

            final AuditablePropertiesEntity auditable = new AuditablePropertiesEntity();
            auditable.setAuditAccessed(ISO8601DateFormat.format(new Date(msOffset + msOffsetAccessed)));
            auditable.setAuditModified(ISO8601DateFormat.format(new Date(msOffset + msOffsetModified)));
            auditable.setAuditModifier("admin" + (idx % 100));
            auditable.setAuditCreated(ISO8601DateFormat.format(new Date(msOffset + msOffsetCreated)));
            auditable.setAuditCreator("admin" + (idx % 100));
            value.setAuditableProperties(auditable);

            value.setShardKey("somekey");
            value.setExplicitShardId(1234);

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
