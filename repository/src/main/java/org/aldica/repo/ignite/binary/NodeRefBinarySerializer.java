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
 * Instances of this class handle (de-)serialisations of {@link NodeRef} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise handling of well-known {@link StoreRef stores} as part of the node reference, and flatten the
 * serial form - instead of writing the store as a nested, complex object - in case a custom store needs to be handled.
 *
 * @author Axel Faust
 */
public class NodeRefBinarySerializer implements BinarySerializer
{

    public static final StoreRef REF_USER_ALFRESCO_USER_STORE = new StoreRef("user://alfrescoUserStore");

    public static final StoreRef REF_SYSTEM_SYSTEM = new StoreRef("system://system");

    public static final StoreRef REF_WORKSPACE_LIGHT_WEIGHT_VERSION_STORE = new StoreRef("workspace://lightWeightVersionStore");

    public static final StoreRef REF_WORKSPACE_VERSION2STORE = new StoreRef("workspace://version2Store");

    public static final StoreRef REF_ARCHIVE_SPACES_STORE = StoreRef.STORE_REF_ARCHIVE_SPACESSTORE;

    public static final StoreRef REF_WORKSPACE_SPACES_STORE = StoreRef.STORE_REF_WORKSPACE_SPACESSTORE;

    private static final String STORE_TYPE = "storeType";

    private static final String STORE_PROTOCOL = "storeProtocol";

    private static final String STORE_ID = "storeId";

    private static final String ID = "id";

    private static final String PROTOCOL_USER = "user";

    private static final String PROTOCOL_SYSTEM = "system";

    private static final StoreRef[] STORES = { REF_USER_ALFRESCO_USER_STORE, REF_SYSTEM_SYSTEM, REF_WORKSPACE_LIGHT_WEIGHT_VERSION_STORE,
            REF_WORKSPACE_VERSION2STORE, REF_ARCHIVE_SPACES_STORE, REF_WORKSPACE_SPACES_STORE };

    private static final String[] PROTOCOLS = { PROTOCOL_USER, PROTOCOL_SYSTEM, StoreRef.PROTOCOL_ARCHIVE, StoreRef.PROTOCOL_WORKSPACE };

    private static final byte CUSTOM_STORE = (byte) STORES.length;

    private static final byte KNOWN_PROTOCOL = (byte) (CUSTOM_STORE + 1);

    private static Map<StoreRef, Byte> KNOWN_STORES;

    private static Map<String, Byte> KNOWN_PROTOCOLS;

    static
    {
        final Map<StoreRef, Byte> knownStores = new HashMap<>(6);
        for (int idx = 0; idx < STORES.length; idx++)
        {
            knownStores.put(STORES[idx], (byte) idx);
        }
        KNOWN_STORES = Collections.unmodifiableMap(knownStores);

        final Map<String, Byte> knownProtocols = new HashMap<>(4);
        for (int idx = 0; idx < PROTOCOLS.length; idx++)
        {
            knownProtocols.put(PROTOCOLS[idx], (byte) idx);
        }
        KNOWN_PROTOCOLS = Collections.unmodifiableMap(knownProtocols);
    }

    private static final Field STORE_REF_FIELD;

    private static final Field ID_FIELD;

    static
    {
        try
        {
            STORE_REF_FIELD = NodeRef.class.getDeclaredField("storeRef");
            ID_FIELD = NodeRef.class.getDeclaredField("id");

            STORE_REF_FIELD.setAccessible(true);
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
        if (!cls.equals(NodeRef.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final NodeRef node = (NodeRef) obj;

        final StoreRef storeRef = node.getStoreRef();
        final String id = node.getId();

        Byte storeType = KNOWN_STORES.get(storeRef);
        if (storeType == null)
        {
            final Byte protocolType = KNOWN_PROTOCOLS.get(storeRef.getProtocol());
            if (protocolType != null)
            {
                storeType = Byte.valueOf((byte) (KNOWN_PROTOCOL + protocolType.byteValue()));
            }
            else
            {
                storeType = CUSTOM_STORE;
            }
        }

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();
            rawWriter.writeByte(storeType);
            if (storeType == CUSTOM_STORE)
            {
                rawWriter.writeString(storeRef.getProtocol());
            }
            if (storeType == CUSTOM_STORE || storeType >= KNOWN_PROTOCOL)
            {
                rawWriter.writeString(storeRef.getIdentifier());
            }
            rawWriter.writeString(id);
        }
        else
        {
            writer.writeByte(STORE_TYPE, storeType);
            if (storeType == CUSTOM_STORE)
            {
                writer.writeString(STORE_PROTOCOL, storeRef.getProtocol());
            }
            if (storeType == CUSTOM_STORE || storeType >= KNOWN_PROTOCOL)
            {
                writer.writeString(STORE_ID, storeRef.getIdentifier());
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
        if (!cls.equals(NodeRef.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        // need two separate branches as rawReader() sets internal flag
        // otherwise would have used ternary read, e.g. this.useRawSerialForm ? rawReader.readByte() : reader.readByte(STORE_TYPE);

        final byte storeType;
        StoreRef storeRef = null;
        String id;
        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();
            storeType = rawReader.readByte();

            if (storeType == CUSTOM_STORE)
            {
                final String protocol = rawReader.readString();
                final String identifier = rawReader.readString();
                storeRef = new StoreRef(protocol, identifier);
            }
            else if (storeType > KNOWN_PROTOCOL)
            {
                final byte protocolType = (byte) (storeType - KNOWN_PROTOCOL);
                if (protocolType >= PROTOCOLS.length || protocolType < 0)
                {
                    throw new BinaryObjectException("Read unsupported protocol flag value " + protocolType);
                }
                final String protocol = PROTOCOLS[protocolType];
                final String identifier = rawReader.readString();
                storeRef = new StoreRef(protocol, identifier);
            }
            id = rawReader.readString();
        }
        else
        {
            storeType = reader.readByte(STORE_TYPE);

            if (storeType == CUSTOM_STORE)
            {
                final String protocol = reader.readString(STORE_PROTOCOL);
                final String identifier = reader.readString(STORE_ID);
                storeRef = new StoreRef(protocol, identifier);
            }
            else if (storeType > KNOWN_PROTOCOL)
            {
                final byte protocolType = (byte) (storeType - KNOWN_PROTOCOL);
                if (protocolType >= PROTOCOLS.length || protocolType < 0)
                {
                    throw new BinaryObjectException("Read unsupported protocol flag value " + protocolType);
                }
                final String protocol = PROTOCOLS[protocolType];
                final String identifier = reader.readString(STORE_ID);
                storeRef = new StoreRef(protocol, identifier);
            }
            id = reader.readString(ID);
        }

        if (storeType < 0)
        {
            throw new BinaryObjectException("Read unsupported store type flag value " + storeType);
        }
        else if (storeType < CUSTOM_STORE)
        {
            storeRef = STORES[storeType];
        }

        try
        {
            STORE_REF_FIELD.set(obj, storeRef);
            ID_FIELD.set(obj, id);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

}
