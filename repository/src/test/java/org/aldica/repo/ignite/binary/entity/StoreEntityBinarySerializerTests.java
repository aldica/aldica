/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.repo.domain.node.StoreEntity;
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
public class StoreEntityBinarySerializerTests extends GridTestsBase
{

    private static final String PROTOCOL_USER = "user";

    private static final String PROTOCOL_SYSTEM = "system";

    // relevant protocols - no support for legacy avm or any test-like ones (test/deleted) - plus one custom one
    private static final String[] PROTOCOLS = { PROTOCOL_USER, PROTOCOL_SYSTEM, StoreRef.PROTOCOL_ARCHIVE, StoreRef.PROTOCOL_WORKSPACE,
            "custom" };

    private static final String IDENTIFIER_ALFRESCO_USER_STORE = "alfrescoUserStore";

    private static final String IDENTIFIER_SYSTEM = "system";

    private static final String IDENTIFIER_LIGHT_WEIGHT_VERSION_STORE = "lightWeightVersionStore";

    private static final String IDENTIFIER_VERSION_2_STORE = "version2Store";

    private static final String IDENTIFIER_SPACES_STORE = "SpacesStore";

    // relevant default stores - no support for tenant-specific store identifiers - plus one custom one
    private static final String[] IDENTIFIERS = { IDENTIFIER_ALFRESCO_USER_STORE, IDENTIFIER_SYSTEM, IDENTIFIER_LIGHT_WEIGHT_VERSION_STORE,
            IDENTIFIER_VERSION_2_STORE, IDENTIFIER_SPACES_STORE, "myStore" };

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForStoreEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForStoreEntity.setTypeName(StoreEntity.class.getName());
        final StoreEntityBinarySerializer serializer = new StoreEntityBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForStoreEntity.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForStoreEntity));
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

            // 29%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0.29);
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

            // we optimise serial form for most value components, so quite a bit of difference - 36%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.36);
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

            StoreEntity controlValue;
            StoreEntity serialisedValue;

            // normal case
            controlValue = new StoreEntity();
            controlValue.setId(1l);
            controlValue.setVersion(1l);
            controlValue.setProtocol(StoreRef.PROTOCOL_WORKSPACE);
            controlValue.setIdentifier("SpacesStore");
            controlValue.setRootNode(new NodeEntity());

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // can't check for equals - value class does not support it
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getProtocol(), serialisedValue.getProtocol());
            Assert.assertEquals(controlValue.getIdentifier(), serialisedValue.getIdentifier());
            Assert.assertNotNull(serialisedValue.getRootNode());

            // custom store identifier
            controlValue = new StoreEntity();
            controlValue.setId(2l);
            controlValue.setVersion(1l);
            controlValue.setProtocol(StoreRef.PROTOCOL_WORKSPACE);
            controlValue.setIdentifier("custom");

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // can't check for equals - value class does not support it
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getProtocol(), serialisedValue.getProtocol());
            Assert.assertEquals(controlValue.getIdentifier(), serialisedValue.getIdentifier());
            Assert.assertSame(controlValue.getRootNode(), serialisedValue.getRootNode());

            // custom store protocol
            controlValue = new StoreEntity();
            controlValue.setId(3l);
            controlValue.setVersion(1l);
            controlValue.setProtocol("custom");
            controlValue.setIdentifier("SpacesStore");

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // can't check for equals - value class does not support it
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getProtocol(), serialisedValue.getProtocol());
            Assert.assertEquals(controlValue.getIdentifier(), serialisedValue.getIdentifier());
            Assert.assertSame(controlValue.getRootNode(), serialisedValue.getRootNode());

            // entirely custom store
            controlValue = new StoreEntity();
            controlValue.setId(4l);
            controlValue.setVersion(1l);
            controlValue.setProtocol("custom");
            controlValue.setIdentifier("custom");

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // can't check for equals - value class does not support it
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getProtocol(), serialisedValue.getProtocol());
            Assert.assertEquals(controlValue.getIdentifier(), serialisedValue.getIdentifier());
            Assert.assertSame(controlValue.getRootNode(), serialisedValue.getRootNode());

            // incomplete case (e.g. as constituent of NodeEntity)
            controlValue = new StoreEntity();
            controlValue.setProtocol(StoreRef.PROTOCOL_WORKSPACE);
            controlValue.setIdentifier("SpacesStore");

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // can't check for equals - value class does not support it
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getProtocol(), serialisedValue.getProtocol());
            Assert.assertEquals(controlValue.getIdentifier(), serialisedValue.getIdentifier());
            Assert.assertNull(serialisedValue.getRootNode());
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

        final Supplier<StoreEntity> comparisonValueSupplier = () -> {
            final int idx = 1000 + rnJesus.nextInt(100000000);
            final StoreEntity value = new StoreEntity();
            value.setId(Long.valueOf(idx));
            value.setVersion(Long.valueOf(rnJesus.nextInt(1024)));
            value.setProtocol(PROTOCOLS[rnJesus.nextInt(PROTOCOLS.length)]);
            value.setIdentifier(IDENTIFIERS[rnJesus.nextInt(IDENTIFIERS.length)]);

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "StoreEntity", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
