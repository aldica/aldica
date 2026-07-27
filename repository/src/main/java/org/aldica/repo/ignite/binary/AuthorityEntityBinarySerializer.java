/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import org.alfresco.repo.domain.permissions.AuthorityEntity;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Instances of this class handle (de-)serialisations of {@link AuthorityEntity} instances in order to optimise their serial form.
 *
 * @author Axel Faust
 */
public class AuthorityEntityBinarySerializer extends AbstractBinarySerializer<AuthorityEntity>
{

    private static final String ID = "id";

    private static final String VERSION = "version";

    private static final String AUTHORITY = "authority";

    private static final String CRC = "crc";

    private static final byte FLAG_ID_UNSIGNED = 0x01;

    private static final byte FLAG_VERSION_UNSIGNED = 0x02;

    private static final byte FLAG_CRC_UNSIGNED = 0x04;

    public AuthorityEntityBinarySerializer()
    {
        super(AuthorityEntity.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final AuthorityEntity authorityEntity, final BinaryWriterEx rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();

        byte flags = 0;

        final int startPos = out.position();
        out.unsafeEnsure(1);
        out.position(startPos + 1);

        flags |= this.writeWithFlagIfUnsigned(authorityEntity.getId(), FLAG_ID_UNSIGNED, out);
        flags |= this.writeWithFlagIfUnsigned(authorityEntity.getVersion(), FLAG_VERSION_UNSIGNED, out);
        flags |= this.writeWithFlagIfUnsigned(authorityEntity.getCrc(), FLAG_CRC_UNSIGNED, out);

        this.writeString(authorityEntity.getAuthority(), out);

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteByte(flags);
        out.position(endPos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final AuthorityEntity authorityEntity, final BinaryWriter writer)
    {
        writer.writeLong(ID, authorityEntity.getId());
        writer.writeLong(VERSION, authorityEntity.getVersion());
        writer.writeString(AUTHORITY, authorityEntity.getAuthority());
        writer.writeLong(CRC, authorityEntity.getCrc());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final AuthorityEntity authorityEntity, final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        final long id = this.readLong(rawReader, (flags & FLAG_ID_UNSIGNED) == FLAG_ID_UNSIGNED);
        final long version = this.readLong(rawReader, (flags & FLAG_VERSION_UNSIGNED) == FLAG_VERSION_UNSIGNED);
        final long crc = this.readLong(rawReader, (flags & FLAG_CRC_UNSIGNED) == FLAG_CRC_UNSIGNED);

        final String authority = this.readString(rawReader);

        authorityEntity.setId(id);
        authorityEntity.setVersion(version);
        authorityEntity.setAuthority(authority);
        authorityEntity.setCrc(crc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final AuthorityEntity authorityEntity, final BinaryReader reader)
    {
        authorityEntity.setId(reader.readLong(ID));
        authorityEntity.setVersion(reader.readLong(VERSION));
        authorityEntity.setAuthority(reader.readString(AUTHORITY));
        authorityEntity.setCrc(reader.readLong(CRC));
    }
}
