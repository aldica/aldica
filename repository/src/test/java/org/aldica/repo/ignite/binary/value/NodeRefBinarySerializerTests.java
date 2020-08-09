/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
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
public class NodeRefBinarySerializerTests extends GridTestsBase
{

    private static final int MODE_FULLY_RANDOM = 0;

    private static final int MODE_RANDOM_STORE_AND_NODE_ID = 1;

    private static final int MODE_KNOWN_STORE_AND_RANDOM_NODE_ID = 2;

    private static final String PROTOCOL_USER = "user";

    private static final String PROTOCOL_SYSTEM = "system";

    private static final String[] PROTOCOLS = { PROTOCOL_USER, PROTOCOL_SYSTEM, StoreRef.PROTOCOL_ARCHIVE, StoreRef.PROTOCOL_WORKSPACE };

    private static final StoreRef[] DEFAULT_STORE_REFS = new StoreRef[] { NodeRefBinarySerializer.REF_ARCHIVE_SPACES_STORE,
            NodeRefBinarySerializer.REF_SYSTEM_SYSTEM, NodeRefBinarySerializer.REF_USER_ALFRESCO_USER_STORE,
            NodeRefBinarySerializer.REF_WORKSPACE_LIGHT_WEIGHT_VERSION_STORE, NodeRefBinarySerializer.REF_WORKSPACE_SPACES_STORE,
            NodeRefBinarySerializer.REF_WORKSPACE_VERSION2STORE };

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForNodeRef = new BinaryTypeConfiguration();
        binaryTypeConfigurationForNodeRef.setTypeName(NodeRef.class.getName());
        final NodeRefBinarySerializer serializer = new NodeRefBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForNodeRef.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForNodeRef));
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

            // minimal potential, but inlining StoreRef should still yield at least 12%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", MODE_FULLY_RANDOM, 0.12);

            // decent potential with known store protocol, 23%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", MODE_RANDOM_STORE_AND_NODE_ID, 0.23);

            // most potential with full store optimised away into a byte flag, 44%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", MODE_KNOWN_STORE_AND_RANDOM_NODE_ID, 0.44);
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

            // saving potential is limited - 11%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", MODE_FULLY_RANDOM, 0.11);

            // saving potential is limited - 10%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", MODE_RANDOM_STORE_AND_NODE_ID, 0.10);

            // saving potential is limited - 10%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", MODE_KNOWN_STORE_AND_RANDOM_NODE_ID, 0.10);
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

            NodeRef controlValue;
            NodeRef serialisedValue;

            controlValue = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "node1");
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertNotSame(controlValue.getId(), serialisedValue.getId());
            // well-known store should use same value
            Assert.assertSame(controlValue.getStoreRef(), serialisedValue.getStoreRef());

            controlValue = new NodeRef(new StoreRef("my", "store"), "node2");
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertNotSame(controlValue.getStoreRef(), serialisedValue.getStoreRef());
            Assert.assertNotSame(controlValue.getId(), serialisedValue.getId());

            controlValue = new NodeRef(new StoreRef(StoreRef.PROTOCOL_WORKSPACE, "store"), "node3");
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            // check deep serialisation was actually involved
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertNotSame(controlValue.getStoreRef(), serialisedValue.getStoreRef());
            Assert.assertNotSame(controlValue.getId(), serialisedValue.getId());
            Assert.assertNotSame(controlValue.getStoreRef().getIdentifier(), serialisedValue.getStoreRef().getIdentifier());
            // well known protocol should use same value
            Assert.assertSame(controlValue.getStoreRef().getProtocol(), serialisedValue.getStoreRef().getProtocol());
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
            case MODE_RANDOM_STORE_AND_NODE_ID:
                modeStr = "random store and node IDs";
                break;
            case MODE_KNOWN_STORE_AND_RANDOM_NODE_ID:
                modeStr = "known stores and random node IDs";
                break;
        }

        final SecureRandom rnJesus = new SecureRandom();

        final Supplier<NodeRef> comparisonValueSupplier = () -> {
            StoreRef storeRef = null;
            switch (mode)
            {
                case MODE_FULLY_RANDOM:
                    storeRef = new StoreRef(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                    break;
                case MODE_RANDOM_STORE_AND_NODE_ID:
                    storeRef = new StoreRef(PROTOCOLS[rnJesus.nextInt(PROTOCOLS.length)], UUID.randomUUID().toString());
                    break;
                case MODE_KNOWN_STORE_AND_RANDOM_NODE_ID:
                    storeRef = DEFAULT_STORE_REFS[rnJesus.nextInt(DEFAULT_STORE_REFS.length)];
                    break;
            }
            final NodeRef value = new NodeRef(storeRef, UUID.randomUUID().toString());
            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "StoreRef (" + modeStr + ")", referenceSerialisationType,
                serialisationType, comparisonValueSupplier, marginFraction);
    }
}
