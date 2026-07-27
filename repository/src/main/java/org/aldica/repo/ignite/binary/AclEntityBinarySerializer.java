/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import org.alfresco.repo.domain.permissions.AclEntity;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Instances of this class handle (de-)serialisations of {@link AclEntity} instances in order to optimise their serial form.
 *
 * @author Axel Faust
 */
public class AclEntityBinarySerializer extends AbstractAclBinarySerializer<AclEntity>
{

    private static final String FLAGS = "flags";

    private static final String ID = "id";

    private static final String VERSION = "version";

    private static final String ACL_ID = "aclId";

    private static final String ACL_VERSION = "aclVersion";

    private static final String INHERITS_FROM = "inheritsFrom";

    private static final String TYPE = "type";

    private static final String INHERITED_ACL = "inheritedAcl";

    private static final String ACL_CHANGE_SET = "aclChangeSet";

    private static final byte FLAG_SIMPLE_IS_LATEST = 0x01;

    private static final byte FLAG_SIMPLE_INHERITS = 0x02;

    private static final byte FLAG_SIMPLE_INHERITED_ACL_NULL = 0x04;

    private static final byte FLAG_SIMPLE_IS_VERSIONED = 0x08;

    private static final byte FLAG_SIMPLE_REQUIRES_VERSION = 0x10;

    private static final byte FLAG_SIMPLE_INHERITS_FROM_NULL = 0x20;

    public AclEntityBinarySerializer()
    {
        super(AclEntity.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUseIdsWhenReasonable(boolean useIdsWhenReasonable)
    {
        // NO-OP - not relevant
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final AclEntity aclEntity, final BinaryWriterEx rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();
        this.writeAclEntity(aclEntity, out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final AclEntity aclEntity, final BinaryWriter writer)
    {
        byte flags = 0;

        writer.writeLong(ID, aclEntity.getId());
        // technically, version internally wraps at Short.MAX_VALUE (see incrementVersion())
        // but column allows long
        writer.writeLong(VERSION, aclEntity.getVersion());
        writer.writeString(ACL_ID, aclEntity.getAclId());

        if (aclEntity.isLatest())
        {
            flags |= FLAG_SIMPLE_IS_LATEST;
        }

        writer.writeLong(ACL_VERSION, aclEntity.getAclVersion());

        if (Boolean.TRUE.equals(aclEntity.getInherits()))
        {
            flags |= FLAG_SIMPLE_INHERITS;
        }

        final Long inheritsFrom = aclEntity.getInheritsFrom();
        if (inheritsFrom != null)
        {
            writer.writeLong(INHERITS_FROM, aclEntity.getInheritsFrom());
        }
        else
        {
            flags |= FLAG_SIMPLE_INHERITS_FROM_NULL;
        }

        writer.writeInt(TYPE, aclEntity.getType());

        Long inheritedAcl = aclEntity.getInheritedAcl();
        if (inheritedAcl != null)
        {
            writer.writeLong(INHERITED_ACL, inheritedAcl);
        }
        else
        {
            flags |= FLAG_SIMPLE_INHERITED_ACL_NULL;
        }

        if (aclEntity.isVersioned())
        {
            flags |= FLAG_SIMPLE_IS_VERSIONED;
        }
        if (Boolean.TRUE.equals(aclEntity.getRequiresVersion()))
        {
            flags |= FLAG_SIMPLE_REQUIRES_VERSION;
        }

        writer.writeLong(ACL_CHANGE_SET, aclEntity.getAclChangeSetId());

        writer.writeByte(FLAGS, flags);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final AclEntity aclEntity, final BinaryRawReader rawReader)
    {
        this.readAclEntity(aclEntity, rawReader);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final AclEntity aclEntity, final BinaryReader reader)
    {
        final byte simpleFlags = reader.readByte(FLAGS);

        aclEntity.setId(reader.readLong(ID));
        aclEntity.setVersion(reader.readLong(VERSION));
        aclEntity.setAclId(reader.readString(ACL_ID));
        aclEntity.setLatest((simpleFlags & FLAG_SIMPLE_IS_LATEST) == FLAG_SIMPLE_IS_LATEST);
        aclEntity.setAclVersion(reader.readLong(ACL_VERSION));
        aclEntity.setInherits((simpleFlags & FLAG_SIMPLE_INHERITS) == FLAG_SIMPLE_INHERITS);

        if ((simpleFlags & FLAG_SIMPLE_INHERITS_FROM_NULL) != FLAG_SIMPLE_INHERITS_FROM_NULL)
        {
            aclEntity.setInheritsFrom(reader.readLong(INHERITS_FROM));
        }

        aclEntity.setType(reader.readInt(TYPE));
        if ((simpleFlags & FLAG_SIMPLE_INHERITED_ACL_NULL) == 0)
        {
            aclEntity.setInheritedAcl(reader.readLong(INHERITED_ACL));
        }
        aclEntity.setVersioned((simpleFlags & FLAG_SIMPLE_IS_VERSIONED) == FLAG_SIMPLE_IS_VERSIONED);
        aclEntity.setRequiresVersion((simpleFlags & FLAG_SIMPLE_REQUIRES_VERSION) == FLAG_SIMPLE_REQUIRES_VERSION);
        aclEntity.setAclChangeSetId(reader.readLong(ACL_CHANGE_SET));
    }
}
