/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.service.cmr.repository.StoreRef;
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
public class StoreRefBinarySerializerTests extends GridTestsBase
{

    private static final int MODE_FULLY_RANDOM = 0;

    private static final int MODE_RANDOM_ID = 1;

    private static final String PROTOCOL_USER = "user";

    private static final String PROTOCOL_SYSTEM = "system";

    private static final String[] PROTOCOLS = { PROTOCOL_USER, PROTOCOL_SYSTEM, StoreRef.PROTOCOL_ARCHIVE, StoreRef.PROTOCOL_WORKSPACE };

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForStoreRef = new BinaryTypeConfiguration();
        binaryTypeConfigurationForStoreRef.setTypeName(StoreRef.class.getName());
        final StoreRefBinarySerializer serializer = new StoreRefBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForStoreRef.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForStoreRef));
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

            // in this case aldica optimised is actually less efficient than Ignite default due to extra byte for type flag
            // verify it is still within 3% even in this unlikely worst case
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", MODE_FULLY_RANDOM, -0.03);

            // fixed, short store protocols are small fraction of overall store reference with UUID as identifier
            // savings are thus limited in our test, but definitely more pronounced in real life
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", MODE_RANDOM_ID, 0.12);
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

            // saving potential is limited - 10.5%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", MODE_FULLY_RANDOM, 0.105);

            // saving potential is limited - 10%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", MODE_RANDOM_ID, 0.10);
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

            StoreRef controlValue;
            StoreRef serialisedValue;

            controlValue = new StoreRef(StoreRef.PROTOCOL_WORKSPACE, "node1");
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertNotSame(controlValue.getIdentifier(), serialisedValue.getIdentifier());
            // well-known protocol should use same value
            Assert.assertSame(controlValue.getProtocol(), serialisedValue.getProtocol());

            controlValue = new StoreRef("my", "store");
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertNotSame(controlValue.getIdentifier(), serialisedValue.getIdentifier());
            Assert.assertNotSame(controlValue.getProtocol(), serialisedValue.getProtocol());
        }
        catch (final IgniteCheckedException ice)
        {
            throw new IllegalStateException("Serialisation failed in correctness test", ice);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final String serialisationType,
            final String referenceSerialisationType, final int mode, final double marginFraction)
    {
        String modeStr = "unknown";
        switch (mode)
        {
            case MODE_FULLY_RANDOM:
                modeStr = "fully random values";
                break;
            case MODE_RANDOM_ID:
                modeStr = "random store IDs";
                break;
        }

        final SecureRandom rnJesus = new SecureRandom();

        final Supplier<StoreRef> comparisonValueSupplier = () -> {
            StoreRef value = null;
            switch (mode)
            {
                case MODE_FULLY_RANDOM:
                    value = new StoreRef(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                    break;
                case MODE_RANDOM_ID:
                    value = new StoreRef(PROTOCOLS[rnJesus.nextInt(PROTOCOLS.length)], UUID.randomUUID().toString());
                    break;
            }
            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "StoreRef (" + modeStr + ")", referenceSerialisationType,
                serialisationType, comparisonValueSupplier, marginFraction);
    }
}
