/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.alfresco.repo.domain.permissions.AclEntity;
import org.alfresco.repo.security.permissions.AccessControlEntry;
import org.alfresco.repo.security.permissions.AccessControlListProperties;
import org.alfresco.repo.security.permissions.SimpleAccessControlEntry;
import org.alfresco.repo.security.permissions.SimpleAccessControlList;
import org.alfresco.repo.security.permissions.SimpleAccessControlListProperties;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Instances of this class handle (de-)serialisations of {@link SimpleAccessControlList} instances in order to optimise their serial form.
 *
 * @author Axel Faust
 */
public class SimpleAccessControlListBinarySerializer extends AbstractAclBinarySerializer<SimpleAccessControlList>
{

    private static final String PROPERTIES = "properties";

    private static final String ENTRIES = "entries";

    private static final byte FLAG_SIMPLE_ACCESS_CONTROL_PROPERTIES = 0x01;

    public SimpleAccessControlListBinarySerializer()
    {
        super(SimpleAccessControlList.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final SimpleAccessControlList acl, final BinaryWriterExImpl rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();

        final AccessControlListProperties properties = acl.getProperties();

        out.unsafeEnsure(1);
        if (properties instanceof SimpleAccessControlListProperties)
        {
            out.writeByte(FLAG_SIMPLE_ACCESS_CONTROL_PROPERTIES);
            this.writeAclProperties((SimpleAccessControlListProperties) properties, out);
        }
        else
        {
            out.writeByte((byte) 0);
            this.writeAclEntity((AclEntity) properties, out);
        }

        final List<AccessControlEntry> entries = acl.getEntries();
        this.writeUnsigned(entries.size(), out);

        for (final AccessControlEntry entry : entries)
        {
            this.writeAce((SimpleAccessControlEntry) entry, out);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final SimpleAccessControlList acl, final BinaryWriter writer)
    {
        writer.writeObject(PROPERTIES, acl.getProperties());
        writer.writeCollection(ENTRIES, acl.getEntries());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final SimpleAccessControlList acl, final BinaryRawReader rawReader)
    {
        final byte typeFlag = rawReader.readByte();

        AccessControlListProperties properties;
        if (typeFlag == 0)
        {
            properties = new AclEntity();
            this.readAclEntity((AclEntity) properties, rawReader);
        }
        else
        {
            properties = new SimpleAccessControlListProperties();
            this.readAclProperties((SimpleAccessControlListProperties) properties, rawReader);
        }
        acl.setProperties(properties);

        final int size = this.readUnsignedInt(rawReader);
        final List<AccessControlEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            final SimpleAccessControlEntry entry = new SimpleAccessControlEntry();
            this.readAce(entry, rawReader);
            entries.add(entry);
        }
        acl.setEntries(entries);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final SimpleAccessControlList acl, final BinaryReader reader)
    {
        final AccessControlListProperties properties = reader.readObject(PROPERTIES);
        acl.setProperties(properties);

        final Collection<AccessControlEntry> entries = reader.readCollection(ENTRIES);
        acl.setEntries(entries instanceof List<?> ? (List<AccessControlEntry>) entries : new ArrayList<>(entries));
    }
}
