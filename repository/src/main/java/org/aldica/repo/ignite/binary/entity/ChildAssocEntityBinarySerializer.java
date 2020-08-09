/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.domain.node.ChildAssocEntity;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link ChildAssocEntity} instances in order to optimise their serial form. This
 * implementation primarily optimises the serial form by not handling state which is only used for ibatis queries and applying raw
 * serialisation optimisations, if enabled.
 *
 * @author Axel Faust
 */
public class ChildAssocEntityBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String FLAGS = "flags";

    private static final String ID = "id";

    private static final String VERSION = "version";

    private static final String PARENT_NODE = "parentNode";

    private static final String CHILD_NODE = "childNode";

    private static final String TYPE_QNAME_ID = "typeQNameId";

    private static final String CHILD_NODE_NAME_CRC = "childNodeNameCrc";

    private static final String CHILD_NODE_NAME = "childNodeName";

    private static final String QNAME_NAMESPACE_ID = "qnameNamespaceId";

    private static final String QNAME_LOCAL_NAME = "qnameLocalName";

    private static final String QNAME_CRC = "qnameCrc";

    private static final String IS_PRIMARY = "isPrimary";

    private static final String ASSOC_INDEX = "assocIndex";

    // though technically mandatory due to NOT NULL constraint, the version does not get retrieved in select_ChildAssoc_Results
    private static final byte FLAG_NO_VERSION = 1;

    // technically not mandatory due to missing NOT NULL constraint
    private static final byte FLAG_NO_PRIMARY = 2;

    // assoc_index is technically not mandatory due to missing NOT NULL constraint, but value type is int, so cannot be null in Java

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(ChildAssocEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final ChildAssocEntity childAssoc = (ChildAssocEntity) obj;

        byte flags = 0;
        if (childAssoc.getVersion() == null)
        {
            flags |= FLAG_NO_VERSION;
        }
        if (childAssoc.isPrimary() == null)
        {
            flags |= FLAG_NO_PRIMARY;
        }

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();

            rawWriter.writeByte(flags);
            this.writeDbId(childAssoc.getId(), rawWriter);
            if (childAssoc.getVersion() != null)
            {
                this.writeDbId(childAssoc.getVersion(), rawWriter);
            }
            rawWriter.writeObject(childAssoc.getParentNode());
            rawWriter.writeObject(childAssoc.getChildNode());
            this.writeDbId(childAssoc.getTypeQNameId(), rawWriter);
            // CRC-32 does not need a full long
            this.write(childAssoc.getChildNodeNameCrc(), false, rawWriter);
            this.write(childAssoc.getChildNodeName(), rawWriter);
            this.writeDbId(childAssoc.getQnameNamespaceId(), rawWriter);
            this.write(childAssoc.getQnameLocalName(), rawWriter);
            this.write(childAssoc.getQnameCrc(), false, rawWriter);
            if (childAssoc.isPrimary() != null)
            {
                rawWriter.writeBoolean(childAssoc.isPrimary());
            }
            // assocIndex = -1 used for "no index"
            this.write(childAssoc.getAssocIndex(), false, rawWriter);
        }
        else
        {
            writer.writeByte(FLAGS, flags);
            writer.writeLong(ID, childAssoc.getId());
            if (childAssoc.getVersion() != null)
            {
                writer.writeLong(VERSION, childAssoc.getVersion());
            }
            writer.writeObject(PARENT_NODE, childAssoc.getParentNode());
            writer.writeObject(CHILD_NODE, childAssoc.getChildNode());
            writer.writeLong(TYPE_QNAME_ID, childAssoc.getTypeQNameId());
            writer.writeLong(CHILD_NODE_NAME_CRC, childAssoc.getChildNodeNameCrc());
            writer.writeString(CHILD_NODE_NAME, childAssoc.getChildNodeName());
            writer.writeLong(QNAME_NAMESPACE_ID, childAssoc.getQnameNamespaceId());
            writer.writeString(QNAME_LOCAL_NAME, childAssoc.getQnameLocalName());
            writer.writeLong(QNAME_CRC, childAssoc.getQnameCrc());
            if (childAssoc.isPrimary() != null)
            {
                writer.writeBoolean(IS_PRIMARY, childAssoc.isPrimary());
            }
            writer.writeInt(ASSOC_INDEX, childAssoc.getAssocIndex());
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("deprecation")
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(ChildAssocEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final ChildAssocEntity childAssoc = (ChildAssocEntity) obj;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            final byte flags = rawReader.readByte();
            childAssoc.setId(this.readDbId(rawReader));
            if ((flags & FLAG_NO_VERSION) == 0)
            {
                childAssoc.setVersion(this.readDbId(rawReader));
            }
            childAssoc.setParentNode(rawReader.readObject());
            childAssoc.setChildNode(rawReader.readObject());
            // somewhat non-standard that this type of setter is deprecated on an entity class
            // the alternative setTypeQNameAll uses a pattern not used anywhere else in Alfresco
            childAssoc.setTypeQNameId(this.readDbId(rawReader));
            // equally as strange - none of these deprecated methods can be removed anyway as long as ibatis needs them for ORM
            childAssoc.setChildNodeNameCrc(this.readLong(false, rawReader));
            childAssoc.setChildNodeName(this.readString(rawReader));
            childAssoc.setQnameNamespaceId(this.readDbId(rawReader));
            childAssoc.setQnameLocalName(this.readString(rawReader));
            childAssoc.setQnameCrc(this.readLong(false, rawReader));
            if ((flags & FLAG_NO_PRIMARY) == 0)
            {
                childAssoc.setPrimary(rawReader.readBoolean());
            }
            childAssoc.setAssocIndex(this.readInt(false, rawReader));
        }
        else
        {
            final byte flags = reader.readByte(FLAGS);
            childAssoc.setId(reader.readLong(ID));
            if ((flags & FLAG_NO_VERSION) == 0)
            {
                childAssoc.setVersion(reader.readLong(VERSION));
            }
            childAssoc.setParentNode(reader.readObject(PARENT_NODE));
            childAssoc.setChildNode(reader.readObject(CHILD_NODE));
            // somewhat non-standard that this type of setter is deprecated on an entity class
            // the alternative setTypeQNameAll uses a pattern not used anywhere else in Alfresco
            childAssoc.setTypeQNameId(reader.readLong(TYPE_QNAME_ID));
            // equally as strange - none of these deprecated methods can be removed anyway as long as ibatis needs them for ORM
            childAssoc.setChildNodeNameCrc(reader.readLong(CHILD_NODE_NAME_CRC));
            childAssoc.setChildNodeName(reader.readString(CHILD_NODE_NAME));
            childAssoc.setQnameNamespaceId(reader.readLong(QNAME_NAMESPACE_ID));
            childAssoc.setQnameLocalName(reader.readString(QNAME_LOCAL_NAME));
            childAssoc.setQnameCrc(reader.readLong(QNAME_CRC));
            if ((flags & FLAG_NO_PRIMARY) == 0)
            {
                childAssoc.setPrimary(reader.readBoolean(IS_PRIMARY));
            }
            childAssoc.setAssocIndex(reader.readInt(ASSOC_INDEX));
        }
    }
}
