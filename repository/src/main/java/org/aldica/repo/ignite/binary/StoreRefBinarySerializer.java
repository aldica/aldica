/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link StoreRef} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise handling of well-known {@link StoreRef#getProtocol() protocols} and
 * {@link StoreRef#getIdentifier() identifiers} as part of the store reference. These optimisations are intended to cover cache uses of
 * store references independent of an enclosing {@link NodeRef} which already optimises the {@link NodeRef#getStoreRef() internal store
 * reference}.
 *
 * @author Axel Faust
 */
public class StoreRefBinarySerializer implements BinarySerializer
{

    private static final String TYPE = "type";

    private static final String PROTOCOL = "protocol";

    private static final String ID = "id";

    private static final String PROTOCOL_USER = "user";

    private static final String PROTOCOL_SYSTEM = "system";

    private static final String[] PROTOCOLS = { PROTOCOL_USER, PROTOCOL_SYSTEM, StoreRef.PROTOCOL_ARCHIVE, StoreRef.PROTOCOL_WORKSPACE };

    private static final byte CUSTOM_PROTOCOL = (byte) PROTOCOLS.length;

    private static Map<String, Byte> KNOWN_PROTOCOLS;

    static
    {
        final Map<String, Byte> knownProtocols = new HashMap<>(4);
        for (int idx = 0; idx < PROTOCOLS.length; idx++)
        {
            knownProtocols.put(PROTOCOLS[idx], (byte) idx);
        }
        KNOWN_PROTOCOLS = Collections.unmodifiableMap(knownProtocols);
    }

    private static final Field PROTOCOL_FIELD;

    private static final Field ID_FIELD;

    static
    {
        try
        {
            PROTOCOL_FIELD = StoreRef.class.getDeclaredField("protocol");
            ID_FIELD = StoreRef.class.getDeclaredField("identifier");

            PROTOCOL_FIELD.setAccessible(true);
            ID_FIELD.setAccessible(true);
        }
        catch (final NoSuchFieldException nsfe)
        {
            throw new RuntimeException("Failed to initialise reflective field accessors", nsfe);
        }
    }

    protected boolean useRawSerialForm = false;

    /**
     * @param useRawSerialForm
     *            the useRawSerialForm to set
     */
    public void setUseRawSerialForm(final boolean useRawSerialForm)
    {
        this.useRawSerialForm = useRawSerialForm;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(StoreRef.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final StoreRef store = (StoreRef) obj;

        final String protocol = store.getProtocol();
        final String id = store.getIdentifier();

        final byte protocolType = KNOWN_PROTOCOLS.getOrDefault(protocol, CUSTOM_PROTOCOL);

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();
            rawWriter.writeByte(protocolType);
            if (protocolType == CUSTOM_PROTOCOL)
            {
                rawWriter.writeString(protocol);
            }
            rawWriter.writeString(id);
        }
        else
        {
            writer.writeByte(TYPE, protocolType);
            if (protocolType == CUSTOM_PROTOCOL)
            {
                writer.writeString(PROTOCOL, protocol);
            }
            writer.writeString(ID, id);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(StoreRef.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        // need two separate branches as rawReader() sets internal flag
        // otherwise would have used ternary read, e.g. this.useRawSerialForm ? rawReader.readByte() : reader.readByte(STORE_TYPE);

        final byte protocolType;
        String protocol = null;
        String id;
        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();
            protocolType = rawReader.readByte();

            if (protocolType == CUSTOM_PROTOCOL)
            {
                protocol = rawReader.readString();
            }
            id = rawReader.readString();
        }
        else
        {
            protocolType = reader.readByte(TYPE);

            if (protocolType == CUSTOM_PROTOCOL)
            {
                protocol = reader.readString(PROTOCOL);
            }
            id = reader.readString(ID);
        }

        if (protocolType > CUSTOM_PROTOCOL || protocolType < 0)
        {
            throw new BinaryObjectException("Read unsupported protocol flag value " + protocolType);
        }
        else if (protocolType != CUSTOM_PROTOCOL)
        {
            protocol = PROTOCOLS[protocolType];
        }

        try
        {
            PROTOCOL_FIELD.set(obj, protocol);
            ID_FIELD.set(obj, id);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

}
