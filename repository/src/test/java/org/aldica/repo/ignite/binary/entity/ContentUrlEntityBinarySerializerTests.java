/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.repo.content.filestore.FileContentUrlProvider;
import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.repo.domain.contentdata.ContentUrlKeyEntity;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.marshaller.Marshaller;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Axel Faust
 */
public class ContentUrlEntityBinarySerializerTests extends GridTestsBase
{

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForContentUrlEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForContentUrlEntity.setTypeName(ContentUrlEntity.class.getName());
        final ContentUrlEntityBinarySerializer serializer = new ContentUrlEntityBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        serializer.setUseOptimisedContentURL(serialForm);
        binaryTypeConfigurationForContentUrlEntity.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForContentUrlEntity));
        conf.setBinaryConfiguration(binaryConfiguration);

        return conf;
    }

    @Test
    public void defaultFormCorrectness()
    {
        final IgniteConfiguration conf = createConfiguration(false);
        this.correctnessImpl(conf);
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void defaultFormEfficiency()
    {
        final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(false);

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            // we avoid having secondary fields in the serial form, so quite a bit of difference - 20%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", false, 0.20);

            // savings are negligible due to high cost of key payload - 4%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", true, 0.04);
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

    @Category(ExpensiveTestCategory.class)
    @Test
    public void rawSerialFormEfficiency()
    {
        final IgniteConfiguration referenceConf = createConfiguration(false);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(true);

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            // we optimise serial form for most value components, making a significant difference - 46%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", false, 0.46);

            // savings are negligible due to high cost of key payload - 7%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", true, 0.07);
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
            @SuppressWarnings("deprecation")
            final Marshaller marshaller = grid.configuration().getMarshaller();

            // default Alfresco classes are inaccessible (package-protected visibility)
            final FileContentUrlProvider urlProvider = () -> FileContentStore.STORE_PROTOCOL + "://" + UUID.randomUUID().toString();

            ContentUrlEntity controlValue;
            ContentUrlEntity serialisedValue;
            ContentUrlKeyEntity keyControlValue;
            ContentUrlKeyEntity keySerialisedValue;

            // most common case - unorphaned, unencrypted
            controlValue = new ContentUrlEntity();
            controlValue.setId(1l);
            controlValue.setContentUrl(urlProvider.createNewFileStoreUrl());
            controlValue.setSize(123l);
            controlValue.setOrphanTime(null);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getContentUrlShort(), serialisedValue.getContentUrlShort());
            Assert.assertEquals(controlValue.getContentUrlCrc(), serialisedValue.getContentUrlCrc());
            Assert.assertEquals(controlValue.getSize(), serialisedValue.getSize());
            Assert.assertNull(serialisedValue.getOrphanTime());
            Assert.assertNull(serialisedValue.getContentUrlKey());

            // second most common case - orphaned, unencrypted
            controlValue = new ContentUrlEntity();
            controlValue.setId(2l);
            controlValue.setContentUrl(urlProvider.createNewFileStoreUrl());
            controlValue.setSize(321l);
            controlValue.setOrphanTime(System.currentTimeMillis());

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getContentUrlShort(), serialisedValue.getContentUrlShort());
            Assert.assertEquals(controlValue.getContentUrlCrc(), serialisedValue.getContentUrlCrc());
            Assert.assertEquals(controlValue.getSize(), serialisedValue.getSize());
            Assert.assertEquals(controlValue.getOrphanTime(), serialisedValue.getOrphanTime());
            Assert.assertNull(serialisedValue.getContentUrlKey());

            // not sure when this case ever exists in Alfresco, but legally possible
            controlValue = new ContentUrlEntity();
            controlValue.setId(3l);
            // controlValue.setContentUrl(null); // null is default value and setter is not null-safe
            controlValue.setSize(999l);
            controlValue.setOrphanTime(null);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertNull(serialisedValue.getContentUrlShort());
            Assert.assertEquals(controlValue.getContentUrlCrc(), serialisedValue.getContentUrlCrc());
            Assert.assertEquals(controlValue.getSize(), serialisedValue.getSize());
            Assert.assertNull(serialisedValue.getOrphanTime());
            Assert.assertNull(serialisedValue.getContentUrlKey());

            // case which normally only exists for Enterprise, unorphaned, encrypted
            controlValue = new ContentUrlEntity();
            controlValue.setId(4l);
            controlValue.setContentUrl(urlProvider.createNewFileStoreUrl());
            controlValue.setSize(123456789l);
            controlValue.setOrphanTime(null);
            keyControlValue = new ContentUrlKeyEntity();
            keyControlValue.setId(1l);
            keyControlValue.setContentUrlId(4l);
            keyControlValue.setEncryptedKeyAsBytes(new byte[] { 123, -123, 15, -15, 64, -64, 0, 1, -1 });
            keyControlValue.setKeySize(1024);
            keyControlValue.setAlgorithm("MyTestAlgorithm");
            keyControlValue.setMasterKeystoreId("masterKeystore");
            keyControlValue.setMasterKeyAlias("masterAlias");
            keyControlValue.setUnencryptedFileSize(123456l);
            controlValue.setContentUrlKey(keyControlValue);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            keySerialisedValue = serialisedValue.getContentUrlKey();

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getContentUrlShort(), serialisedValue.getContentUrlShort());
            Assert.assertEquals(controlValue.getContentUrlCrc(), serialisedValue.getContentUrlCrc());
            Assert.assertEquals(controlValue.getSize(), serialisedValue.getSize());
            Assert.assertNull(serialisedValue.getOrphanTime());

            Assert.assertEquals(keyControlValue, keySerialisedValue);
            Assert.assertNotSame(keyControlValue, keySerialisedValue);
            // equals checks most, but not all
            Assert.assertEquals(keyControlValue.getContentUrlId(), keySerialisedValue.getContentUrlId());
            Assert.assertTrue(Arrays.equals(keyControlValue.getEncryptedKeyAsBytes(), keySerialisedValue.getEncryptedKeyAsBytes()));
            Assert.assertEquals(keyControlValue.getKeySize(), keySerialisedValue.getKeySize());
            Assert.assertEquals(keyControlValue.getUnencryptedFileSize(), keySerialisedValue.getUnencryptedFileSize());

            // case with non-default content URL - unorphaned, encrypted
            controlValue = new ContentUrlEntity();
            controlValue.setId(5l);
            controlValue.setContentUrl("my-store://path/to/file/with/weird.name");
            controlValue.setSize(987654321l);
            controlValue.setOrphanTime(null);
            keyControlValue = new ContentUrlKeyEntity();
            keyControlValue.setId(2l);
            keyControlValue.setContentUrlId(5l);
            keyControlValue.setEncryptedKeyAsBytes(new byte[] { 22, -99, 127, -67, 111, -97, 0, 69, -96 });
            keyControlValue.setKeySize(1024);
            keyControlValue.setAlgorithm("MyTestAlgorithm");
            keyControlValue.setMasterKeystoreId("masterKeystore");
            keyControlValue.setMasterKeyAlias("masterAlias");
            keyControlValue.setUnencryptedFileSize(null);
            controlValue.setContentUrlKey(keyControlValue);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            keySerialisedValue = serialisedValue.getContentUrlKey();

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getContentUrlShort(), serialisedValue.getContentUrlShort());
            Assert.assertEquals(controlValue.getContentUrlCrc(), serialisedValue.getContentUrlCrc());
            Assert.assertEquals(controlValue.getSize(), serialisedValue.getSize());
            Assert.assertNull(serialisedValue.getOrphanTime());

            Assert.assertEquals(keyControlValue, keySerialisedValue);
            Assert.assertNotSame(keyControlValue, keySerialisedValue);
            // equals checks most, but not all
            Assert.assertEquals(keyControlValue.getContentUrlId(), keySerialisedValue.getContentUrlId());
            Assert.assertTrue(Arrays.equals(keyControlValue.getEncryptedKeyAsBytes(), keySerialisedValue.getEncryptedKeyAsBytes()));
            Assert.assertEquals(keyControlValue.getKeySize(), keySerialisedValue.getKeySize());
            Assert.assertNull(keySerialisedValue.getUnencryptedFileSize());

            // weird case in default Alfresco where ID is not set despite being persisted in the DB
            controlValue = new ContentUrlEntity();
            controlValue.setId(null);
            // controlValue.setContentUrl(null); // null is default value and setter is not null-safe
            controlValue.setSize(999l);
            controlValue.setOrphanTime(null);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertNull(serialisedValue.getContentUrlShort());
            Assert.assertEquals(controlValue.getContentUrlCrc(), serialisedValue.getContentUrlCrc());
            Assert.assertEquals(controlValue.getSize(), serialisedValue.getSize());
            Assert.assertNull(serialisedValue.getOrphanTime());
            Assert.assertNull(serialisedValue.getContentUrlKey());
        }
        catch (final IgniteCheckedException ice)
        {
            throw new IllegalStateException("Serialisation failed in correctness test", ice);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final String serialisationType,
            final String referenceSerialisationType, final boolean withKeys, final double marginFraction)
    {
        // default Alfresco classes are inaccessible (package-protected visibility)
        final FileContentUrlProvider urlProvider = () -> FileContentStore.STORE_PROTOCOL + "://" + UUID.randomUUID().toString();

        final SecureRandom rnJesus = new SecureRandom();
        final long now = System.currentTimeMillis();

        final Supplier<ContentUrlEntity> comparisonValueSupplier = () -> {
            final int idx = 1000 + rnJesus.nextInt(100000000);
            final ContentUrlEntity value = new ContentUrlEntity();
            value.setId(Long.valueOf(idx));
            value.setContentUrl(urlProvider.createNewFileStoreUrl());
            value.setSize(10240l + rnJesus.nextInt(Integer.MAX_VALUE));
            value.setOrphanTime(now + rnJesus.nextInt(365 * 24 * 60 * 60 * 1000));

            if (withKeys)
            {
                final ContentUrlKeyEntity key = new ContentUrlKeyEntity();
                key.setId(value.getId());
                key.setContentUrlId(value.getId());

                final byte[] keyBytes = new byte[1024];
                rnJesus.nextBytes(keyBytes);
                key.setEncryptedKeyAsBytes(keyBytes);
                key.setKeySize(keyBytes.length);

                key.setAlgorithm("SHA0815");
                key.setMasterKeystoreId("masterKeystore");
                key.setMasterKeyAlias("masterKey");
                key.setUnencryptedFileSize(value.getSize() - rnJesus.nextInt(10240));

                value.setContentUrlKey(key);
            }

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, withKeys ? "ContentUrlEntityWithEncryptionKey" : "ContentUrlEntity",
                referenceSerialisationType, serialisationType, comparisonValueSupplier, marginFraction);
    }
}
