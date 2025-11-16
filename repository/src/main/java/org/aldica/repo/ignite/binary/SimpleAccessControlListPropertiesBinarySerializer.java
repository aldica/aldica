/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import org.alfresco.repo.security.permissions.ACLType;
import org.alfresco.repo.security.permissions.SimpleAccessControlListProperties;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Instances of this class handle (de-)serialisations of {@link SimpleAccessControlListProperties} instances in order to optimise their
 * serial form.
 *
 * @author Axel Faust
 */
public class SimpleAccessControlListPropertiesBinarySerializer extends AbstractAclBinarySerializer<SimpleAccessControlListProperties>
{

    private static final String ID = "id";

    private static final String ACL_TYPE_ID = "aclTypeId";

    private static final String ACL_VERSION = "aclVersion";

    private static final String FLAGS = "flags";

    private static final String ACL_ID = "aclId";

    private static final String ACL_CHANGE_SET_ID = "aclChangeSetId";

    private static final byte FLAG_INHERITS = 0x01;

    private static final byte FLAG_LATEST = 0x02;

    private static final byte FLAG_VERSIONED = 0x04;

    public SimpleAccessControlListPropertiesBinarySerializer()
    {
        super(SimpleAccessControlListProperties.class);
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
    protected void writeRawSerialForm(final SimpleAccessControlListProperties properties, final BinaryWriterExImpl rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();
        this.writeAclProperties(properties, out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final SimpleAccessControlListProperties properties, final BinaryWriter writer)
    {
        writer.writeLong(ID, properties.getId());
        writer.writeByte(ACL_TYPE_ID, (byte) properties.getAclType().getId());
        writer.writeLong(ACL_VERSION, properties.getAclVersion());

        byte flags = 0;
        if (Boolean.TRUE.equals(properties.getInherits()))
        {
            flags |= FLAG_INHERITS;
        }
        if (Boolean.TRUE.equals(properties.isLatest()))
        {
            flags |= FLAG_LATEST;
        }
        if (Boolean.TRUE.equals(properties.isVersioned()))
        {
            flags |= FLAG_VERSIONED;
        }
        writer.writeByte(FLAGS, flags);

        writer.writeString(ACL_ID, properties.getAclId());
        writer.writeLong(ACL_CHANGE_SET_ID, properties.getAclChangeSetId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final SimpleAccessControlListProperties properties, final BinaryRawReader rawReader)
    {
        this.readAclProperties(properties, rawReader);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final SimpleAccessControlListProperties properties, final BinaryReader reader)
    {
        properties.setId(reader.readLong(ID));
        properties.setAclType(ACLType.getACLTypeFromId(reader.readByte(ACL_TYPE_ID)));
        properties.setAclVersion(reader.readLong(ACL_VERSION));

        final byte flags = reader.readByte(FLAGS);
        properties.setInherits((flags & FLAG_INHERITS) == FLAG_INHERITS);
        properties.setLatest((flags & FLAG_LATEST) == FLAG_LATEST);
        properties.setVersioned((flags & FLAG_VERSIONED) == FLAG_VERSIONED);

        properties.setAclId(reader.readString(ACL_ID));
        properties.setAclChangeSetId(reader.readLong(ACL_CHANGE_SET_ID));
    }
}
