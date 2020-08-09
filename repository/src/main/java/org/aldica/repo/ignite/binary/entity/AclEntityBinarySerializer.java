/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import java.util.UUID;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.domain.permissions.AclEntity;
import org.alfresco.repo.domain.permissions.AclUpdateEntity;
import org.alfresco.repo.security.permissions.ACLType;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link AclEntity} and {@link AclUpdateEntity} instances. This implementation
 * optimises the serial form of entities by aggregating all boolean flags as well as the {@link AclEntity#getType() type} into single byte.
 * The type is safe to be inlined that way because its value range is effectively defined by an {@link ACLType enum} in Java, and since
 * there are four boolean fields in total and an integer with six distinct values, all values can be overlaid in 8 bits (bottom 4 bits for
 * booleans, 3 bits for type). Additionally, this implementation optimises the handling of the {@link AclEntity#getAclId() ACL ID} (which is
 * separate from the {@link AclEntity#getId() database ID}) by serialising its 36-character UUID string value as its two constituent long
 * values.
 *
 *
 * @author Axel Faust
 */
public class AclEntityBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String FLAGS = "flags";

    private static final String ID = "id";

    private static final String VERSION = "version";

    private static final String ACL_ID_MOST_SIGNIFICANT_BITS = "aclIdMostSignificantBits";

    private static final String ACL_ID_LEAST_SIGNIFICANT_BITS = "aclIdLeastSignificantBits";

    private static final String BOOL_TYPE_AGGREGATE = "boolTypeAggregate";

    private static final String ACL_VERSION = "aclVersion";

    private static final String INHERITS_FROM = "inheritsFrom";

    private static final String INHERITED_ACL = "inheritedAcl";

    private static final String ACL_CHANGE_SET_ID = "aclChangeSetId";

    // three fields allowed to be null per DB schema
    private static final byte FLAG_INHERITS_FROM_NULL = 1;

    private static final byte FLAG_INHERITED_ACL_NULL = 2;

    private static final byte FLAG_ACL_CHANGE_SET_NULL = 4;

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(AclEntity.class) && !cls.equals(AclUpdateEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        AclEntity entity = (AclEntity) obj;

        final String guidHex = entity.getAclId().replace("-", "");
        long guidMostSignificantBits = Long.parseUnsignedLong(guidHex.substring(0, 16), 16);
        long guidLeastSignificantBits = Long.parseUnsignedLong(guidHex.substring(16), 16);

        byte flags = 0;
        byte boolTypeAggregate = 0;

        Long inheritsFrom = entity.getInheritsFrom();
        Long inheritedAcl = entity.getInheritedAcl();
        Long aclChangeSetId = entity.getAclChangeSetId();

        if (inheritsFrom == null)
        {
            flags |= FLAG_INHERITS_FROM_NULL;
        }
        if (inheritedAcl == null)
        {
            flags |= FLAG_INHERITED_ACL_NULL;
        }
        if (aclChangeSetId == null)
        {
            flags |= FLAG_ACL_CHANGE_SET_NULL;
        }

        Integer type = entity.getType();
        // despite Boolean in API, internally these are all boolean, so always set - never null
        Boolean latest = entity.isLatest();
        Boolean inherits = entity.getInherits();
        Boolean isVersioned = entity.isVersioned();
        Boolean requiresVersion = entity.getRequiresVersion();

        boolTypeAggregate |= latest.booleanValue() ? 1 : 0;
        boolTypeAggregate |= inherits.booleanValue() ? 2 : 0;
        boolTypeAggregate |= isVersioned.booleanValue() ? 4 : 0;
        boolTypeAggregate |= requiresVersion.booleanValue() ? 8 : 0;
        boolTypeAggregate |= (byte) (type << 4);

        if (this.useRawSerialForm)
        {
            BinaryRawWriter rawWriter = writer.rawWriter();

            rawWriter.writeByte(flags);
            this.writeDbId(entity.getId(), rawWriter);
            this.writeDbId(entity.getVersion(), rawWriter);
            rawWriter.writeLong(guidMostSignificantBits);
            rawWriter.writeLong(guidLeastSignificantBits);
            rawWriter.writeByte(boolTypeAggregate);
            this.writeDbId(entity.getAclVersion(), rawWriter);
            if (inheritsFrom != null)
            {
                this.writeDbId(inheritsFrom, rawWriter);
            }
            if (inheritedAcl != null)
            {
                this.writeDbId(inheritedAcl, rawWriter);
            }
            if (aclChangeSetId != null)
            {
                this.writeDbId(aclChangeSetId, rawWriter);
            }
        }
        else
        {
            writer.writeByte(FLAGS, flags);
            writer.writeLong(ID, entity.getId());
            writer.writeLong(VERSION, entity.getVersion());
            writer.writeLong(ACL_ID_MOST_SIGNIFICANT_BITS, guidMostSignificantBits);
            writer.writeLong(ACL_ID_LEAST_SIGNIFICANT_BITS, guidLeastSignificantBits);
            writer.writeByte(BOOL_TYPE_AGGREGATE, boolTypeAggregate);
            writer.writeLong(ACL_VERSION, entity.getAclVersion());
            if (inheritsFrom != null)
            {
                writer.writeLong(INHERITS_FROM, inheritsFrom);
            }
            if (inheritedAcl != null)
            {
                writer.writeLong(INHERITED_ACL, inheritedAcl);
            }
            if (aclChangeSetId != null)
            {
                writer.writeLong(ACL_CHANGE_SET_ID, aclChangeSetId);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(AclEntity.class) && !cls.equals(AclUpdateEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        AclEntity entity = (AclEntity) obj;

        final byte flags;
        final long id;
        final long version;
        final long guidMostSignificantBits;
        final long guidLeastSignificantBits;
        final byte boolTypeAggregate;
        final long aclVersion;
        Long inheritsFrom = null;
        Long inheritedAcl = null;
        Long aclChangeSetId = null;

        if (this.useRawSerialForm)
        {
            BinaryRawReader rawReader = reader.rawReader();

            flags = rawReader.readByte();
            id = this.readDbId(rawReader);
            version = this.readDbId(rawReader);
            guidMostSignificantBits = rawReader.readLong();
            guidLeastSignificantBits = rawReader.readLong();
            boolTypeAggregate = rawReader.readByte();
            aclVersion = this.readDbId(rawReader);

            if ((flags & FLAG_INHERITS_FROM_NULL) == 0)
            {
                inheritsFrom = this.readDbId(rawReader);
            }
            if ((flags & FLAG_INHERITED_ACL_NULL) == 0)
            {
                inheritedAcl = this.readDbId(rawReader);
            }
            if ((flags & FLAG_ACL_CHANGE_SET_NULL) == 0)
            {
                aclChangeSetId = this.readDbId(rawReader);
            }
        }
        else
        {
            flags = reader.readByte(FLAGS);
            id = reader.readLong(ID);
            version = reader.readLong(VERSION);
            guidMostSignificantBits = reader.readLong(ACL_ID_MOST_SIGNIFICANT_BITS);
            guidLeastSignificantBits = reader.readLong(ACL_ID_LEAST_SIGNIFICANT_BITS);
            boolTypeAggregate = reader.readByte(BOOL_TYPE_AGGREGATE);
            aclVersion = reader.readLong(ACL_VERSION);

            if ((flags & FLAG_INHERITS_FROM_NULL) == 0)
            {
                inheritsFrom = reader.readLong(INHERITS_FROM);
            }
            if ((flags & FLAG_INHERITED_ACL_NULL) == 0)
            {
                inheritedAcl = reader.readLong(INHERITED_ACL);
            }
            if ((flags & FLAG_ACL_CHANGE_SET_NULL) == 0)
            {
                aclChangeSetId = reader.readLong(ACL_CHANGE_SET_ID);
            }
        }

        entity.setId(id);
        entity.setVersion(version);
        entity.setAclId(new UUID(guidMostSignificantBits, guidLeastSignificantBits).toString());
        entity.setLatest((boolTypeAggregate & 1) == 1);
        entity.setAclVersion(aclVersion);
        entity.setInherits((boolTypeAggregate & 2) == 2);
        entity.setInheritsFrom(inheritsFrom);
        entity.setType(boolTypeAggregate >> 4);
        entity.setInheritedAcl(inheritedAcl);
        entity.setVersioned((boolTypeAggregate & 4) == 4);
        entity.setRequiresVersion((boolTypeAggregate & 8) == 8);
        entity.setAclVersion(aclVersion);
        entity.setAclChangeSetId(aclChangeSetId);
    }
}
