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
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.UUID;

import org.aldica.common.ignite.GridTestsBase;
import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.repo.domain.contentdata.ContentUrlKeyEntity;
import org.alfresco.util.GUID;
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
public class ContentUrlEntityBinarySerializerTests extends GridTestsBase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentUrlEntityBinarySerializerTests.class);

    private static final String[] ALGORITHMS = { "AES", "DES" };

    protected static IgniteConfiguration createConfiguration(final boolean serialForm, final String... regionNames)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForContentUrlEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForContentUrlEntity.setTypeName(ContentUrlEntity.class.getName());
        final ContentUrlEntityBinarySerializer contentUrlEntitySerializer = new ContentUrlEntityBinarySerializer();
        contentUrlEntitySerializer.setUseRawSerialForm(serialForm);
        binaryTypeConfigurationForContentUrlEntity.setSerializer(contentUrlEntitySerializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForContentUrlEntity));
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

        final IgniteConfiguration defaultConf = createConfiguration(false, "comparison1");
        referenceConf.setDataStorageConfiguration(defaultConf.getDataStorageConfiguration());

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite defaultGrid = Ignition.start(defaultConf);

            final CacheConfiguration<Long, ContentUrlEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("comparison1");
            cacheConfig.setDataRegionName("comparison1");
            final IgniteCache<Long, ContentUrlEntity> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, ContentUrlEntity> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

            // inlining of content url key (if encrypted) - 8
            this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica optimised", "Ignite default", 0.08);
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
        final IgniteConfiguration referenceConf = createConfiguration(false, "comparison1");
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");

        final IgniteConfiguration defaultConf = createConfiguration(true, "comparison1");
        referenceConf.setDataStorageConfiguration(defaultConf.getDataStorageConfiguration());

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite defaultGrid = Ignition.start(defaultConf);

            final CacheConfiguration<Long, ContentUrlEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setCacheMode(CacheMode.REPLICATED);

            cacheConfig.setName("comparison1");
            cacheConfig.setDataRegionName("comparison1");
            final IgniteCache<Long, ContentUrlEntity> referenceCache1 = referenceGrid.getOrCreateCache(cacheConfig);
            final IgniteCache<Long, ContentUrlEntity> cache1 = defaultGrid.getOrCreateCache(cacheConfig);

            // overhead reduction and better numeric handling - 8%
            this.efficiencyImpl(referenceGrid, defaultGrid, referenceCache1, cache1, "aldica raw serial", "aldica optimised", 0.08);
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
            final CacheConfiguration<Long, ContentUrlEntity> cacheConfig = new CacheConfiguration<>();
            cacheConfig.setName("contentUrlEntity");
            cacheConfig.setCacheMode(CacheMode.REPLICATED);
            final IgniteCache<Long, ContentUrlEntity> cache = grid.getOrCreateCache(cacheConfig);

            ContentUrlEntity controlValue;
            ContentUrlEntity cacheValue;

            controlValue = new ContentUrlEntity();

            controlValue.setId(987654l);
            controlValue.setContentUrl(createNewFileStoreUrl(0));
            controlValue.setContentUrlShort(controlValue.getContentUrlShort().toLowerCase());
            controlValue.setSize(0);
            controlValue.setOrphanTime(System.currentTimeMillis());

            cache.put(1l, controlValue);

            cacheValue = cache.get(1l);

            // only shallow equals() in ContentUrlEntity
            // we do a deep check ourselves
            Assert.assertNotSame(controlValue, cacheValue);
            Assert.assertEquals(controlValue.getId(), cacheValue.getId());
            Assert.assertEquals(controlValue.getContentUrl(), cacheValue.getContentUrl());
            Assert.assertEquals(controlValue.getContentUrlCrc(), cacheValue.getContentUrlCrc());
            Assert.assertEquals(controlValue.getContentUrlShort(), cacheValue.getContentUrlShort());
            Assert.assertEquals(controlValue.getSize(), cacheValue.getSize());
            Assert.assertEquals(controlValue.getOrphanTime(), cacheValue.getOrphanTime());
            Assert.assertNull(cacheValue.getContentUrlKey());

            controlValue = new ContentUrlEntity();

            controlValue.setId(-123456789l);
            controlValue.setContentUrl(createNewFileStoreUrl(0));
            controlValue.setContentUrlShort(controlValue.getContentUrlShort().toLowerCase());
            controlValue.setSize(123456789l);
            controlValue.setOrphanTime(System.currentTimeMillis());
            
            ContentUrlKeyEntity controlContentUrlKey = new ContentUrlKeyEntity();
            controlContentUrlKey.setId(Long.MAX_VALUE);
            controlContentUrlKey.setContentUrlId(controlValue.getId());
            byte[] bytes = new byte[512];
            final SecureRandom rnJesus = new SecureRandom();
            rnJesus.nextBytes(bytes);
            controlContentUrlKey.setEncryptedKeyAsBytes(bytes);
            controlContentUrlKey.setKeySize(bytes.length);
            controlContentUrlKey.setAlgorithm("AES");
            controlContentUrlKey.setMasterKeystoreId(UUID.randomUUID().toString());
            controlContentUrlKey.setMasterKeyAlias(UUID.randomUUID().toString());
            controlContentUrlKey.setUnencryptedFileSize(controlValue.getSize() - rnJesus.nextInt(100));
            controlValue.setContentUrlKey(controlContentUrlKey);

            cache.put(2l, controlValue);

            cacheValue = cache.get(2l);

            // only shallow equals() in ContentUrlEntity
            // we do a deep check ourselves
            Assert.assertNotSame(controlValue, cacheValue);
            Assert.assertEquals(controlValue.getId(), cacheValue.getId());
            Assert.assertEquals(controlValue.getContentUrl(), cacheValue.getContentUrl());
            Assert.assertEquals(controlValue.getContentUrlCrc(), cacheValue.getContentUrlCrc());
            Assert.assertEquals(controlValue.getContentUrlShort(), cacheValue.getContentUrlShort());
            Assert.assertEquals(controlValue.getSize(), cacheValue.getSize());
            Assert.assertEquals(controlValue.getOrphanTime(), cacheValue.getOrphanTime());
            
            ContentUrlKeyEntity cacheContentUrlKey = cacheValue.getContentUrlKey();
            Assert.assertNotNull(cacheContentUrlKey);

            Assert.assertEquals(controlContentUrlKey.getId(), cacheContentUrlKey.getId());
            Assert.assertEquals(controlContentUrlKey.getContentUrlId(), cacheContentUrlKey.getContentUrlId());
            Assert.assertArrayEquals(controlContentUrlKey.getEncryptedKeyAsBytes(), cacheContentUrlKey.getEncryptedKeyAsBytes());
            Assert.assertEquals(controlContentUrlKey.getKeySize(), cacheContentUrlKey.getKeySize());
            Assert.assertEquals(controlContentUrlKey.getAlgorithm(), cacheContentUrlKey.getAlgorithm());
            Assert.assertEquals(controlContentUrlKey.getMasterKeystoreId(), cacheContentUrlKey.getMasterKeystoreId());
            Assert.assertEquals(controlContentUrlKey.getMasterKeyAlias(), cacheContentUrlKey.getMasterKeyAlias());
            Assert.assertEquals(controlContentUrlKey.getUnencryptedFileSize(), cacheContentUrlKey.getUnencryptedFileSize());
        }
    }

    @SuppressWarnings("deprecation")
    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final IgniteCache<Long, ContentUrlEntity> referenceCache,
            final IgniteCache<Long, ContentUrlEntity> cache, final String serialisationType, final String referenceSerialisationType,
            final double marginFraction)
    {
        LOGGER.info(
                "Running ContentUrlEntity serialisation benchmark of 100k instances, comparing {} vs. {} serialisation, expecting relative improvement margin / difference fraction of {}",
                referenceSerialisationType, serialisationType, marginFraction);

        final int msPerYear = 365 * 24 * 60 * 60 * 1000;
        final long msOffset = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();

        final SecureRandom rnJesus = new SecureRandom();
        for (int idx = 0; idx < 100000; idx++)
        {
            final ContentUrlEntity value = new ContentUrlEntity();

            value.setId(rnJesus.nextLong());
            value.setContentUrl(createNewFileStoreUrl(0));
            value.setContentUrlShort(value.getContentUrlShort().toLowerCase());
            value.setSize(rnJesus.nextLong());

            final int msOffsetOrphaned = rnJesus.nextInt(msPerYear / 2);
            value.setOrphanTime(msOffset + msOffsetOrphaned);

            if (rnJesus.nextBoolean())
            {
                // encrypted content URL
                ContentUrlKeyEntity contentUrlKey = new ContentUrlKeyEntity();
                contentUrlKey.setId(rnJesus.nextLong());
                contentUrlKey.setContentUrlId(value.getId());
                byte[] bytes = new byte[(1 + rnJesus.nextInt(3)) * 128];
                rnJesus.nextBytes(bytes);
                contentUrlKey.setEncryptedKeyAsBytes(bytes);
                contentUrlKey.setKeySize(bytes.length);
                contentUrlKey.setAlgorithm(ALGORITHMS[rnJesus.nextInt(ALGORITHMS.length)]);
                contentUrlKey.setMasterKeystoreId(UUID.randomUUID().toString());
                contentUrlKey.setMasterKeyAlias(UUID.randomUUID().toString());
                contentUrlKey.setUnencryptedFileSize(rnJesus.nextLong());
                value.setContentUrlKey(contentUrlKey);
            }

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

    private static String createTimeBasedPath(int bucketsPerMinute)
    {
        Calendar calendar = new GregorianCalendar();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1; // 0-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        // create the URL
        StringBuilder sb = new StringBuilder(20);
        sb.append(year).append('/').append(month).append('/').append(day).append('/').append(hour).append('/').append(minute).append('/');

        if (bucketsPerMinute != 0)
        {
            long seconds = System.currentTimeMillis() % (60 * 1000);
            int actualBucket = (int) seconds / ((60 * 1000) / bucketsPerMinute);
            sb.append(actualBucket).append('/');
        }
        // done
        return sb.toString();
    }

    private static String createNewFileStoreUrl(int minuteBucketCount)
    {
        StringBuilder sb = new StringBuilder(20);
        sb.append(FileContentStore.STORE_PROTOCOL);
        sb.append(ContentStore.PROTOCOL_DELIMITER);
        sb.append(createTimeBasedPath(minuteBucketCount));
        sb.append(GUID.generate()).append(".BIN");
        return sb.toString();
    }
}
