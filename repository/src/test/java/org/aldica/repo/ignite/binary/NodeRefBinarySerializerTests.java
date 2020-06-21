/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.aldica.common.ignite.GridTestsBase;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
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
public class NodeRefBinarySerializerTests extends GridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRefBinarySerializerTests.class);

    private static final int MODE_FULLY_RANDOM = 0;

    private static final int MODE_RANDOM_STORE_AND_NODE_ID = 1;

    private static final int MODE_KNOWN_STORE_AND_RANDOM_NODE_ID = 2;

    private static final String PROTOCOL_USER = "user";

    private static final String PROTOCOL_SYSTEM = "system";

    private static final String[] PROTOCOLS = { PROTOCOL_USER, PROTOCOL_SYSTEM, StoreRef.PROTOCOL_ARCHIVE, StoreRef.PROTOCOL_WORKSPACE };

    private static final StoreRef[] DEFAULT_STORE_REFS = new StoreRef[] { NodeRefBinarySerializer.REF_ARCHIVE_SPACES_STORE,
            NodeRefBinarySerializer.REF_SYSTEM_SYSTEM, NodeRefBinarySerializer.REF_USER_ALFRESCO_USER_STORE,
            NodeRefBinarySerializer.REF_WORKSPACE_LIGHT_WEIGHT_VERSION_STORE, NodeRefBinarySerializer.REF_WORKSPACE_SPACES_STORE,
            NodeRefBinarySerializer.REF_WORKSPACE_VERSION2STORE };

    protected static IgniteConfiguration createConfiguration(final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForNodeRef = new BinaryTypeConfiguration();
        binaryTypeConfigurationForNodeRef.setTypeName(NodeRef.class.getName());
        final NodeRefBinarySerializer serializer = new NodeRefBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForNodeRef.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForNodeRef));
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
        final IgniteConfiguration conf = createConfiguration(false, "fullyRandomNodeRefs", "randomNodeRefsKnownStore",
                "knownStoreRandomId");

        referenceConf.setDataStorageConfiguration(conf.getDataStorageConfiguration());

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            final CacheConfiguration<Long, NodeRef> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            cacheConfig.setName("fullyRandomNodeRefs");
            cacheConfig.setDataRegionName("fullyRandomNodeRefs");
            final IgniteCache<Long, NodeRef> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, NodeRef> cache1 = grid.getOrCreateCache(cacheConfig);

            // minimal potential, but inlining StoreRef should still yield at least 9%
            this.efficiencyImpl(referenceGrid, grid, referenceCache1, cache1, "aldica optimised", "Ignite default", MODE_FULLY_RANDOM,
                    0.09);

            cacheConfig.setName("randomNodeRefsKnownStore");
            cacheConfig.setDataRegionName("randomNodeRefsKnownStore");
            final IgniteCache<Long, NodeRef> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, NodeRef> cache2 = grid.getOrCreateCache(cacheConfig);

            // decent potential with known store protocol, 15%
            this.efficiencyImpl(referenceGrid, grid, referenceCache2, cache2, "aldica optimised", "Ignite default",
                    MODE_RANDOM_STORE_AND_NODE_ID, 0.15);

            cacheConfig.setName("knownStoreRandomId");
            cacheConfig.setDataRegionName("knownStoreRandomId");
            final IgniteCache<Long, NodeRef> referenceCache3 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, NodeRef> cache3 = grid.getOrCreateCache(cacheConfig);

            // most potential with full store optimised away into a byte flag, 30%
            this.efficiencyImpl(referenceGrid, grid, referenceCache3, cache3, "aldica optimised", "Ignite default",
                    MODE_KNOWN_STORE_AND_RANDOM_NODE_ID, 0.3);
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
        final IgniteConfiguration referenceConf = createConfiguration(false, "fullyRandomNodeRefs", "randomNodeRefsKnownStore",
                "knownStoreRandomId");
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(true, "fullyRandomNodeRefs", "randomNodeRefsKnownStore", "knownStoreRandomId");

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            final CacheConfiguration<Long, NodeRef> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            cacheConfig.setName("fullyRandomNodeRefs");
            cacheConfig.setDataRegionName("fullyRandomNodeRefs");
            final IgniteCache<Long, NodeRef> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, NodeRef> cache1 = grid.getOrCreateCache(cacheConfig);

            // saving potential is limited - 4%
            this.efficiencyImpl(referenceGrid, grid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", MODE_FULLY_RANDOM,
                    0.03);

            cacheConfig.setName("randomNodeRefsKnownStore");
            cacheConfig.setDataRegionName("randomNodeRefsKnownStore");
            final IgniteCache<Long, NodeRef> referenceCache2 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, NodeRef> cache2 = grid.getOrCreateCache(cacheConfig);

            // saving potential is limited - 3%
            this.efficiencyImpl(referenceGrid, grid, referenceCache2, cache2, "aldica raw serial", "aldica optimised",
                    MODE_RANDOM_STORE_AND_NODE_ID, 0.03);

            cacheConfig.setName("knownStoreRandomId");
            cacheConfig.setDataRegionName("knownStoreRandomId");
            final IgniteCache<Long, NodeRef> referenceCache3 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, NodeRef> cache3 = grid.getOrCreateCache(cacheConfig);

            // saving potential is limited - 2%
            this.efficiencyImpl(referenceGrid, grid, referenceCache3, cache3, "aldica raw serial", "aldica optimised",
                    MODE_KNOWN_STORE_AND_RANDOM_NODE_ID, 0.02);
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
            final CacheConfiguration<Long, NodeRef> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("nodeRef");
            cacheConfig.setCacheMode(CacheMode.LOCAL);
            final IgniteCache<Long, NodeRef> cache = grid.getOrCreateCache(cacheConfig);

            NodeRef controlValue;
            NodeRef cacheValue;

            controlValue = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "node1");
            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved
            Assert.assertFalse(controlValue == cacheValue);
            Assert.assertFalse(controlValue.getId() == cacheValue.getId());
            // well-known store should use same value
            Assert.assertTrue(controlValue.getStoreRef() == cacheValue.getStoreRef());

            controlValue = new NodeRef(new StoreRef("my", "store"), "node2");
            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved
            Assert.assertFalse(controlValue == cacheValue);
            Assert.assertFalse(controlValue.getStoreRef() == cacheValue.getStoreRef());
            Assert.assertFalse(controlValue.getId() == cacheValue.getId());

            controlValue = new NodeRef(new StoreRef(StoreRef.PROTOCOL_WORKSPACE, "store"), "node3");
            cache.put(3l, controlValue);

            cacheValue = cache.get(3l);

            Assert.assertEquals(controlValue, cacheValue);
            // check deep serialisation was actually involved
            Assert.assertFalse(controlValue == cacheValue);
            Assert.assertFalse(controlValue.getStoreRef() == cacheValue.getStoreRef());
            Assert.assertFalse(controlValue.getId() == cacheValue.getId());
            Assert.assertFalse(controlValue.getStoreRef().getIdentifier() == cacheValue.getStoreRef().getIdentifier());
            // well known protocol should use same value
            Assert.assertTrue(controlValue.getStoreRef().getProtocol() == cacheValue.getStoreRef().getProtocol());
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final IgniteCache<Long, NodeRef> referenceCache,
            final IgniteCache<Long, NodeRef> cache, final String serialisationType, final String referenceSerialisationType, final int mode,
            final double marginFraction)
    {
        String modeStr = "unknown";
        switch (mode)
        {
            case MODE_FULLY_RANDOM:
                modeStr = "fully random values";
                break;
            case MODE_RANDOM_STORE_AND_NODE_ID:
                modeStr = "random store and node IDs";
                break;
            case MODE_KNOWN_STORE_AND_RANDOM_NODE_ID:
                modeStr = "known stores and random node IDs";
                break;
        }

        LOGGER.info(
                "Running NodeRef serialisation benchmark of 100k instances with {}, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                modeStr, referenceSerialisationType, serialisationType, marginFraction);

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            StoreRef storeRef = null;
            switch (mode)
            {
                case MODE_FULLY_RANDOM:
                    storeRef = new StoreRef(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                    break;
                case MODE_RANDOM_STORE_AND_NODE_ID:
                    storeRef = new StoreRef(PROTOCOLS[rnJesus.nextInt(PROTOCOLS.length)], UUID.randomUUID().toString());
                    break;
                case MODE_KNOWN_STORE_AND_RANDOM_NODE_ID:
                    storeRef = DEFAULT_STORE_REFS[rnJesus.nextInt(DEFAULT_STORE_REFS.length)];
                    break;
            }
            final NodeRef value = new NodeRef(storeRef, UUID.randomUUID().toString());
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
