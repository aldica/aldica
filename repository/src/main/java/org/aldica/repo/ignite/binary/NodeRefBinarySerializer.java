/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.lang.reflect.Field;

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
 * serial form - instead of writing the store as a nested, complex object - in case a custom store needs to be handled
 *
 * @author Axel Faust
 */
public class NodeRefBinarySerializer implements BinarySerializer
{

    private static final String STORE_TYPE = "storeType";

    private static final String STORE_PROTOCOL = "storeProtocol";

    private static final String STORE_ID = "storeId";

    private static final String ID = "id";

    // store constant values are the typical default IDs when Alfresco is bootstrapped, but are not treated as IDs here - rather re-used for
    // familiarity

    private static final byte USER_ALFRESCO_USER_STORE = 1;

    private static final byte SYSTEM_SYSTEM = 2;

    private static final byte WORKSPACE_LIGHT_WEIGHT_VERSION_STORE = 3;

    private static final byte WORKSPACE_VERSION2STORE = 4;

    private static final byte ARCHIVE_SPACES_STORE = 5;

    private static final byte WORKSPACE_SPACES_STORE = 6;

    private static final byte CUSTOM_STORE = 7;

    private static final String STR_USER_ALFRESCO_USER_STORE = "user://alfrescoUserStore";

    private static final String STR_SYSTEM_SYSTEM = "system://system";

    private static final String STR_WORKSPACE_LIGHT_WEIGHT_VERSION_STORE = "workspace://lightWeightVersionStore";

    private static final String STR_WORKSPACE_VERSION2STORE = "workspace://version2Store";

    private static final String STR_ARCHIVE_SPACES_STORE = "archive://SpacesStore";

    private static final String STR_WORKSPACE_SPACES_STORE = "workspace://SpacesStore";

    private static final StoreRef REF_USER_ALFRESCO_USER_STORE = new StoreRef(STR_USER_ALFRESCO_USER_STORE);

    private static final StoreRef REF_SYSTEM_SYSTEM = new StoreRef(STR_SYSTEM_SYSTEM);

    private static final StoreRef REF_WORKSPACE_LIGHT_WEIGHT_VERSION_STORE = new StoreRef(STR_WORKSPACE_LIGHT_WEIGHT_VERSION_STORE);

    private static final StoreRef REF_WORKSPACE_VERSION2STORE = new StoreRef(STR_WORKSPACE_VERSION2STORE);

    private static final StoreRef REF_ARCHIVE_SPACES_STORE = new StoreRef(STR_ARCHIVE_SPACES_STORE);

    private static final StoreRef REF_WORKSPACE_SPACES_STORE = new StoreRef(STR_WORKSPACE_SPACES_STORE);

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

    protected boolean useRawSerialForm = true;

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

        byte storeType;
        switch (storeRef.toString())
        {
            case STR_USER_ALFRESCO_USER_STORE:
                storeType = USER_ALFRESCO_USER_STORE;
                break;
            case STR_SYSTEM_SYSTEM:
                storeType = SYSTEM_SYSTEM;
                break;
            case STR_WORKSPACE_LIGHT_WEIGHT_VERSION_STORE:
                storeType = WORKSPACE_LIGHT_WEIGHT_VERSION_STORE;
                break;
            case STR_WORKSPACE_VERSION2STORE:
                storeType = WORKSPACE_VERSION2STORE;
                break;
            case STR_ARCHIVE_SPACES_STORE:
                storeType = ARCHIVE_SPACES_STORE;
                break;
            case STR_WORKSPACE_SPACES_STORE:
                storeType = WORKSPACE_SPACES_STORE;
                break;
            default:
                storeType = CUSTOM_STORE;
        }

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();
            rawWriter.writeByte(storeType);
            if (storeType == CUSTOM_STORE)
            {
                rawWriter.writeString(storeRef.getProtocol());
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

        final BinaryRawReader rawReader = reader.rawReader();

        final byte storeType = this.useRawSerialForm ? rawReader.readByte() : reader.readByte(STORE_TYPE);

        StoreRef storeRef;
        switch (storeType)
        {
            case USER_ALFRESCO_USER_STORE:
                storeRef = REF_USER_ALFRESCO_USER_STORE;
                break;
            case SYSTEM_SYSTEM:
                storeRef = REF_SYSTEM_SYSTEM;
                break;
            case WORKSPACE_LIGHT_WEIGHT_VERSION_STORE:
                storeRef = REF_WORKSPACE_LIGHT_WEIGHT_VERSION_STORE;
                break;
            case WORKSPACE_VERSION2STORE:
                storeRef = REF_WORKSPACE_VERSION2STORE;
                break;
            case ARCHIVE_SPACES_STORE:
                storeRef = REF_ARCHIVE_SPACES_STORE;
                break;
            case WORKSPACE_SPACES_STORE:
                storeRef = REF_WORKSPACE_SPACES_STORE;
                break;
            case CUSTOM_STORE:
                final String protocol = this.useRawSerialForm ? rawReader.readString() : reader.readString(STORE_PROTOCOL);
                final String identifier = this.useRawSerialForm ? rawReader.readString() : reader.readString(STORE_ID);
                storeRef = new StoreRef(protocol, identifier);
                break;
            default:
                throw new BinaryObjectException("Read unsupported store type flag value " + storeType);
        }

        final String id = this.useRawSerialForm ? rawReader.readString() : reader.readString(ID);

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
