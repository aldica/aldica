/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import org.alfresco.repo.domain.permissions.PermissionEntity;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Instances of this class handle (de-)serialisations of {@link PermissionEntity} instances in order to optimise their serial form.
 *
 * @author Axel Faust
 */
public class PermissionEntityBinarySerializer extends AbstractBinarySerializer<PermissionEntity>
{

    private static final String ID = "id";

    private static final String VERSION = "version";

    private static final String TYPE_QNAME_ID = "typeQNameId";

    private static final String NAME = "name";

    private static final byte FLAG_ID_UNSIGNED = 0x01;

    private static final byte FLAG_VERSION_UNSIGNED = 0x02;

    private static final byte FLAG_TYPE_QNAME_ID_UNSIGNED = 0x04;

    public PermissionEntityBinarySerializer()
    {
        super(PermissionEntity.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final PermissionEntity permissionEntity, final BinaryWriterEx rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();

        byte flags = 0;

        final int startPos = out.position();
        out.unsafeEnsure(1);
        out.position(startPos + 1);

        flags |= this.writeWithFlagIfUnsigned(permissionEntity.getId(), FLAG_ID_UNSIGNED, out);
        flags |= this.writeWithFlagIfUnsigned(permissionEntity.getVersion(), FLAG_VERSION_UNSIGNED, out);
        flags |= this.writeWithFlagIfUnsigned(permissionEntity.getTypeQNameId(), FLAG_TYPE_QNAME_ID_UNSIGNED, out);

        this.writeString(permissionEntity.getName(), out);

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteByte(flags);
        out.position(endPos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final PermissionEntity permissionEntity, final BinaryWriter writer)
    {
        writer.writeLong(ID, permissionEntity.getId());
        writer.writeLong(VERSION, permissionEntity.getVersion());
        writer.writeLong(TYPE_QNAME_ID, permissionEntity.getTypeQNameId());
        writer.writeString(NAME, permissionEntity.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final PermissionEntity permissionEntity, final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        final long id = this.readLong(rawReader, (flags & FLAG_ID_UNSIGNED) == FLAG_ID_UNSIGNED);
        final long version = this.readLong(rawReader, (flags & FLAG_VERSION_UNSIGNED) == FLAG_VERSION_UNSIGNED);
        final long typeQNameId = this.readLong(rawReader, (flags & FLAG_TYPE_QNAME_ID_UNSIGNED) == FLAG_TYPE_QNAME_ID_UNSIGNED);

        final String name = this.readString(rawReader);

        permissionEntity.setId(id);
        permissionEntity.setVersion(version);
        permissionEntity.setTypeQNameId(typeQNameId);
        permissionEntity.setName(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final PermissionEntity permissionEntity, final BinaryReader reader)
    {
        permissionEntity.setId(reader.readLong(ID));
        permissionEntity.setVersion(reader.readLong(VERSION));
        permissionEntity.setTypeQNameId(reader.readLong(TYPE_QNAME_ID));
        permissionEntity.setName(reader.readString(NAME));
    }
}
