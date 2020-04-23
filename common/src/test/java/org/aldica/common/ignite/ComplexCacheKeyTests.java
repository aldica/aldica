/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;

import org.aldica.common.ignite.binary.SelectivelyReflectiveBinarySerializer;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.eviction.lru.LruEvictionPolicyFactory;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.junit.Assert;
import org.junit.Test;

/**
 * The tests in this class mostly exist to validate the known / expected behaviour of the default Ignite caches when it comes to handling
 * custom, complex cache keys.
 *
 * @author Axel Faust
 */
public class ComplexCacheKeyTests extends GridTestsBase
{

    /**
     * Instances of this cache key class use all members to calculate a hash code and have to equal in all fields to be considered
     * identical.
     *
     * @author Axel Faust
     */
    protected static class CacheKeyWithOnlyHashRelevantFields implements Serializable
    {

        private static final long serialVersionUID = -7547582421808635360L;

        private final String hashRelevant1;

        private final String hashRelevant2;

        protected CacheKeyWithOnlyHashRelevantFields(final String hashRelevant, final String hashRelevant2)
        {
            this.hashRelevant1 = hashRelevant;
            this.hashRelevant2 = hashRelevant2;
        }

        /**
         * @return the hashRelevant1
         */
        public String getHashRelevant1()
        {
            return this.hashRelevant1;
        }

        /**
         * @return the hashRelevant2
         */
        public String getHashRelevant2()
        {
            return this.hashRelevant2;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((this.hashRelevant1 == null) ? 0 : this.hashRelevant1.hashCode());
            result = prime * result + ((this.hashRelevant2 == null) ? 0 : this.hashRelevant2.hashCode());
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (this.getClass() != obj.getClass())
            {
                return false;
            }
            final CacheKeyWithOnlyHashRelevantFields other = (CacheKeyWithOnlyHashRelevantFields) obj;
            if (this.hashRelevant1 == null)
            {
                if (other.hashRelevant1 != null)
                {
                    return false;
                }
            }
            else if (!this.hashRelevant1.equals(other.hashRelevant1))
            {
                return false;
            }
            if (this.hashRelevant2 == null)
            {
                if (other.hashRelevant2 != null)
                {
                    return false;
                }
            }
            else if (!this.hashRelevant2.equals(other.hashRelevant2))
            {
                return false;
            }
            return true;
        }
    }

    /**
     * Instances of this cache key class use only one member to calculate a hash code and only have to equal in that field to be considered
     * identical. Thus multiple instances with different values for the non-relevant member can map to the same value if used in maps etc.
     *
     * @author Axel Faust
     */
    protected static class CacheKeyWithNonHashRelevantFields implements Serializable
    {

        private static final long serialVersionUID = -2814524389206953667L;

        private final String hashRelevant;

        private final String nonHashRelevant;

        protected CacheKeyWithNonHashRelevantFields(final String hashRelevant, final String nonHashRelevant)
        {
            this.hashRelevant = hashRelevant;
            this.nonHashRelevant = nonHashRelevant;
        }

        /**
         * @return the hashRelevant
         */
        public String getHashRelevant()
        {
            return this.hashRelevant;
        }

        /**
         * @return the nonHashRelevant
         */
        public String getNonHashRelevant()
        {
            return this.nonHashRelevant;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((this.hashRelevant == null) ? 0 : this.hashRelevant.hashCode());
            return result;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (this.getClass() != obj.getClass())
            {
                return false;
            }
            final CacheKeyWithNonHashRelevantFields other = (CacheKeyWithNonHashRelevantFields) obj;
            if (this.hashRelevant == null)
            {
                if (other.hashRelevant != null)
                {
                    return false;
                }
            }
            else if (!this.hashRelevant.equals(other.hashRelevant))
            {
                return false;
            }
            return true;
        }
    }

    @Test
    public void simpleCacheKeysOnHeap()
    {
        // base line test for trivial cache keys
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<String, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final LruEvictionPolicyFactory<String, String> evictionPolicyFactory = new LruEvictionPolicyFactory<>(1000);
            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCache<String, String> cache = grid.getOrCreateCache(cacheConfig);

            final String baseKey = "key1";
            final String baseValue = "value1";

            cache.put(baseKey, baseValue);

            String key = baseKey;
            String value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance
            key = new String(baseKey);
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            key = baseKey + "Suffix";
            value = cache.get(key);

            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void simpleCacheKeysOffHeap()
    {
        // base line test for trivial cache keys
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<String, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final Ignite grid = Ignition.start(conf);

            final IgniteCache<String, String> cache = grid.getOrCreateCache(cacheConfig);

            final String baseKey = "key1";
            final String baseValue = "value1";

            cache.put(baseKey, baseValue);

            String key = baseKey;
            String value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance
            key = new String(baseKey);
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            key = baseKey + "Suffix";
            value = cache.get(key);

            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void complexKeyWithAllRelevantFieldsOnHeap()
    {
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<CacheKeyWithOnlyHashRelevantFields, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final LruEvictionPolicyFactory<CacheKeyWithOnlyHashRelevantFields, String> evictionPolicyFactory = new LruEvictionPolicyFactory<>(
                    1000);
            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCache<CacheKeyWithOnlyHashRelevantFields, String> cache = grid.getOrCreateCache(cacheConfig);

            final CacheKeyWithOnlyHashRelevantFields baseKey = new CacheKeyWithOnlyHashRelevantFields("keyPart1", "keyPart2");
            final String baseValue = "value1";

            cache.put(baseKey, baseValue);

            CacheKeyWithOnlyHashRelevantFields key = baseKey;
            String value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            key = new CacheKeyWithOnlyHashRelevantFields(baseKey.getHashRelevant1(), baseKey.getHashRelevant2());
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            key = new CacheKeyWithOnlyHashRelevantFields(baseKey.getHashRelevant1(), "keyPartX");
            value = cache.get(key);

            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void complexKeyWithAllRelevantFieldsOffHeap()
    {
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<CacheKeyWithOnlyHashRelevantFields, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final Ignite grid = Ignition.start(conf);

            final IgniteCache<CacheKeyWithOnlyHashRelevantFields, String> cache = grid.getOrCreateCache(cacheConfig);

            final CacheKeyWithOnlyHashRelevantFields baseKey = new CacheKeyWithOnlyHashRelevantFields("keyPart1", "keyPart2");
            final String baseValue = "value1";

            cache.put(baseKey, baseValue);

            CacheKeyWithOnlyHashRelevantFields key = baseKey;
            String value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            key = new CacheKeyWithOnlyHashRelevantFields(baseKey.getHashRelevant1(), baseKey.getHashRelevant2());
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            key = new CacheKeyWithOnlyHashRelevantFields(baseKey.getHashRelevant1(), "keyPartX");
            value = cache.get(key);

            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void complexKeyWithNonRelevantFieldsOnHeap()
    {
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<CacheKeyWithNonHashRelevantFields, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final LruEvictionPolicyFactory<CacheKeyWithNonHashRelevantFields, String> evictionPolicyFactory = new LruEvictionPolicyFactory<>(
                    1000);
            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCache<CacheKeyWithNonHashRelevantFields, String> cache = grid.getOrCreateCache(cacheConfig);

            final CacheKeyWithNonHashRelevantFields baseKey = new CacheKeyWithNonHashRelevantFields("keyPart1", "keyPart2");
            final String baseValue = "value1";

            cache.put(baseKey, baseValue);

            CacheKeyWithNonHashRelevantFields key = baseKey;
            String value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), baseKey.getNonHashRelevant());
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance with different non-hash relevant component
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), "keyPartX");
            value = cache.get(key);

            // default Ignite binary identity resolver considers non-hash relevant component as relevant
            // so cache miss is expected
            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);

            // force new instance with non-hash relevant component set to no specific value
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), null);
            value = cache.get(key);

            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);

            key = new CacheKeyWithNonHashRelevantFields("keyPartX", baseKey.getNonHashRelevant());
            value = cache.get(key);

            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void complexKeyWithNonRelevantFieldsCustomSerialiserOnHeap()
    {
        try
        {
            final SelectivelyReflectiveBinarySerializer serializer = new SelectivelyReflectiveBinarySerializer();
            serializer.setRelevantFieldsProvider(
                    cls -> cls.equals(CacheKeyWithNonHashRelevantFields.class) ? Arrays.asList("hashRelevant") : Collections.emptyList());

            final IgniteConfiguration conf = createConfiguration(1, false);
            final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();
            final BinaryTypeConfiguration binaryTypeConfigurationForKeyClass = new BinaryTypeConfiguration();
            binaryTypeConfigurationForKeyClass.setTypeName(CacheKeyWithNonHashRelevantFields.class.getName());
            binaryTypeConfigurationForKeyClass.setSerializer(serializer);
            binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForKeyClass));
            conf.setBinaryConfiguration(binaryConfiguration);

            final CacheConfiguration<CacheKeyWithNonHashRelevantFields, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final LruEvictionPolicyFactory<CacheKeyWithNonHashRelevantFields, String> evictionPolicyFactory = new LruEvictionPolicyFactory<>(
                    1000);
            cacheConfig.setOnheapCacheEnabled(true);
            cacheConfig.setEvictionPolicyFactory(evictionPolicyFactory);

            final Ignite grid = Ignition.start(conf);

            final IgniteCache<CacheKeyWithNonHashRelevantFields, String> cache = grid.getOrCreateCache(cacheConfig);

            final CacheKeyWithNonHashRelevantFields baseKey = new CacheKeyWithNonHashRelevantFields("keyPart1", "keyPart2");
            final String baseValue = "value1";

            cache.put(baseKey, baseValue);

            CacheKeyWithNonHashRelevantFields key = baseKey;
            String value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), baseKey.getNonHashRelevant());
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance with different non-hash relevant component
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), "keyPartX");
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance with non-hash relevant component set to no specific value
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), null);
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            key = new CacheKeyWithNonHashRelevantFields("keyPartX", baseKey.getNonHashRelevant());
            value = cache.get(key);

            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void complexKeyWithNonRelevantFieldsOffHeap()
    {
        try
        {
            final IgniteConfiguration conf = createConfiguration(1, false);

            final CacheConfiguration<CacheKeyWithNonHashRelevantFields, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final Ignite grid = Ignition.start(conf);

            final IgniteCache<CacheKeyWithNonHashRelevantFields, String> cache = grid.getOrCreateCache(cacheConfig);

            final CacheKeyWithNonHashRelevantFields baseKey = new CacheKeyWithNonHashRelevantFields("keyPart1", "keyPart2");
            final String baseValue = "value1";

            cache.put(baseKey, baseValue);

            CacheKeyWithNonHashRelevantFields key = baseKey;
            String value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), baseKey.getNonHashRelevant());
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance with different non-hash relevant component
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), "keyPartX");
            value = cache.get(key);

            // default Ignite binary identity resolver considers non-hash relevant component as relevant
            // so cache miss is expected
            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);

            // force new instance with non-hash relevant component set to no specific value
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), null);
            value = cache.get(key);

            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);

            key = new CacheKeyWithNonHashRelevantFields("keyPartX", baseKey.getNonHashRelevant());
            value = cache.get(key);

            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void complexKeyWithNonRelevantFieldsCustomSerialiserOffHeap()
    {
        try
        {
            final SelectivelyReflectiveBinarySerializer serializer = new SelectivelyReflectiveBinarySerializer();
            serializer.setRelevantFieldsProvider(
                    cls -> cls.equals(CacheKeyWithNonHashRelevantFields.class) ? Arrays.asList("hashRelevant") : Collections.emptyList());

            final IgniteConfiguration conf = createConfiguration(1, false);
            final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();
            final BinaryTypeConfiguration binaryTypeConfigurationForKeyClass = new BinaryTypeConfiguration();
            binaryTypeConfigurationForKeyClass.setTypeName(CacheKeyWithNonHashRelevantFields.class.getName());
            binaryTypeConfigurationForKeyClass.setSerializer(serializer);
            binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForKeyClass));
            conf.setBinaryConfiguration(binaryConfiguration);

            final CacheConfiguration<CacheKeyWithNonHashRelevantFields, String> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("testCache");
            cacheConfig.setCacheMode(CacheMode.LOCAL);

            final Ignite grid = Ignition.start(conf);

            final IgniteCache<CacheKeyWithNonHashRelevantFields, String> cache = grid.getOrCreateCache(cacheConfig);

            final CacheKeyWithNonHashRelevantFields baseKey = new CacheKeyWithNonHashRelevantFields("keyPart1", "keyPart2");
            final String baseValue = "value1";

            cache.put(baseKey, baseValue);

            CacheKeyWithNonHashRelevantFields key = baseKey;
            String value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), baseKey.getNonHashRelevant());
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance with different non-hash relevant component
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), "keyPartX");
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            // force new instance with non-hash relevant component set to no specific value
            key = new CacheKeyWithNonHashRelevantFields(baseKey.getHashRelevant(), null);
            value = cache.get(key);

            Assert.assertEquals(baseValue, value);

            key = new CacheKeyWithNonHashRelevantFields("keyPartX", baseKey.getNonHashRelevant());
            value = cache.get(key);

            Assert.assertNotEquals(baseValue, value);
            Assert.assertNull(value);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }
}
