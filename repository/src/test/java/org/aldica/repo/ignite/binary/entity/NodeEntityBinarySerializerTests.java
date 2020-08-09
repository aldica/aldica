/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.repo.domain.node.AuditablePropertiesEntity;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.repo.domain.node.NodeUpdateEntity;
import org.alfresco.repo.domain.node.StoreEntity;
import org.alfresco.repo.domain.node.TransactionEntity;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.util.ISO8601DateFormat;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.marshaller.Marshaller;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

/**
 * @author Axel Faust
 */
public class NodeEntityBinarySerializerTests extends GridTestsBase
{

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForNodeEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForNodeEntity.setTypeName(NodeEntity.class.getName());
        final NodeEntityBinarySerializer serializer = new NodeEntityBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForNodeEntity.setSerializer(serializer);
        final BinaryTypeConfiguration binaryTypeConfigurationForNodeUpdateEntity = new BinaryTypeConfiguration();
        binaryTypeConfigurationForNodeUpdateEntity.setTypeName(NodeUpdateEntity.class.getName());
        binaryTypeConfigurationForNodeUpdateEntity.setSerializer(serializer);

        binaryConfiguration
                .setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForNodeEntity, binaryTypeConfigurationForNodeUpdateEntity));
        conf.setBinaryConfiguration(binaryConfiguration);

        return conf;
    }

    @Rule
    public ExpectedException expected = ExpectedException.none();

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

            // potential value deduplication (auditable values) and UUID optimisation - 24%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0.24);
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

            // variable length integers and lack of field metadata in complex object - further 21%
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

            NodeEntity controlValue;
            NodeEntity serialisedValue;
            final StoreEntity storeControlValue;
            final TransactionEntity transactionControlValue;
            AuditablePropertiesEntity auditableControlValue;
            AuditablePropertiesEntity auditableSerialisedValue;

            // we don't really need different entities for store / transaction
            // their serialisation is tested in detail in other tests
            storeControlValue = new StoreEntity();
            storeControlValue.setId(1l);
            storeControlValue.setVersion(1l);
            storeControlValue.setProtocol(StoreRef.PROTOCOL_WORKSPACE);
            storeControlValue.setIdentifier("SpacesStore");

            transactionControlValue = new TransactionEntity();
            transactionControlValue.setId(1l);
            transactionControlValue.setVersion(1l);
            transactionControlValue.setChangeTxnId(UUID.randomUUID().toString());
            transactionControlValue.setCommitTimeMs(System.currentTimeMillis());

            // all set, uuid using expected format, different auditable values
            controlValue = new NodeEntity();
            controlValue.setId(1l);
            controlValue.setVersion(1l);
            controlValue.setStore(storeControlValue);
            controlValue.setUuid(UUID.randomUUID().toString());
            controlValue.setTypeQNameId(1l);
            controlValue.setLocaleId(1l);
            controlValue.setAclId(1l);
            controlValue.setTransaction(transactionControlValue);

            auditableControlValue = new AuditablePropertiesEntity();
            auditableControlValue.setAuditCreator(AuthenticationUtil.SYSTEM_USER_NAME);
            auditableControlValue.setAuditCreated("2020-01-01T00:00:00.000Z");
            auditableControlValue.setAuditModifier("admin");
            auditableControlValue.setAuditModified("2020-01-02T00:00:00.000Z");
            auditableControlValue.setAuditAccessed("2020-01-03T00:00:00.000Z");
            controlValue.setAuditableProperties(auditableControlValue);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            auditableSerialisedValue = serialisedValue.getAuditableProperties();

            // can't check for equals - value class does not support proper equals
            // check deep serialisation was actually involved (different value instances)
            // cannot check lock state directly
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getUuid(), serialisedValue.getUuid());
            Assert.assertNotNull(serialisedValue.getStore());
            Assert.assertNotSame(controlValue.getStore(), serialisedValue.getStore());
            Assert.assertEquals(controlValue.getStore().getId(), serialisedValue.getStore().getId());
            Assert.assertNotNull(serialisedValue.getTransaction());
            Assert.assertNotSame(controlValue.getTransaction(), serialisedValue.getTransaction());
            Assert.assertEquals(controlValue.getTransaction().getId(), serialisedValue.getTransaction().getId());
            Assert.assertEquals(controlValue.getTypeQNameId(), serialisedValue.getTypeQNameId());
            Assert.assertEquals(controlValue.getLocaleId(), serialisedValue.getLocaleId());
            Assert.assertEquals(controlValue.getAclId(), serialisedValue.getAclId());
            Assert.assertNotNull(auditableSerialisedValue);
            Assert.assertNotSame(auditableControlValue, auditableSerialisedValue);
            Assert.assertEquals(auditableControlValue.getAuditCreator(), auditableSerialisedValue.getAuditCreator());
            Assert.assertEquals(auditableControlValue.getAuditCreated(), auditableSerialisedValue.getAuditCreated());
            Assert.assertEquals(auditableControlValue.getAuditModifier(), auditableSerialisedValue.getAuditModifier());
            Assert.assertEquals(auditableControlValue.getAuditModified(), auditableSerialisedValue.getAuditModified());
            Assert.assertEquals(auditableControlValue.getAuditAccessed(), auditableSerialisedValue.getAuditAccessed());

            // minimal data, uuid using custom value, no audit values
            controlValue = new NodeEntity();
            controlValue.setId(2l);
            controlValue.setVersion(1l);
            controlValue.setStore(storeControlValue);
            controlValue.setUuid("my-custom-node-uuid");
            controlValue.setTypeQNameId(1l);
            controlValue.setLocaleId(1l);
            controlValue.setTransaction(transactionControlValue);

            auditableControlValue = new AuditablePropertiesEntity();
            controlValue.setAuditableProperties(auditableControlValue);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            auditableSerialisedValue = serialisedValue.getAuditableProperties();

            // can't check for equals - value class does not support proper equals
            // check deep serialisation was actually involved (different value instances)
            // cannot check lock state directly
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getUuid(), serialisedValue.getUuid());
            Assert.assertNotNull(serialisedValue.getStore());
            Assert.assertNotSame(controlValue.getStore(), serialisedValue.getStore());
            Assert.assertEquals(controlValue.getStore().getId(), serialisedValue.getStore().getId());
            Assert.assertNotNull(serialisedValue.getTransaction());
            Assert.assertNotSame(controlValue.getTransaction(), serialisedValue.getTransaction());
            Assert.assertEquals(controlValue.getTransaction().getId(), serialisedValue.getTransaction().getId());
            Assert.assertEquals(controlValue.getTypeQNameId(), serialisedValue.getTypeQNameId());
            Assert.assertEquals(controlValue.getLocaleId(), serialisedValue.getLocaleId());
            Assert.assertEquals(controlValue.getAclId(), serialisedValue.getAclId());
            Assert.assertNotNull(auditableSerialisedValue);
            Assert.assertNotSame(auditableControlValue, auditableSerialisedValue);
            Assert.assertEquals(auditableControlValue.getAuditCreator(), auditableSerialisedValue.getAuditCreator());
            Assert.assertEquals(auditableControlValue.getAuditCreated(), auditableSerialisedValue.getAuditCreated());
            Assert.assertEquals(auditableControlValue.getAuditModifier(), auditableSerialisedValue.getAuditModifier());
            Assert.assertEquals(auditableControlValue.getAuditModified(), auditableSerialisedValue.getAuditModified());
            Assert.assertEquals(auditableControlValue.getAuditAccessed(), auditableSerialisedValue.getAuditAccessed());

            // identical audit values
            controlValue = new NodeEntity();
            controlValue.setId(3l);
            controlValue.setVersion(1l);
            controlValue.setStore(storeControlValue);
            controlValue.setUuid(UUID.randomUUID().toString());
            controlValue.setTypeQNameId(1l);
            controlValue.setLocaleId(1l);
            controlValue.setTransaction(transactionControlValue);

            auditableControlValue = new AuditablePropertiesEntity();
            auditableControlValue.setAuditCreator(AuthenticationUtil.SYSTEM_USER_NAME);
            auditableControlValue.setAuditCreated("2020-01-01T00:00:00.000Z");
            auditableControlValue.setAuditModifier(AuthenticationUtil.SYSTEM_USER_NAME);
            auditableControlValue.setAuditModified("2020-01-01T00:00:00.000Z");
            controlValue.setAuditableProperties(auditableControlValue);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            auditableSerialisedValue = serialisedValue.getAuditableProperties();

            // can't check for equals - value class does not support proper equals
            // check deep serialisation was actually involved (different value instances)
            // cannot check lock state directly
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getUuid(), serialisedValue.getUuid());
            Assert.assertNotNull(serialisedValue.getStore());
            Assert.assertNotSame(controlValue.getStore(), serialisedValue.getStore());
            Assert.assertEquals(controlValue.getStore().getId(), serialisedValue.getStore().getId());
            Assert.assertNotNull(serialisedValue.getTransaction());
            Assert.assertNotSame(controlValue.getTransaction(), serialisedValue.getTransaction());
            Assert.assertEquals(controlValue.getTransaction().getId(), serialisedValue.getTransaction().getId());
            Assert.assertEquals(controlValue.getTypeQNameId(), serialisedValue.getTypeQNameId());
            Assert.assertEquals(controlValue.getLocaleId(), serialisedValue.getLocaleId());
            Assert.assertEquals(controlValue.getAclId(), serialisedValue.getAclId());
            Assert.assertNotNull(auditableSerialisedValue);
            Assert.assertNotSame(auditableControlValue, auditableSerialisedValue);
            Assert.assertEquals(auditableControlValue.getAuditCreator(), auditableSerialisedValue.getAuditCreator());
            Assert.assertEquals(auditableControlValue.getAuditCreated(), auditableSerialisedValue.getAuditCreated());
            Assert.assertEquals(auditableControlValue.getAuditModifier(), auditableSerialisedValue.getAuditModifier());
            Assert.assertEquals(auditableControlValue.getAuditModified(), auditableSerialisedValue.getAuditModified());
            Assert.assertEquals(auditableControlValue.getAuditAccessed(), auditableSerialisedValue.getAuditAccessed());

            // check update entity is supported, though update flags are ignored
            controlValue = new NodeUpdateEntity();
            controlValue.setId(4l);
            controlValue.setVersion(1l);
            controlValue.setStore(storeControlValue);
            controlValue.setUuid(UUID.randomUUID().toString());
            controlValue.setTypeQNameId(1l);
            controlValue.setLocaleId(1l);
            controlValue.setTransaction(transactionControlValue);

            ((NodeUpdateEntity) controlValue).setUpdateTypeQNameId(true);
            ((NodeUpdateEntity) controlValue).setUpdateLocaleId(true);
            ((NodeUpdateEntity) controlValue).setUpdateAclId(true);
            ((NodeUpdateEntity) controlValue).setUpdateTransaction(true);
            ((NodeUpdateEntity) controlValue).setUpdateAuditableProperties(true);

            auditableControlValue = new AuditablePropertiesEntity();
            auditableControlValue.setAuditCreator(AuthenticationUtil.SYSTEM_USER_NAME);
            auditableControlValue.setAuditCreated("2020-01-01T00:00:00.000Z");
            auditableControlValue.setAuditModifier("admin");
            auditableControlValue.setAuditModified("2020-01-02T00:00:00.000Z");
            controlValue.setAuditableProperties(auditableControlValue);

            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());
            auditableSerialisedValue = serialisedValue.getAuditableProperties();

            // can't check for equals - value class does not support proper equals
            // check deep serialisation was actually involved (different value instances)
            // cannot check lock state directly
            Assert.assertNotSame(controlValue, serialisedValue);
            Assert.assertEquals(controlValue.getId(), serialisedValue.getId());
            Assert.assertEquals(controlValue.getVersion(), serialisedValue.getVersion());
            Assert.assertEquals(controlValue.getUuid(), serialisedValue.getUuid());
            Assert.assertNotNull(serialisedValue.getStore());
            Assert.assertNotSame(controlValue.getStore(), serialisedValue.getStore());
            Assert.assertEquals(controlValue.getStore().getId(), serialisedValue.getStore().getId());
            Assert.assertNotNull(serialisedValue.getTransaction());
            Assert.assertNotSame(controlValue.getTransaction(), serialisedValue.getTransaction());
            Assert.assertEquals(controlValue.getTransaction().getId(), serialisedValue.getTransaction().getId());
            Assert.assertEquals(controlValue.getTypeQNameId(), serialisedValue.getTypeQNameId());
            Assert.assertEquals(controlValue.getLocaleId(), serialisedValue.getLocaleId());
            Assert.assertEquals(controlValue.getAclId(), serialisedValue.getAclId());
            Assert.assertNotNull(auditableSerialisedValue);
            Assert.assertNotSame(auditableControlValue, auditableSerialisedValue);
            Assert.assertEquals(auditableControlValue.getAuditCreator(), auditableSerialisedValue.getAuditCreator());
            Assert.assertEquals(auditableControlValue.getAuditCreated(), auditableSerialisedValue.getAuditCreated());
            Assert.assertEquals(auditableControlValue.getAuditModifier(), auditableSerialisedValue.getAuditModifier());
            Assert.assertEquals(auditableControlValue.getAuditModified(), auditableSerialisedValue.getAuditModified());
            Assert.assertEquals(auditableControlValue.getAuditAccessed(), auditableSerialisedValue.getAuditAccessed());

            Assert.assertFalse(((NodeUpdateEntity) serialisedValue).isUpdateTypeQNameId());
            Assert.assertFalse(((NodeUpdateEntity) serialisedValue).isUpdateLocaleId());
            Assert.assertFalse(((NodeUpdateEntity) serialisedValue).isUpdateAclId());
            Assert.assertFalse(((NodeUpdateEntity) serialisedValue).isUpdateTransaction());
            Assert.assertFalse(((NodeUpdateEntity) serialisedValue).isUpdateAuditableProperties());

            // check locked flag indirectly via modification
            this.expected.expect(IllegalStateException.class);
            serialisedValue.setId(-1l);
        }
        catch (final IgniteCheckedException ice)
        {
            throw new IllegalStateException("Serialisation failed in correctness test", ice);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final String serialisationType,
            final String referenceSerialisationType, final double marginFraction)
    {
        final StoreEntity storeValue;
        final TransactionEntity transactionValue;

        // we don't really need different entities for store / transaction
        // their serialisation is benchmarked in other tests
        storeValue = new StoreEntity();
        storeValue.setId(1l);
        storeValue.setVersion(1l);
        storeValue.setProtocol(StoreRef.PROTOCOL_WORKSPACE);
        storeValue.setIdentifier("SpacesStore");

        transactionValue = new TransactionEntity();
        transactionValue.setId(1l);
        transactionValue.setVersion(1l);
        transactionValue.setChangeTxnId(UUID.randomUUID().toString());
        transactionValue.setCommitTimeMs(System.currentTimeMillis());

        final SecureRandom rnJesus = new SecureRandom();

        final Supplier<NodeEntity> comparisonValueSupplier = () -> {
            final int idx = 1000 + rnJesus.nextInt(100000000);
            final NodeEntity value = new NodeEntity();
            value.setId(Long.valueOf(idx));
            value.setVersion(Long.valueOf(rnJesus.nextInt(1024)));
            value.setStore(storeValue);
            value.setUuid(UUID.randomUUID().toString());
            // systems with extensive metadata can have many QName values
            value.setTypeQNameId(Long.valueOf(rnJesus.nextInt(2048)));
            // very few locales in a system
            value.setLocaleId(Long.valueOf(rnJesus.nextInt(64)));
            // not ever node has its own ACL, so this simulates potential reuse of IDs
            value.setAclId(Long.valueOf(rnJesus.nextInt(idx + 1)));
            value.setTransaction(transactionValue);

            // random technical user IDs - creation / modification in a year's range - 25% chance to have identical values
            final AuditablePropertiesEntity auditableValue = new AuditablePropertiesEntity();
            value.setAuditableProperties(auditableValue);

            final Instant created = Instant.now().minusMillis(1000l * rnJesus.nextInt(31536000));
            final String creator = "u" + rnJesus.nextInt(10000);
            auditableValue.setAuditCreator(creator);
            auditableValue.setAuditCreated(ISO8601DateFormat.format(Date.from(created)));

            if (rnJesus.nextDouble() >= 0.75)
            {
                auditableValue.setAuditModifier(creator);
                auditableValue.setAuditModified(ISO8601DateFormat.format(Date.from(created)));
            }
            else
            {
                final Instant modified = created.plusMillis(1000l * rnJesus.nextInt(31536000));
                auditableValue.setAuditModifier("u" + rnJesus.nextInt(10000));
                auditableValue.setAuditModified(ISO8601DateFormat.format(Date.from(modified)));
            }

            return value;
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "NodeEntity", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
