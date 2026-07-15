/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.util.GridUnsafe;

/**
 * Instances of this class handle (de-)serialisations of {@link StoreRef} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise handling of well-known {@link StoreRef#getProtocol() protocols} and
 * {@link StoreRef#getIdentifier() identifiers} as part of the store reference. These optimisations are intended to cover cache uses of
 * store references independent of an enclosing {@link NodeRef} which already optimises the {@link NodeRef#getStoreRef() internal store
 * reference}.
 *
 * @author Axel Faust
 */
public class StoreRefBinarySerializer extends AbstractExtendedBinarySerializer<StoreRef>
{

    private static final String TYPE = "type";

    private static final String PROTOCOL = "protocol";

    private static final String ID = "id";

    private static final long STORE_REF_PROTOCOL_FIELD_OFFSET = getFieldOffset(StoreRef.class, "protocol", String.class);

    private static final long STORE_REF_IDENTIFIER_FIELD_OFFSET = getFieldOffset(StoreRef.class, "identifier", String.class);

    public StoreRefBinarySerializer()
    {
        super(StoreRef.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final StoreRef storeRef, final BinaryWriterEx rawWriter)
    {
        this.writeStoreRef(storeRef, rawWriter.out());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final StoreRef storeRef, final BinaryWriter writer)
    {
        final String protocol = storeRef.getProtocol();
        final String identifier = storeRef.getIdentifier();

        final byte flags = this.determineStoreFlags(protocol, identifier);
        writer.writeByte(TYPE, flags);

        if ((flags & FLAG_STORE_REF_PROTO_CUSTOM) != 0)
        {
            writer.writeString(PROTOCOL, protocol);
        }

        if ((flags & FLAG_STORE_REF_ID_CUSTOM) != 0)
        {
            writer.writeString(ID, identifier);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final StoreRef storeRef, final BinaryRawReader rawReader)
    {
        this.readStoreRef(storeRef, rawReader);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final StoreRef storeRef, final BinaryReader reader)
    {
        final byte flags = reader.readByte(TYPE);

        final String protocol;
        final String identifier;

        if ((flags & FLAG_STORE_REF_PROTO_CUSTOM) != 0)
        {
            protocol = reader.readString(PROTOCOL);
        }
        else
        {
            protocol = STORE_REF_PROTOCOLS[flags & MASK_STORE_REF_PROTO];
        }

        if ((flags & FLAG_STORE_REF_ID_CUSTOM) != 0)
        {
            identifier = reader.readString(ID);
        }
        else
        {
            identifier = STORE_REF_IDS[(flags & MASK_STORE_REF_ID) >> 2];
        }

        GridUnsafe.putObjectField(storeRef, STORE_REF_PROTOCOL_FIELD_OFFSET, protocol);
        GridUnsafe.putObjectField(storeRef, STORE_REF_IDENTIFIER_FIELD_OFFSET, identifier);
    }

}
