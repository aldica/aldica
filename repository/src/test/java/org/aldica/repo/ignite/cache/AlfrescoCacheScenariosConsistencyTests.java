/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.aldica.repo.ignite.binary.value.QNameBinarySerializer;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.cache.TransactionalCache;
import org.alfresco.repo.cache.TransactionalCache.ValueHolder;
import org.alfresco.repo.cache.lookup.CacheRegionKey;
import org.alfresco.repo.cache.lookup.CacheRegionValueKey;
import org.alfresco.repo.cache.lookup.EntityLookupCache;
import org.alfresco.repo.cache.lookup.EntityLookupCache.EntityLookupCallbackDAOAdaptor;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.service.namespace.NamespaceException;
import org.alfresco.service.namespace.NamespacePrefixResolver;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.util.TempFileProvider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.eviction.lru.LruEvictionPolicyFactory;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Axel Faust
 */
public class AlfrescoCacheScenariosConsistencyTests extends GridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AlfrescoCacheScenariosConsistencyTests.class);

    private static final Serializable VALUE_NOT_FOUND = "@@VALUE_NOT_FOUND@@";

    private static final String CACHE_REGION_QNAME = "QName";

    private static final Collection<QName> CONTENT_MODEL_QNAMES;
    static
    {
        try
        {
            final List<QName> contentModelQNames = new ArrayList<>();
            final Field[] declaredFields = ContentModel.class.getDeclaredFields();
            for (final Field declaredField : declaredFields)
            {
                if (QName.class.equals(declaredField.getType()))
                {
                    final QName qname = (QName) declaredField.get(null);
                    contentModelQNames.add(qname);
                }
            }

            CONTENT_MODEL_QNAMES = Collections.unmodifiableList(contentModelQNames);
        }
        catch (final Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void qnameCacheBackedByImmutableEntityCache() throws Exception
    {
        // this essentially uses the default configuration and use case of the immutableEntityCache
        // this cache was observed to suffer from time-delayed inconsistencies in a customer test environment where
        // the value retrieved would not match up with the last value set in the cache, typically for type QName
        // instances which previously did not exist in the DB and were put into cache using "not found" sentinels

        // this test could not yet reproduce the issue from that specific customer, but is a reasonably complex test to keep around for
        // general testing
        try
        {
            final IgniteConfiguration conf1 = createConfiguration(1, false);
            final IgniteConfiguration conf2 = createConfiguration(2, true);

            final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();
            final BinaryTypeConfiguration binaryTypeConfigurationForKeyClass = new BinaryTypeConfiguration();
            binaryTypeConfigurationForKeyClass.setTypeName(QName.class.getName());
            binaryTypeConfigurationForKeyClass.setSerializer(new QNameBinarySerializer());
            binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForKeyClass));

            conf1.setBinaryConfiguration(binaryConfiguration);
            conf2.setBinaryConfiguration(binaryConfiguration);

            final CacheConfiguration<Serializable, ValueHolder<Serializable>> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("cache.immutableEntitySharedCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);
            cacheConfig.setStatisticsEnabled(true);

            final LruEvictionPolicyFactory<Serializable, ValueHolder<Serializable>> evictionPolicyFactory = new LruEvictionPolicyFactory<>();
            evictionPolicyFactory.setMaxSize(50000);

            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final DataRegionConfiguration defaultDataRegion = new DataRegionConfiguration();
            defaultDataRegion.setName("defaultDataRegion");
            // 16 MiB initial - 64 MiB max size
            defaultDataRegion.setInitialSize(1024 * 1024 * 16);
            defaultDataRegion.setMaxSize(1024 * 1024 * 64);

            // we use swap in our default configuration, so this test should too
            final File tempDir = TempFileProvider.getTempDir(UUID.randomUUID().toString());
            tempDir.deleteOnExit();
            defaultDataRegion.setSwapPath(tempDir.toString());

            final DataStorageConfiguration dataStorageConf = new DataStorageConfiguration();
            dataStorageConf.setDefaultDataRegionConfiguration(defaultDataRegion);
            conf1.setDataStorageConfiguration(dataStorageConf);

            final Ignite grid1 = Ignition.start(conf1);

            // second grid only to verify invalidation messages are sent
            final Collection<Object> invalidatedKeys = Collections.synchronizedSet(new HashSet<>());
            final Ignite grid2 = Ignition.start(conf2);
            grid2.message().localListen(cacheConfig.getName() + "-invalidate", (uuid, key) -> {
                invalidatedKeys.add(key);
                return true;
            });

            final IgniteCache<Serializable, ValueHolder<Serializable>> igniteCache = grid1.getOrCreateCache(cacheConfig);
            final SimpleCache<Serializable, ValueHolder<Serializable>> simpleCache = new SimpleIgniteBackedCache<>(grid1,
                    SimpleIgniteBackedCache.Mode.LOCAL_INVALIDATING_ON_CHANGE, igniteCache, true);

            final NamespacePrefixResolver nsPrefixResolver = new TestNamespacePrefixResolver();
            final QNameDAO qnameDAO = new TestQNameDAO();

            // we are not using transactions in this test, but actually need this to properly test with the ValueHolder wrapper
            final TransactionalCache<Serializable, Serializable> immutableEntityCache = new TransactionalCache<>();
            immutableEntityCache.setSharedCache(simpleCache);
            immutableEntityCache.setName("org.alfresco.cache.immutableEntityTransactionalCache");
            immutableEntityCache.setMaxCacheSize(10000);
            // logically not true (cache name literally contains "immutableEntity")
            // but the way it's handled in Alfresco it is technically mutable (due to support of e.g.
            // org.alfresco.repo.admin.patch.impl.QNamePatch)
            immutableEntityCache.setMutable(true);
            immutableEntityCache.setTenantAware(false);
            immutableEntityCache.setCacheStatsEnabled(false);

            final EntityLookupCache<Long, QName, QName> qnameCache = new EntityLookupCache<>(immutableEntityCache, CACHE_REGION_QNAME,
                    new TestQNameCallbackDAO(qnameDAO));

            Pair<Long, QName> entryPair;
            // as transactional cache is not tenant-aware, it does not wrap its CacheRegionKey around the EntityLookupCache's CacheRegionKey
            // / CacheRegionValue
            CacheRegionKey lowLevelEntryCacheKey;
            CacheRegionValueKey lowLevelEntryCacheValueKey;
            ValueHolder<Serializable> lowLevelCacheValue;

            entryPair = qnameCache.getByKey(Long.valueOf(0));
            Assert.assertNull(entryPair);

            Assert.assertEquals("Failed lookup by ID should have added a sentinel cache entry", 1, igniteCache.size());

            lowLevelEntryCacheKey = new CacheRegionKey(CACHE_REGION_QNAME, Long.valueOf(0));
            lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheKey);

            Assert.assertNotNull("Failed lookup by ID should have added a sentinel cache entry", lowLevelCacheValue);
            Assert.assertEquals("Failed lookup by ID should have added a sentinel cache entry", VALUE_NOT_FOUND,
                    lowLevelCacheValue.getValue());

            final QName folderQName = ContentModel.TYPE_FOLDER;
            final QName prefixedFolderQName = folderQName.getPrefixedQName(nsPrefixResolver);

            // lookup by regular QName
            entryPair = qnameCache.getByValue(folderQName);
            Assert.assertNull(entryPair);

            Assert.assertEquals("Failed lookup by regular QName should have added a sentinel cache entry", 2, igniteCache.size());

            lowLevelEntryCacheValueKey = new CacheRegionValueKey(CACHE_REGION_QNAME, folderQName);
            lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheValueKey);

            Assert.assertNotNull(
                    "Failed lookup by regular QName should have added a sentinel value-key cache entry resolveable by regular QName",
                    lowLevelCacheValue);
            Assert.assertEquals(
                    "Failed lookup by regular QName should have added a sentinel value-key cache entry resolveable by regular QName",
                    VALUE_NOT_FOUND, lowLevelCacheValue.getValue());

            lowLevelEntryCacheValueKey = new CacheRegionValueKey(CACHE_REGION_QNAME, prefixedFolderQName);
            lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheValueKey);

            Assert.assertNotNull(
                    "Failed lookup by regular QName should have added a sentinel value-key cache entry resolveable by prefixed QName",
                    lowLevelCacheValue);
            Assert.assertEquals(
                    "Failed lookup by regular QName should have added a sentinel value-key cache entry resolveable by prefixed QName",
                    VALUE_NOT_FOUND, lowLevelCacheValue.getValue());

            // lookup by prefixed QName (would typically be done with QName provided e.g. in ReST request)
            entryPair = qnameCache.getByValue(prefixedFolderQName);
            Assert.assertNull(entryPair);

            Assert.assertEquals("Failed lookup by prefixed QName should not have added another sentinel cache entry", 2,
                    igniteCache.size());

            // create by prefixed QName - QName is identical according to hashCode + equals, but differs in actual internal state
            entryPair = qnameCache.getOrCreateByValue(prefixedFolderQName);
            Assert.assertNotNull(entryPair);
            Assert.assertEquals(Long.valueOf(0), entryPair.getFirst());
            Assert.assertEquals(folderQName, entryPair.getSecond());

            Assert.assertEquals("Creation should not have added new entries, instead replaced sentinel cache entries", 2,
                    igniteCache.size());

            // lookup by regular QName
            entryPair = qnameCache.getByValue(folderQName);

            Assert.assertNotNull("Lookup by regular QName should yield same cache entry created with prefixed QName", entryPair);
            Assert.assertEquals("Lookup by regular QName should yield same cache entry created with prefixed QName", Long.valueOf(0),
                    entryPair.getFirst());
            Assert.assertEquals("Lookup by regular QName should yield same cache entry created with prefixed QName", folderQName,
                    entryPair.getSecond());

            // lookup by prefixed QName (would typically done with QName provided e.g. in ReST request)
            entryPair = qnameCache.getByValue(prefixedFolderQName);

            Assert.assertNotNull("Lookup by prefixed QName should yield same cache entry created with prefixed QName", entryPair);
            Assert.assertEquals("Lookup by prefixed QName should yield same cache entry created with prefixed QName", Long.valueOf(0),
                    entryPair.getFirst());
            Assert.assertEquals("Lookup by prefixed QName should yield same cache entry created with prefixed QName", folderQName,
                    entryPair.getSecond());

            lowLevelEntryCacheKey = new CacheRegionKey(CACHE_REGION_QNAME, Long.valueOf(0));
            lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheKey);

            Assert.assertNotNull("Value creation should have added a cache entry resolveable by ID", lowLevelCacheValue);
            Assert.assertEquals("Value creation should have added a cache entry resolveable by ID",
                    folderQName,
                    lowLevelCacheValue.getValue());

            lowLevelEntryCacheValueKey = new CacheRegionValueKey(CACHE_REGION_QNAME, folderQName);
            lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheValueKey);

            Assert.assertNotNull("Value creation should have added a value-key cache entry resolveable by regular QName",
                    lowLevelCacheValue);
            Assert.assertEquals("Value creation should have added a value-key cache entry resolveable by regular QName", Long.valueOf(0),
                    lowLevelCacheValue.getValue());

            lowLevelEntryCacheValueKey = new CacheRegionValueKey(CACHE_REGION_QNAME, prefixedFolderQName);
            lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheValueKey);

            Assert.assertNotNull("Value creation should have added a value-key cache entry resolveable by prefixed QName",
                    lowLevelCacheValue);
            Assert.assertEquals("Value creation should have added a value-key cache entry resolveable by prefixed QName", Long.valueOf(0),
                    lowLevelCacheValue.getValue());

            Thread.sleep(100);

            Assert.assertTrue("Invalidating cache should have sent invalidation for value-key cache entry",
                    invalidatedKeys.contains(lowLevelEntryCacheValueKey));

            invalidatedKeys.clear();

            final Map<QName, Long> keysByMustExistQName = new HashMap<>();
            keysByMustExistQName.put(folderQName, Long.valueOf(0));

            final Set<QName> mustNotExistQNames = new HashSet<>(CONTENT_MODEL_QNAMES);
            mustNotExistQNames.remove(folderQName);

            // multiple iterations of looking up QNames, creating some randomly, and verifying cache state

            int iterationNo = 1;
            boolean lastIteration = false;

            // ensure consistent behaviour for reproduction / analysis
            final SecureRandom rnJesus = new SecureRandom(this.getClass().getName().getBytes(StandardCharsets.UTF_8));

            while (lastIteration || !mustNotExistQNames.isEmpty())
            {
                if (iterationNo % 10 == 1)
                {
                    LOGGER.info(
                            "Test 'qnameCacheBackedByImmutableEntityCache': Starting iteration {} with {} QName instances yet to be created",
                            iterationNo, mustNotExistQNames.size());
                }

                int qnamesCreated = 0;

                for (final QName qname : CONTENT_MODEL_QNAMES)
                {
                    // since Alfresco always creates new instances when resolving from String we do too for added realism
                    final QName effectiveQName = QName.createQName(qname.getNamespaceURI(), qname.getLocalName());
                    entryPair = qnameCache.getByValue(effectiveQName);

                    if (mustNotExistQNames.contains(qname))
                    {
                        Assert.assertNull(entryPair);

                        lowLevelEntryCacheValueKey = new CacheRegionValueKey(CACHE_REGION_QNAME, effectiveQName);
                        lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheValueKey);

                        Assert.assertNotNull("Low-level cache should contain a sentinel value-key cache entry after a failed lookup",
                                lowLevelCacheValue);
                        Assert.assertEquals("Low-level cache should contain a sentinel value-key cache entry after a failed lookup",
                                VALUE_NOT_FOUND, lowLevelCacheValue.getValue());

                        if (iterationNo != 1)
                        {
                            // 10% chance for the first QName being created per iteration
                            // each successive QName has 1/10th the chance of the previous
                            // this should stretch out QName creation over many iterations
                            final double rndValue = Math.pow(rnJesus.nextDouble(), qnamesCreated + 1);
                            if (rndValue >= 0.90)
                            {
                                entryPair = qnameCache.getOrCreateByValue(effectiveQName);

                                Assert.assertNotNull(entryPair);

                                lowLevelEntryCacheKey = new CacheRegionKey(CACHE_REGION_QNAME, entryPair.getFirst());
                                lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheKey);

                                Assert.assertNotNull("Value creation should have added a cache entry", lowLevelCacheValue);
                                Assert.assertEquals("Value creation should have added a cache entry", qname, lowLevelCacheValue.getValue());

                                lowLevelEntryCacheValueKey = new CacheRegionValueKey(CACHE_REGION_QNAME, effectiveQName);
                                lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheValueKey);

                                Assert.assertNotNull("Value creation should have added a value-key cache entry", lowLevelCacheValue);
                                Assert.assertEquals("Value creation should have added a value-key cache entry", entryPair.getFirst(),
                                        lowLevelCacheValue.getValue());

                                Thread.sleep(100);

                                Assert.assertTrue("Invalidating cache should have sent invalidation for value-key cache entry",
                                        invalidatedKeys.contains(lowLevelEntryCacheValueKey));

                                keysByMustExistQName.put(qname, entryPair.getFirst());
                                mustNotExistQNames.remove(qname);

                                qnamesCreated++;
                            }
                        }
                    }
                    else
                    {
                        Assert.assertNotNull(entryPair);

                        final Long expectedKey = keysByMustExistQName.get(qname);
                        Assert.assertEquals(expectedKey, entryPair.getFirst());

                        lowLevelEntryCacheValueKey = new CacheRegionValueKey(CACHE_REGION_QNAME, effectiveQName);
                        lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheValueKey);

                        Assert.assertNotNull("Low-level cache should contain a proper value-key cache entry after a successfull lookup",
                                lowLevelCacheValue);
                        Assert.assertEquals("Low-level cache should contain a proper value-key cache entry after a successfull lookup",
                                entryPair.getFirst(), lowLevelCacheValue.getValue());

                        lowLevelEntryCacheKey = new CacheRegionKey(CACHE_REGION_QNAME, entryPair.getFirst());
                        lowLevelCacheValue = igniteCache.get(lowLevelEntryCacheKey);

                        Assert.assertNotNull("Low-level cache should contain a proper cache entry for value previously created",
                                lowLevelCacheValue);
                        Assert.assertEquals("Low-level cache should contain a proper cache entry for value previously created",
                                qname,
                                lowLevelCacheValue.getValue());
                    }
                }

                Assert.assertEquals(qnamesCreated, invalidatedKeys.size());

                if (!lastIteration)
                {
                    Thread.sleep(200);

                    invalidatedKeys.clear();
                }

                lastIteration = !lastIteration && mustNotExistQNames.isEmpty();
                iterationNo++;
            }

            LOGGER.info("Test 'qnameCacheBackedByImmutableEntityCache': {} iterations resulted in {} lookups, {} puts, {} removes",
                    iterationNo - 1, igniteCache.metrics().getCacheGets(), igniteCache.metrics().getCachePuts(),
                    igniteCache.metrics().getCacheRemovals());
        }
        catch (final Exception ex)
        {
            LOGGER.error("Test 'qnameCacheBackedByImmutableEntityCache' failed", ex);
            throw ex;
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    /**
     *
     * @author Axel Faust
     */
    protected static class TestQNameCallbackDAO extends EntityLookupCallbackDAOAdaptor<Long, QName, QName>
    {

        private final QNameDAO qnameDAO;

        protected TestQNameCallbackDAO(final QNameDAO qnameDAO)
        {
            this.qnameDAO = qnameDAO;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public QName getValueKey(final QName value)
        {
            return value;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, QName> findByKey(final Long key)
        {
            return this.qnameDAO.getQName(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, QName> findByValue(final QName value)
        {
            return this.qnameDAO.getQName(value);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, QName> createValue(final QName value)
        {
            // make sure the namespace exists
            this.qnameDAO.getOrCreateNamespace(value.getNamespaceURI());
            return this.qnameDAO.getOrCreateQName(value);
        }

    }

    protected static class TestNamespacePrefixResolver implements NamespacePrefixResolver
    {

        @Override
        public String getNamespaceURI(final String prefix) throws NamespaceException
        {
            switch (prefix)
            {
                case NamespaceService.ALFRESCO_PREFIX:
                    return NamespaceService.ALFRESCO_URI;
                case NamespaceService.CONTENT_MODEL_PREFIX:
                    return NamespaceService.CONTENT_MODEL_1_0_URI;
                case NamespaceService.SYSTEM_MODEL_PREFIX:
                    return NamespaceService.SYSTEM_MODEL_1_0_URI;
            }
            throw new NamespaceException("Prefix " + prefix + " not registered");
        }

        @Override
        public Collection<String> getPrefixes(final String namespaceURI) throws NamespaceException
        {
            switch (namespaceURI)
            {
                case NamespaceService.ALFRESCO_URI:
                    return Collections.singleton(NamespaceService.ALFRESCO_PREFIX);
                case NamespaceService.CONTENT_MODEL_1_0_URI:
                    return Collections.singleton(NamespaceService.CONTENT_MODEL_PREFIX);
                case NamespaceService.SYSTEM_MODEL_1_0_URI:
                    return Collections.singleton(NamespaceService.SYSTEM_MODEL_PREFIX);
            }
            throw new NamespaceException("URI " + namespaceURI + " not registered");
        }

        @Override
        public Collection<String> getPrefixes()
        {
            return new HashSet<>(Arrays.asList(NamespaceService.ALFRESCO_PREFIX, NamespaceService.CONTENT_MODEL_PREFIX,
                    NamespaceService.SYSTEM_MODEL_PREFIX));
        }

        @Override
        public Collection<String> getURIs()
        {
            return new HashSet<>(Arrays.asList(NamespaceService.ALFRESCO_URI, NamespaceService.CONTENT_MODEL_1_0_URI,
                    NamespaceService.SYSTEM_MODEL_1_0_URI));
        }

    }

    /**
     *
     * @author Axel Faust
     */
    protected static class TestQNameDAO implements QNameDAO
    {

        private final Map<Long, String> namespaceById = new HashMap<>();

        private final Map<String, Long> idByNamespace = new HashMap<>();

        private final Map<Long, QName> qnameById = new HashMap<>();

        private final Map<QName, Long> idByQName = new HashMap<>();

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, String> getNamespace(final Long id)
        {
            final String namespaceUri = this.namespaceById.get(id);
            return namespaceUri != null ? new Pair<>(id, namespaceUri) : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, String> getNamespace(final String namespaceUri)
        {
            final Long id = this.idByNamespace.get(namespaceUri);
            return id != null ? new Pair<>(id, namespaceUri) : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, String> getOrCreateNamespace(final String namespaceUri)
        {
            Pair<Long, String> pair = this.getNamespace(namespaceUri);
            if (pair == null)
            {
                final Long id = Long.valueOf(this.namespaceById.size());
                this.namespaceById.put(id, namespaceUri);
                this.idByNamespace.put(namespaceUri, id);
                pair = new Pair<>(id, namespaceUri);
            }
            return pair;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void updateNamespace(final String oldNamespaceUri, final String newNamespaceUri)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, QName> getQName(final Long id)
        {
            final QName qname = this.qnameById.get(id);
            return qname != null ? new Pair<>(id, qname) : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, QName> getQName(final QName qname)
        {
            final Long id = this.idByQName.get(qname);
            return id != null ? new Pair<>(id, qname) : null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, QName> getOrCreateQName(final QName qname)
        {
            Pair<Long, QName> pair = this.getQName(qname);
            if (pair == null)
            {
                final Long id = Long.valueOf(this.qnameById.size());
                this.qnameById.put(id, qname);
                this.idByQName.put(qname, id);
                pair = new Pair<>(id, qname);
            }
            return pair;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Pair<Long, QName> updateQName(final QName qnameOld, final QName qnameNew)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void deleteQName(final QName qname)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Set<QName> convertIdsToQNames(final Set<Long> ids)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Map<QName, ? extends Object> convertIdMapToQNameMap(final Map<Long, ? extends Object> idMap)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Set<Long> convertQNamesToIds(final Set<QName> qnames, final boolean create)
        {
            throw new UnsupportedOperationException();
        }

    }
}
