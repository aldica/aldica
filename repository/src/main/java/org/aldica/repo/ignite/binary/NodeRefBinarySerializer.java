/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.util.GridUnsafe;

/**
 * Instances of this class handle (de-)serialisations of {@link NodeRef} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise handling of well-known {@link StoreRef stores} as part of the node reference, and flatten the
 * serial form - instead of writing the store as a nested, complex object - in case a custom store needs to be handled.
 *
 * @author Axel Faust
 */
public class NodeRefBinarySerializer extends AbstractExtendedBinarySerializer<NodeRef>
{

    private static final String STORE_TYPE = "storeType";

    private static final String STORE_PROTOCOL = "storeProtocol";

    private static final String STORE_ID = "storeId";

    private static final String ID = "id";

    private static final long NODE_REF_STORE_REF_FIELD_OFFSET = getFieldOffset(NodeRef.class, "storeRef", StoreRef.class);

    private static final long NODE_REF_ID_FIELD_OFFSET = getFieldOffset(NodeRef.class, "id", String.class);

    public NodeRefBinarySerializer()
    {
        super(NodeRef.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final NodeRef nodeRef, final BinaryWriterExImpl rawWriter)
    {
        this.writeNodeRef(nodeRef, rawWriter.out());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final NodeRef nodeRef, final BinaryWriter writer)
    {
        final StoreRef storeRef = nodeRef.getStoreRef();
        final String protocol = storeRef.getProtocol();
        final String identifier = storeRef.getIdentifier();

        final byte flags = this.determineStoreFlags(protocol, identifier);
        writer.writeByte(STORE_TYPE, flags);

        if ((flags & FLAG_STORE_REF_PROTO_CUSTOM) != 0)
        {
            writer.writeString(STORE_PROTOCOL, protocol);
        }

        if ((flags & FLAG_STORE_REF_ID_CUSTOM) != 0)
        {
            writer.writeString(STORE_ID, identifier);
        }

        writer.writeString(ID, nodeRef.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final NodeRef nodeRef, final BinaryRawReader rawReader)
    {
        this.readNodeRef(nodeRef, rawReader);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final NodeRef nodeRef, final BinaryReader reader)
    {
        final byte flags = reader.readByte(STORE_TYPE);

        StoreRef storeRef;
        String id;

        if ((flags & MASK_STORE_REF_CUSTOM_FLAGS) == 0)
        {
            storeRef = STORE_REF_CONSTANTS[flags & (MASK_STORE_REF_PROTO | MASK_STORE_REF_ID)];
        }
        else
        {
            final String protocol;
            final String identifier;

            if ((flags & FLAG_STORE_REF_PROTO_CUSTOM) != 0)
            {
                protocol = reader.readString(STORE_PROTOCOL);
            }
            else
            {
                protocol = STORE_REF_PROTOCOLS[flags & MASK_STORE_REF_PROTO];
            }

            if ((flags & FLAG_STORE_REF_ID_CUSTOM) != 0)
            {
                identifier = reader.readString(STORE_ID);
            }
            else
            {
                identifier = STORE_REF_IDS[(flags & MASK_STORE_REF_ID) >> 2];
            }
            storeRef = new StoreRef(protocol, identifier);
        }

        id = reader.readString(ID);

        GridUnsafe.putObjectField(nodeRef, NODE_REF_STORE_REF_FIELD_OFFSET, storeRef);
        GridUnsafe.putObjectField(nodeRef, NODE_REF_ID_FIELD_OFFSET, id);
    }

}
