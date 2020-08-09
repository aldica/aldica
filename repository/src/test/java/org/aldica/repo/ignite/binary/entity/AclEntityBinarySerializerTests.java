/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.repo.domain.permissions.AclEntity;
import org.alfresco.repo.security.permissions.ACLType;
import org.alfresco.util.GUID;
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
public class AclEntityBinarySerializerTests extends GridTestsBase
{

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForAclEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForAclEntity.setTypeName(AclEntity.class.getName());
        final AclEntityBinarySerializer serializer = new AclEntityBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForAclEntity.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForAclEntity));
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

            // ACL ID serialisation and boolean-type aggregation save quite a bit - 23%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0.23);
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

            // we optimise serial form for most value components, so still quite a bit of difference compared to the already optimised
            // aldica default - 43%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.43);
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

            AclEntity controlValue;
            AclEntity serialisedValue;

            controlValue = new AclEntity();
            controlValue.setId(1l);
            controlValue.setVersion(1l);
            controlValue.setAclId(GUID.generate());
            controlValue.setLatest(true);
            controlValue.setAclVersion(1l);
            controlValue.setInherits(false);
            controlValue.setInheritsFrom(null);
            controlValue.setAclType(ACLType.DEFINING);
            controlValue.setInheritedAcl(2l);
            controlValue.setVersioned(false);
            controlValue.setRequiresVersion(false);
            controlValue.setAclChangeSetId(1l);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // don't check for equals - value class only has superficial check
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getAclId(), serialisedValue.getAclId());
            Assert.assertEquals(controlValue.getInherits(), serialisedValue.getInherits());
            Assert.assertNull(serialisedValue.getInheritsFrom());
            Assert.assertEquals(controlValue.getType(), serialisedValue.getType());
            Assert.assertEquals(controlValue.getInheritedAcl(), serialisedValue.getInheritedAcl());
            Assert.assertEquals(controlValue.isVersioned(), serialisedValue.isVersioned());
            Assert.assertEquals(controlValue.getRequiresVersion(), serialisedValue.getRequiresVersion());
            Assert.assertEquals(controlValue.getAclChangeSetId(), serialisedValue.getAclChangeSetId());

            controlValue = new AclEntity();
            controlValue.setId(2l);
            controlValue.setVersion(1l);
            controlValue.setAclId(GUID.generate());
            controlValue.setLatest(true);
            controlValue.setAclVersion(1l);
            controlValue.setInherits(true);
            controlValue.setInheritsFrom(1l);
            controlValue.setAclType(ACLType.SHARED);
            controlValue.setInheritedAcl(2l);
            controlValue.setVersioned(false);
            controlValue.setRequiresVersion(false);
            controlValue.setAclChangeSetId(1l);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // don't check for equals - value class only has superficial check
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getAclId(), serialisedValue.getAclId());
            Assert.assertEquals(controlValue.getInherits(), serialisedValue.getInherits());
            Assert.assertEquals(controlValue.getInheritsFrom(), serialisedValue.getInheritsFrom());
            Assert.assertEquals(controlValue.getType(), serialisedValue.getType());
            Assert.assertEquals(controlValue.getInheritedAcl(), serialisedValue.getInheritedAcl());
            Assert.assertEquals(controlValue.isVersioned(), serialisedValue.isVersioned());
            Assert.assertEquals(controlValue.getRequiresVersion(), serialisedValue.getRequiresVersion());
            Assert.assertEquals(controlValue.getAclChangeSetId(), serialisedValue.getAclChangeSetId());

            // all nullable fields nulled and booleans inverted from previous
            controlValue = new AclEntity();
            controlValue.setId(3l);
            controlValue.setVersion(1l);
            controlValue.setAclId(GUID.generate());
            controlValue.setLatest(true);
            controlValue.setAclVersion(1l);
            controlValue.setInherits(false);
            controlValue.setInheritsFrom(null);
            controlValue.setAclType(ACLType.FIXED);
            controlValue.setInheritedAcl(null);
            controlValue.setVersioned(true);
            controlValue.setRequiresVersion(true);
            controlValue.setAclChangeSetId(null);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // don't check for equals - value class only has superficial check
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getAclId(), serialisedValue.getAclId());
            Assert.assertEquals(controlValue.getInherits(), serialisedValue.getInherits());
            Assert.assertNull(serialisedValue.getInheritsFrom());
            Assert.assertEquals(controlValue.getType(), serialisedValue.getType());
            Assert.assertNull(serialisedValue.getInheritedAcl());
            Assert.assertEquals(controlValue.isVersioned(), serialisedValue.isVersioned());
            Assert.assertEquals(controlValue.getRequiresVersion(), serialisedValue.getRequiresVersion());
            Assert.assertNull(serialisedValue.getAclChangeSetId());

            // all nullable fields set
            controlValue = new AclEntity();
            controlValue.setId(4l);
            controlValue.setVersion(1l);
            controlValue.setAclId(GUID.generate());
            controlValue.setLatest(true);
            controlValue.setAclVersion(1l);
            controlValue.setInherits(false);
            controlValue.setInheritsFrom(999l);
            controlValue.setAclType(ACLType.GLOBAL);
            controlValue.setInheritedAcl(999l);
            controlValue.setVersioned(true);
            controlValue.setRequiresVersion(true);
            controlValue.setAclChangeSetId(999l);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            // don't check for equals - value class only has superficial check
            // check deep serialisation was actually involved (different value instances)
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getAclId(), serialisedValue.getAclId());
            Assert.assertEquals(controlValue.getInherits(), serialisedValue.getInherits());
            Assert.assertEquals(controlValue.getInheritsFrom(), serialisedValue.getInheritsFrom());
            Assert.assertEquals(controlValue.getType(), serialisedValue.getType());
            Assert.assertEquals(controlValue.getInheritedAcl(), serialisedValue.getInheritedAcl());
            Assert.assertEquals(controlValue.isVersioned(), serialisedValue.isVersioned());
            Assert.assertEquals(controlValue.getRequiresVersion(), serialisedValue.getRequiresVersion());
            Assert.assertEquals(controlValue.getAclChangeSetId(), serialisedValue.getAclChangeSetId());
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

        final Supplier<AclEntity> comparisonValueSupplier = () -> {
            final int idx = 1000 + rnJesus.nextInt(100000000);
            final AclEntity value = new AclEntity();
            value.setId(Long.valueOf(idx));
            value.setVersion(Long.valueOf(rnJesus.nextInt(1024)));
            value.setAclId(GUID.generate());
            value.setLatest(true);
            value.setAclVersion(Long.valueOf(rnJesus.nextInt(128)));
            value.setInherits(true);
            value.setInheritsFrom(Long.valueOf(rnJesus.nextInt(idx)));
            value.setAclType(ACLType.SHARED);
            value.setInheritedAcl(Long.valueOf(idx));
            value.setVersioned(false);
            value.setRequiresVersion(false);
            value.setAclChangeSetId(Long.valueOf(idx));

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "AclEntity", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
