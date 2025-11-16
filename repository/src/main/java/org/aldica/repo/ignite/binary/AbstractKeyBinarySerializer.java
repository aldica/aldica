/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.io.Serializable;

import org.alfresco.repo.domain.node.NodeVersionKey;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Instances of this class provide additional (de-)serialisation support for key-related data.
 *
 * @author Axel Faust
 */
public abstract class AbstractKeyBinarySerializer<T> extends AbstractExtendedBinarySerializer<T>
{

    private static final short MASK_KEY_TYPE = 0x3fc0;

    @SuppressWarnings("unused")
    private static final short FLAG_KEY_TYPE_LONG = 0x0040;

    private static final short FLAG_KEY_TYPE_STRING = 0x0080;

    private static final short FLAG_KEY_TYPE_STOREREF = 0x00c0;

    private static final short FLAG_KEY_TYPE_NODEREF = 0x0100;

    // TODO granular serializer for NodeVersionKey
    private static final short FLAG_KEY_TYPE_NODE_VERSION = 0x0140;

    private static final short FLAG_KEY_TYPE_OBJECT = 0x0180;

    private static final short FLAG_KEY_TYPE_UNSIGNED_1 = 0x4000;

    private static final short FLAG_KEY_TYPE_UNSIGNED_2 = Short.MIN_VALUE;

    public AbstractKeyBinarySerializer(final Class<T> handledType)
    {
        super(handledType);
    }

    protected short writeKey(final Serializable key, final BinaryRawWriter rawWriter, final BinaryOutputStream out)
    {
        short flags = 0;

        if (key instanceof Long)
        {
            flags |= FLAG_KEY_TYPE_LONG;
            flags |= this.writeWithFlagIfUnsigned(((Long) key).longValue(), FLAG_KEY_TYPE_UNSIGNED_1, out);
        }
        else if (key instanceof String)
        {
            flags |= FLAG_KEY_TYPE_STRING;
            this.writeString((String) key, out);
        }
        else if (key instanceof StoreRef)
        {
            flags |= FLAG_KEY_TYPE_STOREREF;
            this.writeStoreRef((StoreRef) key, out);
        }
        else if (key instanceof NodeRef)
        {
            flags |= FLAG_KEY_TYPE_NODEREF;
            this.writeNodeRef((NodeRef) key, out);
        }
        else if (key instanceof NodeVersionKey)
        {
            flags |= FLAG_KEY_TYPE_NODE_VERSION;
            final NodeVersionKey version = (NodeVersionKey) key;
            flags |= this.writeWithFlagIfUnsigned(version.getNodeId().longValue(), FLAG_KEY_TYPE_UNSIGNED_1, out);
            flags |= this.writeWithFlagIfUnsigned(version.getVersion().longValue(), FLAG_KEY_TYPE_UNSIGNED_2, out);
        }
        else
        {
            flags |= FLAG_KEY_TYPE_OBJECT;
            rawWriter.writeObject(key);
        }

        return flags;
    }

    protected Serializable readKey(final BinaryRawReader rawReader, final short flags)
    {
        final Serializable key;

        switch (flags & MASK_KEY_TYPE)
        {
            case FLAG_KEY_TYPE_LONG:
                key = Long.valueOf(this.readLong(rawReader, (flags & FLAG_KEY_TYPE_UNSIGNED_1) == FLAG_KEY_TYPE_UNSIGNED_1));
                break;
            case FLAG_KEY_TYPE_STRING:
                key = this.readString(rawReader);
                break;
            case FLAG_KEY_TYPE_STOREREF:
                key = this.readStoreRef(rawReader);
                break;
            case FLAG_KEY_TYPE_NODEREF:
                key = this.readNodeRef(rawReader);
                break;
            case FLAG_KEY_TYPE_NODE_VERSION:
                final Long nodeId = Long.valueOf(this.readLong(rawReader, (flags & FLAG_KEY_TYPE_UNSIGNED_1) == FLAG_KEY_TYPE_UNSIGNED_1));
                final Long version = Long.valueOf(this.readLong(rawReader, (flags & FLAG_KEY_TYPE_UNSIGNED_2) == FLAG_KEY_TYPE_UNSIGNED_2));
                key = new NodeVersionKey(nodeId, version);
                break;
            default:
                key = rawReader.readObject();
        }

        return key;
    }
}
