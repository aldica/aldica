/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.aldica.repo.ignite.binary.support;

import java.lang.reflect.Field;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.domain.propval.AbstractPropertyValueDAOImpl.CachePucKey;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link CachePucKey} instances in order to
 * optimise their serial form. This implementation primarily aims to optimise by using the
 * {@link #setUseVariableLengthIntegers(boolean) variable length integer} feature for the key IDs
 * and and strip out the redundant hash code in the raw serial form.
 *
 * @author Axel Faust
 */
public class CachePucKeyBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String FLAGS = "flags";

    private static final String KEY_1 = "key1";

    private static final String KEY_2 = "key2";

    private static final String KEY_3 = "key3";

    private static final Field KEY_1_FIELD;

    private static final Field KEY_2_FIELD;

    private static final Field KEY_3_FIELD;

    private static final Field HASH_CODE_FIELD;

    private static final byte FLAG_KEY_1_NULL = 1;

    private static final byte FLAG_KEY_2_NULL = 2;

    private static final byte FLAG_KEY_3_NULL = 4;

    static
    {
        try
        {
            KEY_1_FIELD = CachePucKey.class.getDeclaredField(KEY_1);
            KEY_2_FIELD = CachePucKey.class.getDeclaredField(KEY_2);
            KEY_3_FIELD = CachePucKey.class.getDeclaredField(KEY_3);
            HASH_CODE_FIELD = CachePucKey.class.getDeclaredField("hashCode");

            KEY_1_FIELD.setAccessible(true);
            KEY_2_FIELD.setAccessible(true);
            KEY_3_FIELD.setAccessible(true);
            HASH_CODE_FIELD.setAccessible(true);
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
        if (!cls.equals(CachePucKey.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final CachePucKey cachePucKey = (CachePucKey) obj;

        try
        {
            final Long key1 = (Long) KEY_1_FIELD.get(cachePucKey);
            final Long key2 = (Long) KEY_2_FIELD.get(cachePucKey);
            final Long key3 = (Long) KEY_3_FIELD.get(cachePucKey);

            byte flags = 0;
            if (key1 == null)
            {
                flags |= FLAG_KEY_1_NULL;
            }
            if (key2 == null)
            {
                flags |= FLAG_KEY_2_NULL;
            }
            if (key3 == null)
            {
                flags |= FLAG_KEY_3_NULL;
            }

            if (this.useRawSerialForm)
            {
                final BinaryRawWriter rawWriter = writer.rawWriter();

                rawWriter.writeByte(flags);
                if (key1 != null)
                {
                    this.writeDbId(key1, rawWriter);
                }
                if (key2 != null)
                {
                    this.writeDbId(key2, rawWriter);
                }
                if (key3 != null)
                {
                    this.writeDbId(key3, rawWriter);
                }
            }
            else
            {
                writer.writeByte(FLAGS, flags);
                if (key1 != null)
                {
                    writer.writeLong(KEY_1, key1);
                }
                if (key2 != null)
                {
                    writer.writeLong(KEY_2, key2);
                }
                if (key3 != null)
                {
                    writer.writeLong(KEY_3, key3);
                }
            }
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to retrieve fields to write", iae);
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
        if (!cls.equals(CachePucKey.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        Long key1 = null;
        Long key2 = null;
        Long key3 = null;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            final byte flags = rawReader.readByte();
            if ((flags & FLAG_KEY_1_NULL) == 0)
            {
                key1 = this.readDbId(rawReader);
            }
            if ((flags & FLAG_KEY_2_NULL) == 0)
            {
                key2 = this.readDbId(rawReader);
            }
            if ((flags & FLAG_KEY_3_NULL) == 0)
            {
                key3 = this.readDbId(rawReader);
            }
        }
        else
        {
            final byte flags = reader.readByte(FLAGS);
            if ((flags & FLAG_KEY_1_NULL) == 0)
            {
                key1 = reader.readLong(KEY_1);
            }
            if ((flags & FLAG_KEY_2_NULL) == 0)
            {
                key2 = reader.readLong(KEY_2);
            }
            if ((flags & FLAG_KEY_3_NULL) == 0)
            {
                key3 = reader.readLong(KEY_3);
            }
        }

        // logic from CachePucKey class
        final Integer hashCode = (key1 == null ? 0 : key1.hashCode()) + (key2 == null ? 0 : key2.hashCode())
                + (key3 == null ? 0 : key3.hashCode());

        try
        {
            KEY_1_FIELD.set(obj, key1);
            KEY_2_FIELD.set(obj, key2);
            KEY_3_FIELD.set(obj, key3);
            HASH_CODE_FIELD.set(obj, hashCode);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

}
