/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.service.cmr.action.ExecutionDetails;
import org.alfresco.service.cmr.repository.NodeRef;
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
public class ExecutionDetailsBinarySerializerTests extends GridTestsBase
{

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForExecutionDetails = new BinaryTypeConfiguration();
        binaryTypeConfigurationForExecutionDetails.setTypeName(ExecutionDetails.class.getName());
        final ExecutionDetailsBinarySerializer serializer = new ExecutionDetailsBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForExecutionDetails.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForExecutionDetails));
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

            // exactly identical
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0);
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

            // lack of field metadata + variable length integer reduce size somewhat - 6%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.06);
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

            ExecutionDetails controlValue;
            ExecutionDetails serialisedValue;

            // normal details (no transient summary)
            controlValue = new ExecutionDetails(null, new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "12345"),
                    new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "09876"), "someServer", Date.from(Instant.now()), false);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // no equals in implementation
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getPersistedActionRef(), serialisedValue.getPersistedActionRef());
            Assert.assertEquals(controlValue.getActionedUponNodeRef(), serialisedValue.getActionedUponNodeRef());
            Assert.assertEquals(controlValue.getRunningOn(), serialisedValue.getRunningOn());
            Assert.assertEquals(controlValue.getStartedAt(), serialisedValue.getStartedAt());
            Assert.assertEquals(controlValue.isCancelRequested(), serialisedValue.isCancelRequested());

            // different values, primarily for boolean flag
            controlValue = new ExecutionDetails(null, new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "376491"),
                    new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "09182737"), "someOtherServer",
                    Date.from(Instant.now().minusSeconds(3600)), true);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // no equals in implementation
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getPersistedActionRef(), serialisedValue.getPersistedActionRef());
            Assert.assertEquals(controlValue.getActionedUponNodeRef(), serialisedValue.getActionedUponNodeRef());
            Assert.assertEquals(controlValue.getRunningOn(), serialisedValue.getRunningOn());
            Assert.assertEquals(controlValue.getStartedAt(), serialisedValue.getStartedAt());
            Assert.assertEquals(controlValue.isCancelRequested(), serialisedValue.isCancelRequested());

            // default constructor => every field is null / primitive default
            controlValue = new ExecutionDetails();
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // no equals in implementation
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getPersistedActionRef(), serialisedValue.getPersistedActionRef());
            Assert.assertEquals(controlValue.getActionedUponNodeRef(), serialisedValue.getActionedUponNodeRef());
            Assert.assertEquals(controlValue.getRunningOn(), serialisedValue.getRunningOn());
            Assert.assertEquals(controlValue.getStartedAt(), serialisedValue.getStartedAt());
            Assert.assertEquals(controlValue.isCancelRequested(), serialisedValue.isCancelRequested());
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
        final Instant now = Instant.now();
        final Supplier<ExecutionDetails> comparisonValueSupplier = () -> new ExecutionDetails(null,
                new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString()),
                new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString()), UUID.randomUUID().toString(),
                Date.from(now.minusSeconds(rnJesus.nextInt(36000))), rnJesus.nextBoolean());

        super.serialisationEfficiencyComparison(referenceGrid, grid, "ExecutionDetails", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
