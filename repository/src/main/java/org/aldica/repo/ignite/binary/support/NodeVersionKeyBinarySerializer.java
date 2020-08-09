/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.support;

import java.lang.reflect.Field;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.domain.node.NodeVersionKey;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link NodeVersionKey} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise by using the {@link #setUseVariableLengthIntegers(boolean) variable length integer} feature for
 * the database ID and version (used for optimistic locking) in the raw serial form.
 *
 * @author Axel Faust
 */
public class NodeVersionKeyBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String NODE_ID = "nodeId";

    private static final String VERSION = "version";

    private static final Field NODE_ID_FIELD;

    private static final Field VERSION_FIELD;

    static
    {
        try
        {
            NODE_ID_FIELD = NodeVersionKey.class.getDeclaredField(NODE_ID);
            VERSION_FIELD = NodeVersionKey.class.getDeclaredField(VERSION);

            NODE_ID_FIELD.setAccessible(true);
            VERSION_FIELD.setAccessible(true);
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
        if (!cls.equals(NodeVersionKey.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final NodeVersionKey nodeVersionKey = (NodeVersionKey) obj;

        final Long nodeId = nodeVersionKey.getNodeId();
        final Long version = nodeVersionKey.getVersion();

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();

            this.writeDbId(nodeId, rawWriter);
            this.write(version, true, rawWriter);
        }
        else
        {
            writer.writeLong(NODE_ID, nodeId);
            writer.writeLong(VERSION, version);
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
        if (!cls.equals(NodeVersionKey.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final long nodeId;
        final long version;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            nodeId = this.readDbId(rawReader);
            version = this.readLong(true, rawReader);
        }
        else
        {
            nodeId = reader.readLong(NODE_ID);
            version = reader.readLong(VERSION);
        }

        try
        {
            NODE_ID_FIELD.set(obj, nodeId);
            VERSION_FIELD.set(obj, version);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

}
