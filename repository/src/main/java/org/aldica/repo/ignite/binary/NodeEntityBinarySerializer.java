/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.function.Consumer;

import org.alfresco.repo.domain.node.AuditablePropertiesEntity;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.repo.domain.node.StoreEntity;
import org.alfresco.repo.domain.node.TransactionEntity;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Instances of this class handle (de-)serialisations of {@link NodeEntity} instances in order to optimise their serial form.
 *
 * @author Axel Faust
 */
public class NodeEntityBinarySerializer extends AbstractExtendedBinarySerializer<NodeEntity>
{

    private static final String FLAGS = "flags";

    private static final String ID = "id";

    private static final String VERSION = "version";

    private static final String STORE_ID = "storeId";

    private static final String STORE_TYPE = "storeType";

    private static final String STORE_PROTOCOL = "storeProtocol";

    private static final String STORE_IDENTIFIER = "storeIdentifier";

    private static final String UUID = "uuid";

    private static final String TYPE_ID = "typeId";

    private static final String LOCALE_ID = "localeId";

    private static final String ACL_ID = "aclId";

    private static final String TXN_ID = "txnId";

    private static final String TXN_CHANGE_TXN_ID = "txnChangeTxnId";

    private static final String AUDITABLE_MODIFIED = "auditableModified";

    private static final String AUDITABLE_MODIFIER = "auditableModifier";

    private static final String AUDITABLE_CREATED = "auditableCreated";

    private static final String AUDITABLE_CREATOR = "auditableCreator";

    private static final String AUDITABLE_ACCESSED = "auditableAccessed";

    // reasonable flags only
    // (i.e. null flags rarely relevant even if fields are technically nullable, as incomplete entities not bound for cache)

    private static final short FLAG_ID_UNSIGNED = 0x01;

    private static final short FLAG_VERSION_UNSIGNED = 0x02;

    private static final short FLAG_STORE_ID_UNSIGNED = 0x04;

    private static final short FLAG_TYPE_ID_UNSIGNED = 0x08;

    private static final short FLAG_LOCALE_ID_UNSIGNED = 0x10;

    private static final short FLAG_ACL_ID_NULL = 0x20;

    private static final short FLAG_ACL_ID_UNSIGNED = 0x40;

    private static final short FLAG_TXN_ID_UNSIGNED = 0x80;

    private static final short FLAG_AUDITABLE_NULL = 0x100;

    private static final short FLAG_MODIFIED_NULL = 0x200;

    private static final short FLAG_MODIFIER_NULL = 0x400;

    private static final short FLAG_CREATED_NULL = 0x800;

    private static final short FLAG_CREATOR_NULL = 0x1000;

    private static final short FLAG_ACCESSED_NULL = 0x2000;

    private static final byte FLAG_SIMPLE_AUDITABLE_NULL = 0x01;

    private static final byte FLAG_SIMPLE_MODIFIED_NULL = 0x02;

    private static final byte FLAG_SIMPLE_MODIFIER_NULL = 0x04;

    private static final byte FLAG_SIMPLE_CREATED_NULL = 0x08;

    private static final byte FLAG_SIMPLE_CREATOR_NULL = 0x10;

    private static final byte FLAG_SIMPLE_ACCESSED_NULL = 0x20;

    private static final byte FLAG_SIMPLE_ACL_ID_NULL = 0x40;

    // txn version + commit time ms not part of entity in node context

    // shard key + ID not part of persistent entity

    public NodeEntityBinarySerializer()
    {
        super(NodeEntity.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final NodeEntity nodeEntity, final BinaryWriterEx rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();

        short flags = 0;

        final int startPos = out.position();
        out.unsafeEnsure(2);
        out.position(startPos + 2);

        flags |= this.writeWithFlagIfUnsigned(nodeEntity.getId(), FLAG_ID_UNSIGNED, out);
        flags |= this.writeWithFlagIfUnsigned(nodeEntity.getVersion(), FLAG_VERSION_UNSIGNED, out);

        final StoreEntity store = nodeEntity.getStore();
        flags |= this.writeWithFlagIfUnsigned(store.getId(), FLAG_STORE_ID_UNSIGNED, out);

        final byte storeFlag = this.determineStoreFlags(store.getProtocol(), store.getIdentifier());

        out.unsafeEnsure(1);
        out.unsafeWriteByte(storeFlag);

        if ((storeFlag & FLAG_STORE_REF_PROTO_CUSTOM) == FLAG_STORE_REF_PROTO_CUSTOM)
        {
            this.writeString(store.getProtocol(), out);
        }
        if ((storeFlag & FLAG_STORE_REF_ID_CUSTOM) == FLAG_STORE_REF_ID_CUSTOM)
        {
            this.writeString(store.getIdentifier(), out);
        }
        this.writeString(nodeEntity.getUuid(), out);

        flags |= this.writeWithFlagIfUnsigned(nodeEntity.getTypeQNameId(), FLAG_TYPE_ID_UNSIGNED, out);
        flags |= this.writeWithFlagIfUnsigned(nodeEntity.getLocaleId(), FLAG_LOCALE_ID_UNSIGNED, out);

        Long aclId = nodeEntity.getAclId();
        if (aclId != null)
        {
            flags |= this.writeWithFlagIfUnsigned(aclId, FLAG_ACL_ID_UNSIGNED, out);
        }
        else
        {
            flags |= FLAG_ACL_ID_NULL;
        }

        final TransactionEntity transaction = nodeEntity.getTransaction();
        flags |= this.writeWithFlagIfUnsigned(transaction.getId(), FLAG_TXN_ID_UNSIGNED, out);
        this.writeString(transaction.getChangeTxnId(), out);

        final AuditablePropertiesEntity auditableProperties = nodeEntity.getAuditableProperties();
        if (auditableProperties != null)
        {
            flags |= this.writeString(auditableProperties.getAuditModified(), FLAG_MODIFIED_NULL, out);
            flags |= this.writeString(auditableProperties.getAuditModifier(), FLAG_MODIFIER_NULL, out);
            flags |= this.writeString(auditableProperties.getAuditCreated(), FLAG_CREATED_NULL, out);
            flags |= this.writeString(auditableProperties.getAuditCreator(), FLAG_CREATOR_NULL, out);
            flags |= this.writeString(auditableProperties.getAuditAccessed(), FLAG_ACCESSED_NULL, out);
        }
        else
        {
            flags |= FLAG_AUDITABLE_NULL;
        }

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteShort(flags);
        out.position(endPos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final NodeEntity nodeEntity, final BinaryWriter writer)
    {
        byte simpleFlags = 0;

        writer.writeLong(ID, nodeEntity.getId());
        // technically, version internally wraps at Short.MAX_VALUE (see incrementVersion())
        // but column allows long
        writer.writeLong(VERSION, nodeEntity.getVersion());

        final StoreEntity store = nodeEntity.getStore();
        final byte storeFlag = this.determineStoreFlags(store.getProtocol(), store.getIdentifier());
        writer.writeLong(STORE_ID, store.getId());
        writer.writeByte(STORE_TYPE, storeFlag);

        if ((storeFlag & FLAG_STORE_REF_PROTO_CUSTOM) == FLAG_STORE_REF_PROTO_CUSTOM)
        {
            writer.writeString(STORE_PROTOCOL, store.getProtocol());
        }
        if ((storeFlag & FLAG_STORE_REF_ID_CUSTOM) == FLAG_STORE_REF_ID_CUSTOM)
        {
            writer.writeString(STORE_IDENTIFIER, store.getIdentifier());
        }
        writer.writeString(UUID, nodeEntity.getUuid());

        writer.writeLong(TYPE_ID, nodeEntity.getTypeQNameId());
        writer.writeLong(LOCALE_ID, nodeEntity.getLocaleId());
        Long aclId = nodeEntity.getAclId();
        if (aclId != null)
        {
            writer.writeLong(ACL_ID, aclId);
        }
        else
        {
            simpleFlags |= FLAG_SIMPLE_ACL_ID_NULL;
        }

        final TransactionEntity transaction = nodeEntity.getTransaction();
        writer.writeLong(TXN_ID, transaction.getId());
        writer.writeString(TXN_CHANGE_TXN_ID, transaction.getChangeTxnId());

        final AuditablePropertiesEntity auditableProperties = nodeEntity.getAuditableProperties();

        if (auditableProperties != null)
        {
            simpleFlags |= this.writeString(AUDITABLE_MODIFIED, auditableProperties.getAuditModified(), FLAG_SIMPLE_MODIFIED_NULL, writer);
            simpleFlags |= this.writeString(AUDITABLE_MODIFIER, auditableProperties.getAuditModifier(), FLAG_SIMPLE_MODIFIER_NULL, writer);
            simpleFlags |= this.writeString(AUDITABLE_CREATED, auditableProperties.getAuditCreated(), FLAG_SIMPLE_CREATED_NULL, writer);
            simpleFlags |= this.writeString(AUDITABLE_CREATOR, auditableProperties.getAuditCreator(), FLAG_SIMPLE_CREATOR_NULL, writer);
            simpleFlags |= this.writeString(AUDITABLE_ACCESSED, auditableProperties.getAuditAccessed(), FLAG_SIMPLE_ACCESSED_NULL, writer);
        }
        else
        {
            simpleFlags |= FLAG_SIMPLE_AUDITABLE_NULL;
        }

        writer.writeByte(FLAGS, simpleFlags);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final NodeEntity nodeEntity, final BinaryRawReader rawReader)
    {
        final short flags = rawReader.readShort();

        final long id = this.readLong(rawReader, (flags & FLAG_ID_UNSIGNED) == FLAG_ID_UNSIGNED);
        final long version = this.readLong(rawReader, (flags & FLAG_VERSION_UNSIGNED) == FLAG_VERSION_UNSIGNED);
        final long storeId = this.readLong(rawReader, (flags & FLAG_STORE_ID_UNSIGNED) == FLAG_STORE_ID_UNSIGNED);
        final byte storeFlag = rawReader.readByte();

        nodeEntity.setId(id);
        nodeEntity.setVersion(version);

        final StoreEntity storeEntity = new StoreEntity();
        storeEntity.setId(storeId);

        String storeProtocol;
        String storeIdentifier;

        if ((storeFlag & FLAG_STORE_REF_PROTO_CUSTOM) == FLAG_STORE_REF_PROTO_CUSTOM)
        {
            storeProtocol = this.readString(rawReader);
        }
        else
        {
            storeProtocol = STORE_REF_PROTOCOLS[storeFlag & MASK_STORE_REF_PROTO];
        }
        if ((storeFlag & FLAG_STORE_REF_ID_CUSTOM) == FLAG_STORE_REF_ID_CUSTOM)
        {
            storeIdentifier = this.readString(rawReader);
        }
        else
        {
            storeIdentifier = STORE_REF_IDS[(storeFlag & MASK_STORE_REF_ID) >> 2];
        }

        storeEntity.setProtocol(storeProtocol);
        storeEntity.setIdentifier(storeIdentifier);

        nodeEntity.setStore(storeEntity);

        final String uuid = this.readString(rawReader);
        final long typeQNameId = this.readLong(rawReader, (flags & FLAG_TYPE_ID_UNSIGNED) == FLAG_TYPE_ID_UNSIGNED);
        final long localeId = this.readLong(rawReader, (flags & FLAG_LOCALE_ID_UNSIGNED) == FLAG_LOCALE_ID_UNSIGNED);
        if ((flags & FLAG_ACL_ID_NULL) == 0)
        {
            final long aclId = this.readLong(rawReader, (flags & FLAG_ACL_ID_UNSIGNED) == FLAG_ACL_ID_UNSIGNED);
            nodeEntity.setAclId(aclId);
        }
        final long txnId = this.readLong(rawReader, (flags & FLAG_TXN_ID_UNSIGNED) == FLAG_TXN_ID_UNSIGNED);
        final String changeTxnId = this.readString(rawReader);

        nodeEntity.setUuid(uuid);
        nodeEntity.setTypeQNameId(typeQNameId);
        nodeEntity.setLocaleId(localeId);

        final TransactionEntity transaction = new TransactionEntity();
        transaction.setId(txnId);
        transaction.setChangeTxnId(changeTxnId);

        nodeEntity.setTransaction(transaction);

        if ((flags & FLAG_AUDITABLE_NULL) == 0)
        {
            final AuditablePropertiesEntity auditableProperties = new AuditablePropertiesEntity();
            this.readString(auditableProperties::setAuditModified, flags, FLAG_MODIFIED_NULL, rawReader);
            this.readString(auditableProperties::setAuditModifier, flags, FLAG_MODIFIER_NULL, rawReader);
            this.readString(auditableProperties::setAuditCreated, flags, FLAG_CREATED_NULL, rawReader);
            this.readString(auditableProperties::setAuditCreator, flags, FLAG_CREATOR_NULL, rawReader);
            this.readString(auditableProperties::setAuditAccessed, flags, FLAG_ACCESSED_NULL, rawReader);

            nodeEntity.setAuditableProperties(auditableProperties);
        }

        // entities in cache are always locked, so set this flag even though it was not serialised
        nodeEntity.lock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final NodeEntity nodeEntity, final BinaryReader reader)
    {
        final byte simpleFlags = reader.readByte(FLAGS);

        nodeEntity.setId(reader.readLong(ID));
        nodeEntity.setVersion(reader.readLong(VERSION));

        final StoreEntity storeEntity = new StoreEntity();
        storeEntity.setId(reader.readLong(STORE_ID));

        final byte storeFlag = reader.readByte(STORE_TYPE);
        String storeProtocol;
        String storeIdentifier;

        if ((storeFlag & FLAG_STORE_REF_PROTO_CUSTOM) == FLAG_STORE_REF_PROTO_CUSTOM)
        {
            storeProtocol = reader.readString(STORE_PROTOCOL);
        }
        else
        {
            storeProtocol = STORE_REF_PROTOCOLS[storeFlag & MASK_STORE_REF_PROTO];
        }
        if ((storeFlag & FLAG_STORE_REF_ID_CUSTOM) == FLAG_STORE_REF_ID_CUSTOM)
        {
            storeIdentifier = reader.readString(STORE_IDENTIFIER);
        }
        else
        {
            storeIdentifier = STORE_REF_IDS[(storeFlag & MASK_STORE_REF_ID) >> 2];
        }

        storeEntity.setProtocol(storeProtocol);
        storeEntity.setIdentifier(storeIdentifier);

        nodeEntity.setStore(storeEntity);

        nodeEntity.setUuid(reader.readString(UUID));
        nodeEntity.setTypeQNameId(reader.readLong(TYPE_ID));
        nodeEntity.setLocaleId(reader.readLong(LOCALE_ID));
        if ((simpleFlags & FLAG_SIMPLE_ACL_ID_NULL) == 0)
        {
            nodeEntity.setAclId(reader.readLong(ACL_ID));
        }

        final TransactionEntity transaction = new TransactionEntity();
        transaction.setId(reader.readLong(TXN_ID));
        transaction.setChangeTxnId(reader.readString(TXN_CHANGE_TXN_ID));

        nodeEntity.setTransaction(transaction);

        if ((simpleFlags & FLAG_SIMPLE_AUDITABLE_NULL) == 0)
        {
            final AuditablePropertiesEntity auditableProperties = new AuditablePropertiesEntity();
            this.readString(AUDITABLE_MODIFIED, auditableProperties::setAuditModified, simpleFlags, FLAG_SIMPLE_MODIFIED_NULL, reader);
            this.readString(AUDITABLE_MODIFIER, auditableProperties::setAuditModifier, simpleFlags, FLAG_SIMPLE_MODIFIER_NULL, reader);
            this.readString(AUDITABLE_CREATED, auditableProperties::setAuditCreated, simpleFlags, FLAG_SIMPLE_CREATED_NULL, reader);
            this.readString(AUDITABLE_CREATOR, auditableProperties::setAuditCreator, simpleFlags, FLAG_SIMPLE_CREATOR_NULL, reader);
            this.readString(AUDITABLE_ACCESSED, auditableProperties::setAuditAccessed, simpleFlags, FLAG_SIMPLE_ACCESSED_NULL, reader);

            nodeEntity.setAuditableProperties(auditableProperties);
        }

        // entities in cache are always locked, so set this flag even though it was not serialised
        nodeEntity.lock();
    }

    protected short writeString(final String value, final short nullFlag, final BinaryOutputStream out)
    {
        short flag = 0;
        if (value != null)
        {
            this.writeString(value, out);
        }
        else
        {
            flag |= nullFlag;
        }
        return flag;
    }

    protected void readString(final Consumer<String> valueAcceptor, final short flags, final short nullFlag,
            final BinaryRawReader rawReader)
    {
        if ((flags & nullFlag) == 0)
        {
            valueAcceptor.accept(this.readString(rawReader));
        }
    }

    protected byte writeString(final String fieldName, final String value, final byte nullFlag, final BinaryWriter writer)
    {
        byte flag = 0;
        if (value != null)
        {
            writer.writeString(fieldName, value);
        }
        else
        {
            flag |= nullFlag;
        }
        return flag;
    }

    protected void readString(final String fieldName, final Consumer<String> valueAcceptor, final byte flags, final byte nullFlag,
            final BinaryReader reader)
    {
        if ((flags & nullFlag) == 0)
        {
            valueAcceptor.accept(reader.readString(fieldName));
        }
    }
}
