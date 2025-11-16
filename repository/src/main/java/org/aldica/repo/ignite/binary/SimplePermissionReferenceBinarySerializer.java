/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import org.alfresco.repo.security.permissions.impl.SimplePermissionReference;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Instances of this class handle (de-)serialisations of {@link SimplePermissionReference} instances in order to optimise their
 * serial form.
 *
 * @author Axel Faust
 */
public class SimplePermissionReferenceBinarySerializer extends AbstractAclBinarySerializer<SimplePermissionReference>
{

    private static final String PERMISSION_TYPE_NAME = "permissionTypeName";

    private static final String PERMISSION_TYPE_ID = "permissionTypeId";

    private static final String PERMISSION_NAME = "permissionName";

    private static final String PERMISSION_NAME_ID = "permissionNameId";

    public SimplePermissionReferenceBinarySerializer()
    {
        super(SimplePermissionReference.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final SimplePermissionReference permission, final BinaryWriterExImpl rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();
        this.writePermissionReference(permission, out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final SimplePermissionReference permission, final BinaryWriter writer)
    {
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final SimplePermissionReference permission, final BinaryRawReader rawReader)
    {
        this.readPermissionReference(permission, rawReader);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final SimplePermissionReference permission, final BinaryReader reader)
    {
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

        this.setPermissionReferenceFields(permission, permissionType, permissionName);
    }
}
