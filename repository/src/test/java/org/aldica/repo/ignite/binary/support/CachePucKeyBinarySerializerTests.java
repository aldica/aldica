/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.domain.propval.AbstractPropertyValueDAOImpl.CachePucKey;
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
public class CachePucKeyBinarySerializerTests extends GridTestsBase
{

    private static final Constructor<CachePucKey> CACHE_PUC_KEY_CTOR;

    static
    {
        try
        {
            CACHE_PUC_KEY_CTOR = CachePucKey.class.getDeclaredConstructor(Long.class, Long.class, Long.class);
            CACHE_PUC_KEY_CTOR.setAccessible(true);
        }
        catch (final NoSuchMethodException e)
        {
            throw new AlfrescoRuntimeException("Failed to lookup constructor");
        }
    }

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForCachePucKey = new BinaryTypeConfiguration();
        binaryTypeConfigurationForCachePucKey.setTypeName(CachePucKey.class.getName());
        final CachePucKeyBinarySerializer serializer = new CachePucKeyBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForCachePucKey.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForCachePucKey));
        conf.setBinaryConfiguration(binaryConfiguration);

        return conf;
    }

    @Test
    public void defaultFormCorrectness() throws Exception
    {
        final IgniteConfiguration conf = createConfiguration(false);
        this.correctnessImpl(conf);
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void defaultFormEfficiency() throws Exception
    {
        final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(false);

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            // slightly better performance due to exclusion of hashCode (reduced partially by extra flag) - 4.5%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0.045);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void rawSerialFormCorrectness() throws Exception
    {
        final IgniteConfiguration conf = createConfiguration(true);
        this.correctnessImpl(conf);
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void rawSerialFormEfficiency() throws Exception
    {
        final IgniteConfiguration referenceConf = createConfiguration(false);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(true);

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            // variable length integers provide significant advantages for all but maximum key value range - 35%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.35);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    protected void correctnessImpl(final IgniteConfiguration conf) throws Exception
    {
        try (Ignite grid = Ignition.start(conf))
        {
            @SuppressWarnings("deprecation")
            final Marshaller marshaller = grid.configuration().getMarshaller();

            CachePucKey controlValue;
            CachePucKey serialisedValue;

            controlValue = CACHE_PUC_KEY_CTOR.newInstance(Long.valueOf(1l), Long.valueOf(2l), Long.valueOf(3l));
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);

            controlValue = CACHE_PUC_KEY_CTOR.newInstance(Long.valueOf(4l), Long.valueOf(5l), null);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);

            controlValue = CACHE_PUC_KEY_CTOR.newInstance(Long.valueOf(6l), null, null);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);

            controlValue = CACHE_PUC_KEY_CTOR.newInstance(null, null, null);
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
            final String referenceSerialisationType, final double marginFraction) throws NoSuchMethodException
    {
        final SecureRandom rnJesus = new SecureRandom();

        final Supplier<CachePucKey> comparisonValueSupplier = () -> {
            try
            {
                // due to deduplication, majority of (often used) values in alf_prop_unique_ctx would be quite low
                final CachePucKey value = CACHE_PUC_KEY_CTOR.newInstance(Long.valueOf(rnJesus.nextInt(10000000)),
                        Long.valueOf(rnJesus.nextInt(10000000)), Long.valueOf(rnJesus.nextInt(10000000)));

                return value;
            }
            catch (final InstantiationException | InvocationTargetException | IllegalAccessException e)
            {
                throw new RuntimeException("Failed to instantiate benchmark value");
            }
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "CachePucKey", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
