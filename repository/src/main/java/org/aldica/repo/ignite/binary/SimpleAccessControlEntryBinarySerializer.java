/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import org.alfresco.repo.security.permissions.ACEType;
import org.alfresco.repo.security.permissions.PermissionReference;
import org.alfresco.repo.security.permissions.SimpleAccessControlEntry;
import org.alfresco.repo.security.permissions.impl.SimplePermissionReference;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Instances of this class handle (de-)serialisations of {@link SimpleAccessControlEntry} instances in order to optimise their
 * serial form.
 *
 * @author Axel Faust
 */
public class SimpleAccessControlEntryBinarySerializer extends AbstractAclBinarySerializer<SimpleAccessControlEntry>
{
    // NOTE: Alfresco does not use the context property in SimpleAccessControlEntry
    // in fact, some code tests + explicitly fails if it is non-null

    private static final String FLAGS = "flags";

    private static final String AUTHORITY = "authority";

    private static final String PERMISSION_TYPE_NAME = "permissionTypeName";

    private static final String PERMISSION_TYPE_ID = "permissionTypeId";

    private static final String PERMISSION_NAME = "permissionName";

    private static final String PERMISSION_NAME_ID = "permissionNameId";

    private static final String POSITION = "position";

    private static final byte MASK_TYPE = 0x03;

    private static final byte FLAG_ALLOWED = 0x04;

    public SimpleAccessControlEntryBinarySerializer()
    {
        super(SimpleAccessControlEntry.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final SimpleAccessControlEntry entry, final BinaryWriterEx rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();
        this.writeAce(entry, out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final SimpleAccessControlEntry entry, final BinaryWriter writer)
    {
        // there are only three enum values
        // we can use it as base of a flags byte
        // and mix in allowed flag
        byte flags = (byte) entry.getAceType().getId();
        if (entry.getAccessStatus() == AccessStatus.ALLOWED)
        {
            flags |= FLAG_ALLOWED;
        }

        writer.writeByte(FLAGS, flags);
        writer.writeString(AUTHORITY, entry.getAuthority());
        final PermissionReference permission = entry.getPermission();
        if (this.useIdsWhenReasonable)
        {
            final Pair<Long, QName> qName = this.qnameDAO.getQName(permission.getQName());
            if (qName != null)
            {
                writer.writeLong(PERMISSION_TYPE_ID, qName.getFirst());
            }
            else
            {
                writer.writeObject(PERMISSION_TYPE_NAME, permission.getQName());
            }
        }
        else
        {
            writer.writeObject(PERMISSION_TYPE_NAME, permission.getQName());
        }

        final String name = permission.getName();
        final Byte id = resolvePermissionId(name);
        if (id != null)
        {
            writer.writeByte(PERMISSION_NAME_ID, id);
        }
        else
        {
            writer.writeString(PERMISSION_NAME, name);
        }

        writer.writeInt(POSITION, entry.getPosition());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final SimpleAccessControlEntry entry, final BinaryRawReader rawReader)
    {
        this.readAce(entry, rawReader);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final SimpleAccessControlEntry entry, final BinaryReader reader)
    {
        final byte flags = reader.readByte(FLAGS);
        final int typeId = flags & MASK_TYPE;
        entry.setAceType(ACEType.getACETypeFromId(typeId));

        entry.setAuthority(reader.readString(AUTHORITY));

        QName permissionType = reader.readObject(PERMISSION_TYPE_NAME);
        String permissionName = reader.readString(PERMISSION_NAME);

        if (this.useIdsWhenReasonable && permissionType == null)
        {
            final long permissionTypeId = reader.readLong(PERMISSION_TYPE_ID);
            final Pair<Long, QName> qName = this.qnameDAO.getQName(permissionTypeId);
            permissionType = qName.getSecond();
        }

        if (permissionName == null)
        {
            final byte id = reader.readByte(PERMISSION_NAME_ID);
            permissionName = resolvePermissionName(id);
        }

        entry.setPermission(SimplePermissionReference.getPermissionReference(permissionType, permissionName));
        entry.setAccessStatus((flags & FLAG_ALLOWED) == FLAG_ALLOWED ? AccessStatus.ALLOWED : AccessStatus.DENIED);
        entry.setPosition(reader.readInt(POSITION));
    }
}
