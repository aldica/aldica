/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import org.aldica.repo.ignite.binary.base.AbstractStoreCustomBinarySerializer;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.repo.domain.node.StoreEntity;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link StoreEntity} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise handling of well-known {@link StoreEntity#getProtocol() protocols} and
 * {@link StoreEntity#getIdentifier() identifiers} as part of the store identity.
 *
 * @author Axel Faust
 */
public class StoreEntityBinarySerializer extends AbstractStoreCustomBinarySerializer
{

    private static final String FLAGS = "flags";

    private static final String ID = "id";

    private static final String VERSION = "version";

    private static final String ROOT_NODE = "rootNode";

    // in some use cases id, version and root node are not present
    // e.g. empty StoreEntity created in NodeEntity(NodeRef) constructor
    private static final byte FLAG_NO_ID = 1;

    private static final byte FLAG_NO_VERSION = 2;

    private static final byte FLAG_NO_ROOT_NODE = 4;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(StoreEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final StoreEntity store = (StoreEntity) obj;

        final Long id = store.getId();
        final Long version = store.getVersion();
        final NodeEntity rootNode = store.getRootNode();

        byte flags = 0;
        if (id == null)
        {
            flags |= FLAG_NO_ID;
        }
        if (version == null)
        {
            flags |= FLAG_NO_VERSION;
        }
        if (rootNode == null)
        {
            flags |= FLAG_NO_ROOT_NODE;
        }

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();

            rawWriter.writeByte(flags);
            if (id != null)
            {
                this.writeDbId(id, rawWriter);
            }
            if (version != null)
            {
                // version for optimistic locking is effectively a DB ID as well
                this.writeDbId(version, rawWriter);
            }
            this.writeStore(store.getProtocol(), store.getIdentifier(), rawWriter);
            if (rootNode != null)
            {
                rawWriter.writeObject(rootNode);
            }
        }
        else
        {
            writer.writeByte(FLAGS, flags);
            if (id != null)
            {
                writer.writeLong(ID, id);
            }
            if (version != null)
            {
                writer.writeLong(VERSION, version);
            }
            this.writeStore(store.getProtocol(), store.getIdentifier(), writer);
            if (rootNode != null)
            {
                writer.writeObject(ROOT_NODE, rootNode);
            }
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
        if (!cls.equals(StoreEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final StoreEntity store = (StoreEntity) obj;

        byte flags;
        Long id = null;
        Long version = null;
        Pair<String, String> protocolAndIdentifier;
        NodeEntity rootNode = null;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            flags = rawReader.readByte();
            if ((flags & FLAG_NO_ID) == 0)
            {
                id = this.readDbId(rawReader);
            }
            if ((flags & FLAG_NO_VERSION) == 0)
            {
                version = this.readDbId(rawReader);
            }
            protocolAndIdentifier = this.readStore(rawReader);
            if ((flags & FLAG_NO_ROOT_NODE) == 0)
            {
                rootNode = rawReader.readObject();
            }
        }
        else
        {
            flags = reader.readByte(FLAGS);
            if ((flags & FLAG_NO_ID) == 0)
            {
                id = reader.readLong(ID);
            }
            if ((flags & FLAG_NO_VERSION) == 0)
            {
                version = reader.readLong(VERSION);
            }
            protocolAndIdentifier = this.readStore(reader);
            if ((flags & FLAG_NO_ROOT_NODE) == 0)
            {
                rootNode = reader.readObject(ROOT_NODE);
            }
        }

        store.setId(id);
        store.setVersion(version);
        store.setProtocol(protocolAndIdentifier.getFirst());
        store.setIdentifier(protocolAndIdentifier.getSecond());
        store.setRootNode(rootNode);
    }

}
