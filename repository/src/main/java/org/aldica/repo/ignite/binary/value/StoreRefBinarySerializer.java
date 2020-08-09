/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.lang.reflect.Field;

import org.aldica.repo.ignite.binary.base.AbstractStoreCustomBinarySerializer;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryReader;
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
public class StoreRefBinarySerializer extends AbstractStoreCustomBinarySerializer
{

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

        if (this.useRawSerialForm)
        {
            this.writeStore(protocol, id, writer.rawWriter());
        }
        else
        {
            this.writeStore(protocol, id, writer);
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

        Pair<String, String> protocolAndIdentifier;

        if (this.useRawSerialForm)
        {
            protocolAndIdentifier = this.readStore(reader.rawReader());
        }
        else
        {
            protocolAndIdentifier = this.readStore(reader);
        }

        try
        {
            PROTOCOL_FIELD.set(obj, protocolAndIdentifier.getFirst());
            ID_FIELD.set(obj, protocolAndIdentifier.getSecond());
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

}
