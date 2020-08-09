/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.support;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.repo.cache.lookup.CacheRegionKey;
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
public class CacheRegionKeyBinarySerializerTests extends GridTestsBase
{

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForCacheRegionKey = new BinaryTypeConfiguration();
        binaryTypeConfigurationForCacheRegionKey.setTypeName(CacheRegionKey.class.getName());
        final CacheRegionKeyBinarySerializer serializer = new CacheRegionKeyBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForCacheRegionKey.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForCacheRegionKey));
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

            // saving potential is substantial, but variable depending on region
            // expect average of 33%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0.33);
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

            // saving potential is limited - 21%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.21);
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

            CacheRegionKey controlValue;
            CacheRegionKey serialisedValue;

            // default region + String key
            controlValue = new CacheRegionKey(CacheRegion.DEFAULT.getCacheRegionName(), "value1");
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);

            // default region + Long key
            controlValue = new CacheRegionKey(CacheRegion.DEFAULT.getCacheRegionName(), Long.valueOf(1234));
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);

            // default region + arbitrary key
            controlValue = new CacheRegionKey(CacheRegion.DEFAULT.getCacheRegionName(), Instant.now());
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);

            // random region + String key
            controlValue = new CacheRegionKey(UUID.randomUUID().toString(), "value2");
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);

            // random region + Long key
            controlValue = new CacheRegionKey(UUID.randomUUID().toString(), Long.valueOf(1234));
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);

            // random region + arbitrary key
            controlValue = new CacheRegionKey(UUID.randomUUID().toString(), Instant.now());
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);
        }
        catch (final IgniteCheckedException ice)
        {
            throw new IllegalStateException("Serialisation failed in correctness test", ice);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final String serialisationType,
            final String referenceSerialisationType, final double marginFraction)
    {
        final CacheRegion[] regions = CacheRegion.values();
        final SecureRandom rnJesus = new SecureRandom();

        final Supplier<CacheRegionKey> comparisonValueSupplier = () -> {
            final String region = regions[rnJesus.nextInt(regions.length - 2)].getCacheRegionName();
            // 1 billion possible IDs is more than sufficient for testing - rarely seen in production, more in benchmarks
            final CacheRegionKey value = new CacheRegionKey(region, Long.valueOf(rnJesus.nextInt(1000000000)));

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "CacheRegionKey", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
