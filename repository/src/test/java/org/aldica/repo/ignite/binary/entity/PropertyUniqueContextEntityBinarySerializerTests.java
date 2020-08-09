/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.repo.domain.propval.PropertyUniqueContextEntity;
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
public class PropertyUniqueContextEntityBinarySerializerTests extends GridTestsBase
{

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForPropertyUniqueContextEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForPropertyUniqueContextEntity.setTypeName(PropertyUniqueContextEntity.class.getName());
        final PropertyUniqueContextEntityBinarySerializer serializer = new PropertyUniqueContextEntityBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForPropertyUniqueContextEntity.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForPropertyUniqueContextEntity));
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

            // no real advantage (minor disadvantage even due to additional flag) - -4%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", -0.04);
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

            // variable length integers provide significant advantages for all but maximum key value range - 41.5%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.415);
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

            PropertyUniqueContextEntity controlValue;
            PropertyUniqueContextEntity serialisedValue;

            controlValue = new PropertyUniqueContextEntity();
            controlValue.setId(1l);
            controlValue.setVersion((short) 1);
            controlValue.setValue1PropId(1l);
            controlValue.setValue2PropId(2l);
            controlValue.setValue3PropId(3l);
            controlValue.setPropertyId(4l);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // can't check for equals - value class does not support it
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getValue1PropId(), serialisedValue.getValue1PropId());
            Assert.assertEquals(controlValue.getValue2PropId(), serialisedValue.getValue2PropId());
            Assert.assertEquals(controlValue.getValue3PropId(), serialisedValue.getValue3PropId());
            Assert.assertEquals(controlValue.getPropertyId(), serialisedValue.getPropertyId());

            controlValue = new PropertyUniqueContextEntity();
            controlValue.setId(2l);
            controlValue.setVersion(Short.MAX_VALUE);
            controlValue.setValue1PropId(5l);
            controlValue.setValue2PropId(6l);
            controlValue.setValue3PropId(null);
            controlValue.setPropertyId(7l);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // can't check for equals - value class does not support it
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getValue1PropId(), serialisedValue.getValue1PropId());
            Assert.assertEquals(controlValue.getValue2PropId(), serialisedValue.getValue2PropId());
            Assert.assertEquals(controlValue.getValue3PropId(), serialisedValue.getValue3PropId());
            Assert.assertEquals(controlValue.getPropertyId(), serialisedValue.getPropertyId());

            controlValue = new PropertyUniqueContextEntity();
            controlValue.setId(3l);
            controlValue.setVersion(Short.MIN_VALUE);
            controlValue.setValue1PropId(8l);
            controlValue.setValue2PropId(null);
            controlValue.setValue3PropId(null);
            controlValue.setPropertyId(8l);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // can't check for equals - value class does not support it
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getValue1PropId(), serialisedValue.getValue1PropId());
            Assert.assertEquals(controlValue.getValue2PropId(), serialisedValue.getValue2PropId());
            Assert.assertEquals(controlValue.getValue3PropId(), serialisedValue.getValue3PropId());
            Assert.assertEquals(controlValue.getPropertyId(), serialisedValue.getPropertyId());
        }
        catch (final IgniteCheckedException ice)
        {
            throw new IllegalStateException("Serialisation failed in correctness test", ice);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final String serialisationType,
            final String referenceSerialisationType, final double marginFraction)
    {
        final SecureRandom rnJesus = new SecureRandom();

        final Supplier<PropertyUniqueContextEntity> comparisonValueSupplier = () -> {
            // anywhere up to 10000000 distinct nodes
            final int idx = 1000 + rnJesus.nextInt(100000000);
            final PropertyUniqueContextEntity value = new PropertyUniqueContextEntity();
            value.setId(Long.valueOf(idx));
            value.setVersion((short) rnJesus.nextInt(Short.MAX_VALUE));
            // due to deduplication, majority of (often used) values in alf_prop_unique_ctx would be quite low
            value.setValue1PropId(Long.valueOf(rnJesus.nextInt(10000000)));
            value.setValue2PropId(Long.valueOf(rnJesus.nextInt(10000000)));
            value.setValue3PropId(Long.valueOf(rnJesus.nextInt(10000000)));
            value.setPropertyId(Long.valueOf(rnJesus.nextInt(10000000)));

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "PropertyUniqueContextEntity", referenceSerialisationType,
                serialisationType, comparisonValueSupplier, marginFraction);
    }
}
