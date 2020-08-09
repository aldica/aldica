/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import java.util.Date;
import java.util.UUID;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.aldica.repo.ignite.binary.base.AbstractStoreCustomBinarySerializer;
import org.alfresco.repo.domain.node.AuditablePropertiesEntity;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.repo.domain.node.NodeUpdateEntity;
import org.alfresco.repo.domain.node.StoreEntity;
import org.alfresco.repo.domain.node.TransactionEntity;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.util.EqualsHelper;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link NodeEntity}/{@link NodeUpdateEntity} instances in order to optimise their
 * serial form. This implementation primarily optimises the serial form by
 * <ol>
 * <li>not handling state which is always constant in a cache use case (the {@code locked} flag)</li>
 * <li>not handling state which is never relevant in a cache use case as its only handled e.g. by queries supporting indexing in SOLR</li>
 * <li>not handling state which is only used in DB update operations (extra flags in {@link NodeUpdateEntity})</li>
 * <li>inlining the auditable properties of the node</li>
 * <li>serialising textual audit dates as proper dates/timestamps</li>
 * <li>applying raw serialisation optimisations, if enabled</li>
 * </ol>
 *
 * @author Axel Faust
 */
public class NodeEntityBinarySerializer extends AbstractStoreCustomBinarySerializer
{

    private static final String FLAGS = "flags";

    private static final String ID = "id";

    private static final String VERSION = "version";

    private static final String STORE = "store";

    private static final String UUID = "uuid";

    private static final String UUID_MOST_SIGNIFICANT_BITS = "uuidMostSignificantBits";

    private static final String UUID_LEAST_SIGNIFICANT_BITS = "uuidLeastSignificantBits";

    private static final String TYPE_QNAME_ID = "typeQNameId";

    private static final String LOCALE_ID = "localeId";

    private static final String ACL_ID = "aclId";

    private static final String TRANSACTION = "transaction";

    private static final String AUDIT_USER = "auditUser";

    private static final String AUDIT_DATE = "auditDate";

    private static final String AUDIT_CREATOR = "auditCreator";

    private static final String AUDIT_MODIFIER = "auditModifier";

    private static final String AUDIT_CREATED = "auditCreated";

    private static final String AUDIT_MODIFIED = "auditModified";

    private static final String AUDIT_ACCESSED = "auditAccessed";

    private static final byte FLAG_NO_ACL_ID = 1;

    private static final byte FLAG_NON_STANDARD_UUID = 2;

    private static final byte FLAG_AUDIT_NULL_CREATOR = 4;

    private static final byte FLAG_AUDIT_NULL_CREATED = 8;

    private static final byte FLAG_AUDIT_NULL_MODIFIER = 16;

    private static final byte FLAG_AUDIT_NULL_MODIFIED = 32;

    private static final byte FLAG_AUDIT_NULL_ACCESSED = 64;

    private static final byte FLAG_AUDIT_IDENTICAL_VALUES = -0x80;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(NodeEntity.class) && !cls.equals(NodeUpdateEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final NodeEntity node = (NodeEntity) obj;

        byte flags = 0;

        final Long aclId = node.getAclId();
        if (aclId == null)
        {
            flags |= FLAG_NO_ACL_ID;
        }

        final String uuid = node.getUuid();
        long guidMostSignificantBits = 0;
        long guidLeastSignificantBits = 0;
        if (OPTIMISABLE_GUID_PATTERN.matcher(uuid).matches())
        {
            final String guidHex = uuid.replace("-", "");
            guidMostSignificantBits = Long.parseUnsignedLong(guidHex.substring(0, 16), 16);
            guidLeastSignificantBits = Long.parseUnsignedLong(guidHex.substring(16), 16);
        }
        else
        {
            flags |= FLAG_NON_STANDARD_UUID;
        }

        final AuditablePropertiesEntity auditableProperties = node.getAuditableProperties();
        final String creator = auditableProperties != null ? auditableProperties.getAuditCreator() : null;
        final String modifier = auditableProperties != null ? auditableProperties.getAuditModifier() : null;
        final String created = auditableProperties != null ? auditableProperties.getAuditCreated() : null;
        final String modified = auditableProperties != null ? auditableProperties.getAuditModified() : null;
        final String accessed = auditableProperties != null ? auditableProperties.getAuditAccessed() : null;

        if (creator == null)
        {
            flags |= FLAG_AUDIT_NULL_CREATOR;
        }
        if (modifier == null)
        {
            flags |= FLAG_AUDIT_NULL_MODIFIER;
        }
        if (created == null)
        {
            flags |= FLAG_AUDIT_NULL_CREATED;
        }
        if (modified == null)
        {
            flags |= FLAG_AUDIT_NULL_MODIFIED;
        }
        if (accessed == null)
        {
            flags |= FLAG_AUDIT_NULL_ACCESSED;
        }

        if (creator != null && modifier != null && created != null && modified != null && EqualsHelper.nullSafeEquals(creator, modifier)
                && EqualsHelper.nullSafeEquals(created, modified))
        {
            flags |= FLAG_AUDIT_IDENTICAL_VALUES;
        }

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();

            this.writeRawSerialForm(node, flags, guidMostSignificantBits, guidLeastSignificantBits, rawWriter);
        }
        else
        {
            this.writeDefaultSerialForm(node, flags, guidMostSignificantBits, guidLeastSignificantBits, writer);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(NodeEntity.class) && !cls.equals(NodeUpdateEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final NodeEntity node = (NodeEntity) obj;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            this.readRawSerialForm(node, rawReader);
        }
        else
        {
            this.readDefaultSerialForm(node, reader);
        }
        // nodes read from caches are always locked
        // see findByKey/findByValue in NodesCacheCallbackDAO
        // see newNodeImpl / getNodePair in AbstractNodeDAOImpl
        node.lock();
    }

    /**
     * Writes the raw serial form of a node entity.
     *
     * @param node
     *            the entity to write in raw serial form
     * @param flags
     *            the pre-calculated flags for the state of the entity
     * @param guidMostSignificantBits
     *            the most significant bits of the node's UUID, if it actually complies with
     *            {@link AbstractCustomBinarySerializer#OPTIMISABLE_GUID_PATTERN the common UUID hash format}
     * @param guidLeastSignificantBits
     *            the least significant bits of the node's UUID, if it actually complies with
     *            {@link AbstractCustomBinarySerializer#OPTIMISABLE_GUID_PATTERN the common UUID hash format}
     * @param rawWriter
     *            the writer for the raw serial form
     * @throws BinaryObjectException
     *             if any errors occur writing the serial form
     */
    protected void writeRawSerialForm(final NodeEntity node, final byte flags, final long guidMostSignificantBits,
            final long guidLeastSignificantBits, final BinaryRawWriter rawWriter) throws BinaryObjectException
    {
        rawWriter.writeByte(flags);

        // version for optimistic locking is effectively a DB ID as well
        // all IDs except ACL ID are mandatory in the DB schema
        this.writeDbId(node.getId(), rawWriter);
        this.writeDbId(node.getVersion(), rawWriter);
        rawWriter.writeObject(node.getStore());
        if ((flags & FLAG_NON_STANDARD_UUID) == FLAG_NON_STANDARD_UUID)
        {
            this.write(node.getUuid(), rawWriter);
        }
        else
        {
            rawWriter.writeLong(guidMostSignificantBits);
            rawWriter.writeLong(guidLeastSignificantBits);
        }
        this.writeDbId(node.getTypeQNameId(), rawWriter);
        this.writeDbId(node.getLocaleId(), rawWriter);
        final Long aclId = node.getAclId();
        if (aclId != null)
        {
            this.writeDbId(aclId, rawWriter);
        }
        rawWriter.writeObject(node.getTransaction());

        // Note: some migration tools / integrations are known to write weird dates, so we use regular long always, even if variable length
        // integers are enabled
        final AuditablePropertiesEntity auditableProperties = node.getAuditableProperties();
        if ((flags & FLAG_AUDIT_IDENTICAL_VALUES) == FLAG_AUDIT_IDENTICAL_VALUES)
        {
            final String creator = auditableProperties.getAuditCreator();
            final String created = auditableProperties.getAuditCreated();
            final Date createdD = DefaultTypeConverter.INSTANCE.convert(Date.class, created);

            this.write(creator, rawWriter);
            rawWriter.writeLong(createdD.getTime());
        }
        else
        {
            if ((flags & FLAG_AUDIT_NULL_CREATOR) == 0)
            {
                this.write(auditableProperties.getAuditCreator(), rawWriter);
            }
            if ((flags & FLAG_AUDIT_NULL_CREATED) == 0)
            {
                final String created = auditableProperties.getAuditCreated();
                final Date createdD = DefaultTypeConverter.INSTANCE.convert(Date.class, created);
                rawWriter.writeLong(createdD.getTime());
            }

            if ((flags & FLAG_AUDIT_NULL_MODIFIER) == 0)
            {
                this.write(auditableProperties.getAuditModifier(), rawWriter);
            }
            if ((flags & FLAG_AUDIT_NULL_MODIFIED) == 0)
            {
                final String modified = auditableProperties.getAuditModified();
                final Date modifiedD = DefaultTypeConverter.INSTANCE.convert(Date.class, modified);
                rawWriter.writeLong(modifiedD.getTime());
            }
        }

        if ((flags & FLAG_AUDIT_NULL_ACCESSED) == 0)
        {
            final String accessed = auditableProperties.getAuditAccessed();
            final Date accessedD = DefaultTypeConverter.INSTANCE.convert(Date.class, accessed);
            rawWriter.writeLong(accessedD.getTime());
        }
    }

    /**
     * Writes the default serial form of a node entity.
     *
     * @param node
     *            the entity to write in default serial form
     * @param flags
     *            the pre-calculated flags for the state of the entity
     * @param guidMostSignificantBits
     *            the most significant bits of the node's UUID, if it actually complies with
     *            {@link AbstractCustomBinarySerializer#OPTIMISABLE_GUID_PATTERN the common UUID hash format}
     * @param guidLeastSignificantBits
     *            the least significant bits of the node's UUID, if it actually complies with
     *            {@link AbstractCustomBinarySerializer#OPTIMISABLE_GUID_PATTERN the common UUID hash format}
     * @param writer
     *            the writer for the default serial form
     * @throws BinaryObjectException
     *             if any errors occur writing the serial form
     */
    protected void writeDefaultSerialForm(final NodeEntity node, final byte flags, final long guidMostSignificantBits,
            final long guidLeastSignificantBits, final BinaryWriter writer) throws BinaryObjectException
    {
        writer.writeByte(FLAGS, flags);

        // all IDs except ACL ID are mandatory in the DB schema
        writer.writeLong(ID, node.getId());
        writer.writeLong(VERSION, node.getVersion());
        writer.writeObject(STORE, node.getStore());
        if ((flags & FLAG_NON_STANDARD_UUID) == FLAG_NON_STANDARD_UUID)
        {
            writer.writeString(UUID, node.getUuid());
        }
        else
        {
            writer.writeLong(UUID_MOST_SIGNIFICANT_BITS, guidMostSignificantBits);
            writer.writeLong(UUID_LEAST_SIGNIFICANT_BITS, guidLeastSignificantBits);
        }
        writer.writeLong(TYPE_QNAME_ID, node.getTypeQNameId());
        writer.writeLong(LOCALE_ID, node.getLocaleId());
        final Long aclId = node.getAclId();
        if (aclId != null)
        {
            writer.writeLong(ACL_ID, aclId);
        }
        writer.writeObject(TRANSACTION, node.getTransaction());

        // Note: some migration tools / integrations are known to write weird dates, so we use regular long always, even if variable length
        // integers are enabled
        final AuditablePropertiesEntity auditableProperties = node.getAuditableProperties();
        if ((flags & FLAG_AUDIT_IDENTICAL_VALUES) == FLAG_AUDIT_IDENTICAL_VALUES)
        {
            final String creator = auditableProperties.getAuditCreator();
            final String dateStr = auditableProperties.getAuditCreated();
            final Date date = DefaultTypeConverter.INSTANCE.convert(Date.class, dateStr);

            writer.writeString(AUDIT_USER, creator);
            writer.writeLong(AUDIT_DATE, date.getTime());
        }
        else
        {
            if ((flags & FLAG_AUDIT_NULL_CREATOR) == 0)
            {
                writer.writeString(AUDIT_CREATOR, auditableProperties.getAuditCreator());
            }
            if ((flags & FLAG_AUDIT_NULL_CREATED) == 0)
            {
                final String dateStr = auditableProperties.getAuditCreated();
                final Date date = DefaultTypeConverter.INSTANCE.convert(Date.class, dateStr);
                writer.writeLong(AUDIT_CREATED, date.getTime());
            }

            if ((flags & FLAG_AUDIT_NULL_MODIFIER) == 0)
            {
                writer.writeString(AUDIT_MODIFIER, auditableProperties.getAuditModifier());
            }
            if ((flags & FLAG_AUDIT_NULL_MODIFIED) == 0)
            {
                final String dateStr = auditableProperties.getAuditModified();
                final Date date = DefaultTypeConverter.INSTANCE.convert(Date.class, dateStr);
                writer.writeLong(AUDIT_MODIFIED, date.getTime());
            }
        }

        if ((flags & FLAG_AUDIT_NULL_ACCESSED) == 0)
        {
            final String dateStr = auditableProperties.getAuditAccessed();
            final Date date = DefaultTypeConverter.INSTANCE.convert(Date.class, dateStr);
            writer.writeLong(AUDIT_ACCESSED, date.getTime());
        }
    }

    /**
     * Reads the state of a node entity from raw serial form.
     *
     * @param node
     *            the node in which the read state is to be stored
     * @param rawReader
     *            the reader for the raw serial form
     * @throws BinaryObjectException
     *             if an error occurs reading the serial form
     */
    protected void readRawSerialForm(final NodeEntity node, final BinaryRawReader rawReader) throws BinaryObjectException
    {
        final byte flags = rawReader.readByte();

        final long id = this.readDbId(rawReader);
        node.setId(id);
        final long version = this.readDbId(rawReader);
        node.setVersion(version);
        final StoreEntity store = rawReader.readObject();
        node.setStore(store);

        if ((flags & FLAG_NON_STANDARD_UUID) == FLAG_NON_STANDARD_UUID)
        {
            final String uuid = this.readString(rawReader);
            node.setUuid(uuid);
        }
        else
        {
            final long guidMostSignificantBits = rawReader.readLong();
            final long guidLeastSignificantBits = rawReader.readLong();
            node.setUuid(new UUID(guidMostSignificantBits, guidLeastSignificantBits).toString());
        }

        final long typeQNameId = this.readDbId(rawReader);
        node.setTypeQNameId(typeQNameId);
        final long localeId = this.readDbId(rawReader);
        node.setLocaleId(localeId);

        if ((flags & FLAG_NO_ACL_ID) == 0)
        {
            final long aclId = this.readDbId(rawReader);
            node.setAclId(aclId);
        }

        final TransactionEntity transaction = rawReader.readObject();
        node.setTransaction(transaction);

        final AuditablePropertiesEntity auditableProperties = new AuditablePropertiesEntity();
        node.setAuditableProperties(auditableProperties);

        if ((flags & FLAG_AUDIT_IDENTICAL_VALUES) == FLAG_AUDIT_IDENTICAL_VALUES)
        {
            final String user = this.readString(rawReader);
            final long stamp = rawReader.readLong();
            final Date date = new Date(stamp);
            final String dateStr = DefaultTypeConverter.INSTANCE.convert(String.class, date);

            auditableProperties.setAuditCreator(user);
            auditableProperties.setAuditCreated(dateStr);
            auditableProperties.setAuditModifier(user);
            auditableProperties.setAuditModified(dateStr);
        }
        else
        {
            if ((flags & FLAG_AUDIT_NULL_CREATOR) == 0)
            {
                final String user = this.readString(rawReader);
                auditableProperties.setAuditCreator(user);
            }
            if ((flags & FLAG_AUDIT_NULL_CREATED) == 0)
            {
                final long stamp = rawReader.readLong();
                final Date date = new Date(stamp);
                final String dateStr = DefaultTypeConverter.INSTANCE.convert(String.class, date);
                auditableProperties.setAuditCreated(dateStr);
            }

            if ((flags & FLAG_AUDIT_NULL_MODIFIER) == 0)
            {
                final String user = this.readString(rawReader);
                auditableProperties.setAuditModifier(user);
            }
            if ((flags & FLAG_AUDIT_NULL_MODIFIED) == 0)
            {
                final long stamp = rawReader.readLong();
                final Date date = new Date(stamp);
                final String dateStr = DefaultTypeConverter.INSTANCE.convert(String.class, date);
                auditableProperties.setAuditModified(dateStr);
            }
        }

        if ((flags & FLAG_AUDIT_NULL_ACCESSED) == 0)
        {
            final long stamp = rawReader.readLong();
            final Date date = new Date(stamp);
            final String dateStr = DefaultTypeConverter.INSTANCE.convert(String.class, date);
            auditableProperties.setAuditAccessed(dateStr);
        }
    }

    /**
     * Reads the state of a node entity from default serial form.
     *
     * @param node
     *            the node in which the read state is to be stored
     * @param reader
     *            the reader for the default serial form
     * @throws BinaryObjectException
     *             if an error occurs reading the serial form
     */
    protected void readDefaultSerialForm(final NodeEntity node, final BinaryReader reader) throws BinaryObjectException
    {
        final byte flags = reader.readByte(FLAGS);

        final long id = reader.readLong(ID);
        node.setId(id);
        final long version = reader.readLong(VERSION);
        node.setVersion(version);
        final StoreEntity store = reader.readObject(STORE);
        node.setStore(store);

        if ((flags & FLAG_NON_STANDARD_UUID) == FLAG_NON_STANDARD_UUID)
        {
            final String uuid = reader.readString(UUID);
            node.setUuid(uuid);
        }
        else
        {
            final long guidMostSignificantBits = reader.readLong(UUID_MOST_SIGNIFICANT_BITS);
            final long guidLeastSignificantBits = reader.readLong(UUID_LEAST_SIGNIFICANT_BITS);
            node.setUuid(new UUID(guidMostSignificantBits, guidLeastSignificantBits).toString());
        }

        final long typeQNameId = reader.readLong(TYPE_QNAME_ID);
        node.setTypeQNameId(typeQNameId);
        final long localeId = reader.readLong(LOCALE_ID);
        node.setLocaleId(localeId);

        if ((flags & FLAG_NO_ACL_ID) == 0)
        {
            final long aclId = reader.readLong(ACL_ID);
            node.setAclId(aclId);
        }

        final TransactionEntity transaction = reader.readObject(TRANSACTION);
        node.setTransaction(transaction);

        final AuditablePropertiesEntity auditableProperties = new AuditablePropertiesEntity();
        node.setAuditableProperties(auditableProperties);

        if ((flags & FLAG_AUDIT_IDENTICAL_VALUES) == FLAG_AUDIT_IDENTICAL_VALUES)
        {
            final String user = reader.readString(AUDIT_USER);
            final long stamp = reader.readLong(AUDIT_DATE);
            final Date date = new Date(stamp);
            final String dateStr = DefaultTypeConverter.INSTANCE.convert(String.class, date);

            auditableProperties.setAuditCreator(user);
            auditableProperties.setAuditCreated(dateStr);
            auditableProperties.setAuditModifier(user);
            auditableProperties.setAuditModified(dateStr);
        }
        else
        {
            if ((flags & FLAG_AUDIT_NULL_CREATOR) == 0)
            {
                final String user = reader.readString(AUDIT_CREATOR);
                auditableProperties.setAuditCreator(user);
            }
            if ((flags & FLAG_AUDIT_NULL_CREATED) == 0)
            {
                final long stamp = reader.readLong(AUDIT_CREATED);
                final Date date = new Date(stamp);
                final String dateStr = DefaultTypeConverter.INSTANCE.convert(String.class, date);
                auditableProperties.setAuditCreated(dateStr);
            }

            if ((flags & FLAG_AUDIT_NULL_MODIFIER) == 0)
            {
                final String user = reader.readString(AUDIT_MODIFIER);
                auditableProperties.setAuditModifier(user);
            }
            if ((flags & FLAG_AUDIT_NULL_MODIFIED) == 0)
            {
                final long stamp = reader.readLong(AUDIT_MODIFIED);
                final Date date = new Date(stamp);
                final String dateStr = DefaultTypeConverter.INSTANCE.convert(String.class, date);
                auditableProperties.setAuditModified(dateStr);
            }
        }

        if ((flags & FLAG_AUDIT_NULL_ACCESSED) == 0)
        {
            final long stamp = reader.readLong(AUDIT_ACCESSED);
            final Date date = new Date(stamp);
            final String dateStr = DefaultTypeConverter.INSTANCE.convert(String.class, date);
            auditableProperties.setAuditAccessed(dateStr);
        }
    }
}
