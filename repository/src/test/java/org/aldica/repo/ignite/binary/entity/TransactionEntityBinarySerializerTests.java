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
import org.alfresco.repo.domain.node.TransactionEntity;
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
public class TransactionEntityBinarySerializerTests extends GridTestsBase
{

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForTransactionEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForTransactionEntity.setTypeName(TransactionEntity.class.getName());
        final TransactionEntityBinarySerializer serializer = new TransactionEntityBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForTransactionEntity.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForTransactionEntity));
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

            // no real advantage (minor disadvantage even due to additional flag) - -1.25%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", -0.0125);
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

            // improvement in raw serial form make a small dent due to size of txn UUID - 25%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.25);
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

            TransactionEntity controlValue;
            TransactionEntity serialisedValue;

            // normal case
            controlValue = new TransactionEntity();
            controlValue.setId(1l);
            controlValue.setVersion(1l);
            controlValue.setChangeTxnId(UUID.randomUUID().toString());
            controlValue.setCommitTimeMs(System.currentTimeMillis());

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // can't check for equals - value class does not support it
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getChangeTxnId(), serialisedValue.getChangeTxnId());
            Assert.assertEquals(controlValue.getCommitTimeMs(), serialisedValue.getCommitTimeMs());

            // incomplete case (e.g. as constituent of NodeEntity)
            controlValue = new TransactionEntity();
            controlValue.setId(2l);
            controlValue.setChangeTxnId(UUID.randomUUID().toString());

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // can't check for equals - value class does not support it
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getChangeTxnId(), serialisedValue.getChangeTxnId());
            Assert.assertEquals(controlValue.getCommitTimeMs(), serialisedValue.getCommitTimeMs());
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

        final Supplier<TransactionEntity> comparisonValueSupplier = () -> {
            final int idx = 1000 + rnJesus.nextInt(100000000);
            final TransactionEntity value = new TransactionEntity();
            value.setId(Long.valueOf(idx));
            value.setVersion(Long.valueOf(rnJesus.nextInt(1024)));
            value.setChangeTxnId(UUID.randomUUID().toString());
            value.setCommitTimeMs(System.currentTimeMillis() + rnJesus.nextInt(365 * 24 * 60 * 60 * 1000));

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "TransactionEntity", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
