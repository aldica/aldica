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
import org.alfresco.repo.domain.node.ChildAssocEntity;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
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
public class ChildAssocEntityBinarySerializerTests extends GridTestsBase
{

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForChildAssocEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForChildAssocEntity.setTypeName(ChildAssocEntity.class.getName());
        final ChildAssocEntityBinarySerializer serializer = new ChildAssocEntityBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForChildAssocEntity.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForChildAssocEntity));
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

            // slight savings due to reduced field metadata - 3%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0.03);
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

            // variable length integers / optimised strings - further 23%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.23);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @SuppressWarnings("deprecation")
    protected void correctnessImpl(final IgniteConfiguration conf)
    {
        try (Ignite grid = Ignition.start(conf))
        {
            final Marshaller marshaller = grid.configuration().getMarshaller();

            ChildAssocEntity controlValue;
            ChildAssocEntity serialisedValue;
            final NodeEntity parentNodeControlValue;
            NodeEntity parentNodeSerialisedValue;
            final NodeEntity childNodeControlValue;
            NodeEntity childNodeSerialisedValue;

            // we don't really need different entities for parent / child node
            // their serialisation is tested in detail in other tests
            parentNodeControlValue = new NodeEntity();
            parentNodeControlValue.setId(1l);

            childNodeControlValue = new NodeEntity();
            childNodeControlValue.setId(2l);

            controlValue = new ChildAssocEntity();
            controlValue.setId(1l);
            controlValue.setVersion(1l);
            controlValue.setParentNode(parentNodeControlValue);
            controlValue.setChildNode(childNodeControlValue);
            controlValue.setTypeQNameId(1l);
            controlValue.setChildNodeName("childFolder");
            controlValue.setChildNodeNameCrc(ChildAssocEntity.getChildNodeNameCrc(controlValue.getChildNodeName()));
            controlValue.setQnameNamespaceId(1l);
            controlValue.setQnameLocalName("childFolder");
            controlValue.setQnameCrc(ChildAssocEntity
                    .getQNameCrc(QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, controlValue.getQnameLocalName())));
            controlValue.setPrimary(Boolean.TRUE);
            controlValue.setAssocIndex(-1);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            parentNodeSerialisedValue = serialisedValue.getParentNode();
            childNodeSerialisedValue = serialisedValue.getChildNode();

            // can't check for equals - value class does not offer equals
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertNotSame(parentNodeControlValue, parentNodeSerialisedValue);
            Assert.assertEquals(parentNodeControlValue.getId(), parentNodeSerialisedValue.getId());
            Assert.assertNotSame(childNodeControlValue, childNodeSerialisedValue);
            Assert.assertEquals(childNodeControlValue.getId(), childNodeSerialisedValue.getId());
            Assert.assertEquals(controlValue.getTypeQNameId(), serialisedValue.getTypeQNameId());
            Assert.assertEquals(controlValue.getChildNodeName(), serialisedValue.getChildNodeName());
            Assert.assertEquals(controlValue.getChildNodeNameCrc(), serialisedValue.getChildNodeNameCrc());
            Assert.assertEquals(controlValue.getQnameNamespaceId(), serialisedValue.getQnameNamespaceId());
            Assert.assertEquals(controlValue.getQnameLocalName(), serialisedValue.getQnameLocalName());
            Assert.assertEquals(controlValue.getQnameCrc(), serialisedValue.getQnameCrc());
            Assert.assertEquals(controlValue.isPrimary(), serialisedValue.isPrimary());
            Assert.assertEquals(controlValue.getAssocIndex(), serialisedValue.getAssocIndex());

            controlValue = new ChildAssocEntity();
            controlValue.setId(2l);
            controlValue.setVersion(null);
            controlValue.setParentNode(parentNodeControlValue);
            controlValue.setChildNode(childNodeControlValue);
            controlValue.setTypeQNameId(987654321l);
            controlValue.setChildNodeName(UUID.randomUUID().toString());
            controlValue.setChildNodeNameCrc(ChildAssocEntity.getChildNodeNameCrc(controlValue.getChildNodeName()));
            controlValue.setQnameNamespaceId(1l);
            controlValue.setQnameLocalName(UUID.randomUUID().toString());
            controlValue.setQnameCrc(ChildAssocEntity
                    .getQNameCrc(QName.createQName(NamespaceService.SYSTEM_MODEL_1_0_URI, controlValue.getQnameLocalName())));
            controlValue.setPrimary(null);
            controlValue.setAssocIndex(123456789);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            parentNodeSerialisedValue = serialisedValue.getParentNode();
            childNodeSerialisedValue = serialisedValue.getChildNode();

            // can't check for equals - value class does not offer equals
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertNotSame(parentNodeControlValue, parentNodeSerialisedValue);
            Assert.assertEquals(parentNodeControlValue.getId(), parentNodeSerialisedValue.getId());
            Assert.assertNotSame(childNodeControlValue, childNodeSerialisedValue);
            Assert.assertEquals(childNodeControlValue.getId(), childNodeSerialisedValue.getId());
            Assert.assertEquals(controlValue.getTypeQNameId(), serialisedValue.getTypeQNameId());
            Assert.assertEquals(controlValue.getChildNodeName(), serialisedValue.getChildNodeName());
            Assert.assertEquals(controlValue.getChildNodeNameCrc(), serialisedValue.getChildNodeNameCrc());
            Assert.assertEquals(controlValue.getQnameNamespaceId(), serialisedValue.getQnameNamespaceId());
            Assert.assertEquals(controlValue.getQnameLocalName(), serialisedValue.getQnameLocalName());
            Assert.assertEquals(controlValue.getQnameCrc(), serialisedValue.getQnameCrc());
            Assert.assertEquals(controlValue.isPrimary(), serialisedValue.isPrimary());
            Assert.assertEquals(controlValue.getAssocIndex(), serialisedValue.getAssocIndex());
        }
        catch (final IgniteCheckedException ice)
        {
            throw new IllegalStateException("Serialisation failed in correctness test", ice);
        }
    }

    @SuppressWarnings("deprecation")
    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final String serialisationType,
            final String referenceSerialisationType, final double marginFraction)
    {
        final NodeEntity parentNode;
        final NodeEntity childNode;

        // we don't really need different entities for parent / child node
        // their serialisation is benchmarked in other tests
        parentNode = new NodeEntity();
        parentNode.setId(1l);

        childNode = new NodeEntity();
        childNode.setId(2l);

        final SecureRandom rnJesus = new SecureRandom();

        final Supplier<ChildAssocEntity> comparisonValueSupplier = () -> {
            final int idx = 1000 + rnJesus.nextInt(100000000);
            final ChildAssocEntity value = new ChildAssocEntity();

            value.setId((long) idx);
            // versions on child assocs typically have very low values
            value.setVersion((long) rnJesus.nextInt(10));
            value.setParentNode(parentNode);
            value.setChildNode(childNode);
            // most systems should have no more than 3000 distinct class / feature qnames
            // ~300 is the default in a fresh system
            value.setTypeQNameId((long) rnJesus.nextInt(3000));
            value.setChildNodeName(UUID.randomUUID().toString());
            value.setChildNodeNameCrc(ChildAssocEntity.getChildNodeNameCrc(value.getChildNodeName()));
            // most systems should have no more than 50 model namespaces
            // ~25 is the default in a fresh system
            value.setQnameNamespaceId((long) rnJesus.nextInt(50));
            value.setQnameLocalName(UUID.randomUUID().toString());
            value.setQnameCrc(
                    ChildAssocEntity.getQNameCrc(QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, value.getQnameLocalName())));
            value.setPrimary(Boolean.TRUE);
            // -1 is the most common assoc index (in some systems the only one)
            value.setAssocIndex(-1);

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "ChildAssocEntity", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
