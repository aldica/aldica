/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.repo.cache.TransactionalCache;
import org.alfresco.repo.cache.TransactionalCache.ValueHolder;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
import org.apache.ignite.internal.util.GridUnsafe;

/**
 * Instances of this class handle (de-)serialisations of {@link TransactionalCache transactional cache} {@link ValueHolder value holder}
 * instances into slightly more efficient binary representations by using single bytes in place of known {@code null} or {@code not found}
 * sentinel values.
 *
 * @author Axel Faust
 */
@SuppressWarnings("rawtypes")
public class TransactionalCacheValueHolderBinarySerializer extends AbstractExtendedBinarySerializer<ValueHolder>
{

    private static final String FLAGS = "flags";

    private static final String RAND = "rand";

    private static final String VALUE = "value";

    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<>();

    // copied from EntityLookupCache
    private static final Serializable VALUE_NULL = "@@VALUE_NULL@@";

    private static final Serializable VALUE_NOT_FOUND = "@@VALUE_NOT_FOUND@@";

    private static final byte MASK_TYPE = 0x3f;

    private static final byte FLAG_TYPE_NULL_ACTUAL = 0x00;

    private static final byte FLAG_TYPE_NULL_SENTINEL = 0x01;

    private static final byte FLAG_TYPE_NOT_FOUND_SENTINEL = 0x02;

    private static final byte FLAG_TYPE_STRING = 0x03;

    private static final byte FLAG_TYPE_LONG = 0x04;

    private static final byte FLAG_TYPE_INT = 0x05;

    private static final byte FLAG_TYPE_FLOAT = 0x06;

    private static final byte FLAG_TYPE_DOUBLE = 0x07;

    private static final byte FLAG_TYPE_DATE = 0x08;

    private static final byte FLAG_TYPE_CLASS = 0x09;

    private static final byte FLAG_TYPE_QNAME = 0x0a;

    private static final byte FLAG_TYPE_NODEREF = 0x0b;

    private static final byte FLAG_TYPE_STOREREF = 0x0c;

    // room for more direct value types

    private static final byte FLAG_TYPE_OBJECT = 0x3f;

    private static final byte FLAG_VALUE_UNSIGNED = 0x40;

    private static final byte FLAG_RAND_UNSIGNED = -128;

    private static final long RAND_FIELD_OFFSET = getFieldOffset(ValueHolder.class, RAND, int.class);

    private static final long VALUE_FIELD_OFFSET = getFieldOffset(ValueHolder.class, VALUE, Object.class);

    public TransactionalCacheValueHolderBinarySerializer()
    {
        super(ValueHolder.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final ValueHolder valueHolder, final BinaryWriterEx rawWriter) throws BinaryObjectException
    {
        final BinaryOutputStream out = rawWriter.out();

        final int startPos = out.position();
        out.unsafeEnsure(1);
        out.position(startPos + 1);

        byte flags = 0;

        final int rand = valueHolder.hashCode();
        final Object value = valueHolder.getValue();

        flags |= this.writeWithFlagIfUnsigned(rand, FLAG_RAND_UNSIGNED, out);

        if (value instanceof String)
        {
            if (VALUE_NULL.equals(value))
            {
                flags |= FLAG_TYPE_NULL_SENTINEL;
            }
            else if (VALUE_NOT_FOUND.equals(value))
            {
                flags |= FLAG_TYPE_NOT_FOUND_SENTINEL;
            }
            else
            {
                flags |= FLAG_TYPE_STRING;
                this.writeString((String) value, out);
            }
        }
        else if (value instanceof Long)
        {
            flags |= FLAG_TYPE_LONG;
            flags |= this.writeWithFlagIfUnsigned(((Long) value).longValue(), FLAG_VALUE_UNSIGNED, out);
        }
        else if (value instanceof Integer)
        {
            flags |= FLAG_TYPE_INT;
            flags |= this.writeWithFlagIfUnsigned(((Integer) value).intValue(), FLAG_VALUE_UNSIGNED, out);
        }
        else if (value instanceof Double)
        {
            flags |= FLAG_TYPE_DOUBLE;
            out.writeDouble(((Double) value).doubleValue());
        }
        else if (value instanceof Float)
        {
            flags |= FLAG_TYPE_FLOAT;
            out.writeDouble(((Float) value).floatValue());
        }
        else if (value instanceof Date)
        {
            flags |= FLAG_TYPE_DATE;
            final long time = ((Date) value).getTime();
            flags |= this.writeWithFlagIfUnsigned(time, FLAG_VALUE_UNSIGNED, out);
        }
        else if (value instanceof Class<?>)
        {
            flags |= FLAG_TYPE_CLASS;
            this.writeString(((Class<?>) value).getName(), out);
        }
        else if (value instanceof QName)
        {
            flags |= FLAG_TYPE_QNAME;
            this.writeQName((QName) value, out);
        }
        else if (value instanceof NodeRef)
        {
            flags |= FLAG_TYPE_NODEREF;
            this.writeNodeRef((NodeRef) value, out);
        }
        else if (value instanceof StoreRef)
        {
            flags |= FLAG_TYPE_STOREREF;
            this.writeStoreRef((StoreRef) value, out);
        }
        else if (value != null)
        {
            flags |= FLAG_TYPE_OBJECT;
            rawWriter.writeObject(value);
        }

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteByte(flags);
        out.position(endPos);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final ValueHolder valueHolder, final BinaryWriter writer)
    {
        final int rand = valueHolder.hashCode();
        final Object value = valueHolder.getValue();

        writer.writeInt(RAND, rand);

        byte flags = 0;

        if (value instanceof String)
        {
            if (VALUE_NULL.equals(value))
            {
                flags |= FLAG_TYPE_NULL_SENTINEL;
            }
            else if (VALUE_NOT_FOUND.equals(value))
            {
                flags |= FLAG_TYPE_NOT_FOUND_SENTINEL;
            }
            else
            {
                flags |= FLAG_TYPE_STRING;
                writer.writeString(VALUE, (String) value);
            }
        }
        else if (value instanceof Long)
        {
            flags |= FLAG_TYPE_LONG;
            writer.writeLong(VALUE, (Long) value);
        }
        else if (value instanceof Integer)
        {
            flags |= FLAG_TYPE_INT;
            writer.writeInt(VALUE, (Integer) value);
        }
        else if (value instanceof Double)
        {
            flags |= FLAG_TYPE_DOUBLE;
            writer.writeDouble(VALUE, (Double) value);
        }
        else if (value instanceof Float)
        {
            flags |= FLAG_TYPE_FLOAT;
            writer.writeFloat(VALUE, (Float) value);
        }
        else if (value instanceof Date)
        {
            flags |= FLAG_TYPE_DATE;
            writer.writeLong(VALUE, ((Date) value).getTime());
        }
        else if (value instanceof Class<?>)
        {
            flags |= FLAG_TYPE_CLASS;
            writer.writeString(VALUE, ((Class<?>) value).getName());
        }
        else if (value != null)
        {
            // not other optimisations
            flags |= FLAG_TYPE_OBJECT;
            writer.writeObject(VALUE, value);
        }

        writer.writeByte(FLAGS, flags);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final ValueHolder valueHolder, final BinaryRawReader rawReader) throws BinaryObjectException
    {
        final byte flags = rawReader.readByte();
        final int rand = this.readInt(rawReader, (flags & FLAG_RAND_UNSIGNED) == FLAG_RAND_UNSIGNED);
        Object value;

        switch (flags & MASK_TYPE)
        {
            case FLAG_TYPE_NULL_ACTUAL:
                value = null;
                break;
            case FLAG_TYPE_NULL_SENTINEL:
                value = VALUE_NULL;
                break;
            case FLAG_TYPE_NOT_FOUND_SENTINEL:
                value = VALUE_NOT_FOUND;
                break;
            case FLAG_TYPE_STRING:
                value = this.readString(rawReader);
                break;
            case FLAG_TYPE_LONG:
                value = this.readLong(rawReader, (flags & FLAG_VALUE_UNSIGNED) == FLAG_VALUE_UNSIGNED);
                break;
            case FLAG_TYPE_INT:
                value = this.readInt(rawReader, (flags & FLAG_VALUE_UNSIGNED) == FLAG_VALUE_UNSIGNED);
                break;
            case FLAG_TYPE_DOUBLE:
                value = rawReader.readDouble();
                break;
            case FLAG_TYPE_FLOAT:
                value = rawReader.readFloat();
                break;
            case FLAG_TYPE_DATE:
                value = new Date(this.readLong(rawReader, (flags & FLAG_VALUE_UNSIGNED) == FLAG_VALUE_UNSIGNED));
                break;
            case FLAG_TYPE_CLASS:
                value = CLASS_CACHE.computeIfAbsent(this.readString(rawReader), this::findClass);
                break;
            case FLAG_TYPE_QNAME:
                value = this.readQName(rawReader);
                break;
            case FLAG_TYPE_STOREREF:
                value = this.readStoreRef(rawReader);
                break;
            case FLAG_TYPE_NODEREF:
                value = this.readNodeRef(rawReader);
                break;
            default:
                value = rawReader.readObject();
        }

        this.setMembers(valueHolder, rand, value);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final ValueHolder valueHolder, final BinaryReader reader) throws BinaryObjectException
    {
        final int flags = reader.readByte(FLAGS);
        final int rand = reader.readInt(RAND);
        Object value;

        switch (flags & MASK_TYPE)
        {
            case FLAG_TYPE_NULL_ACTUAL:
                value = null;
                break;
            case FLAG_TYPE_NULL_SENTINEL:
                value = VALUE_NULL;
                break;
            case FLAG_TYPE_NOT_FOUND_SENTINEL:
                value = VALUE_NOT_FOUND;
                break;
            case FLAG_TYPE_STRING:
                value = reader.readString(VALUE);
                break;
            case FLAG_TYPE_LONG:
                value = reader.readLong(VALUE);
                break;
            case FLAG_TYPE_INT:
                value = reader.readInt(VALUE);
                break;
            case FLAG_TYPE_DOUBLE:
                value = reader.readDouble(VALUE);
                break;
            case FLAG_TYPE_FLOAT:
                value = reader.readFloat(VALUE);
                break;
            case FLAG_TYPE_DATE:
                value = new Date(reader.readLong(VALUE));
                break;
            case FLAG_TYPE_CLASS:
                value = CLASS_CACHE.computeIfAbsent(reader.readString(VALUE), this::findClass);
                break;
            default:
                value = reader.readObject(VALUE);
        }

        this.setMembers(valueHolder, rand, value);
    }

    private void setMembers(final ValueHolder valueHolder, final int rand, final Object value)
    {
        GridUnsafe.putIntField(valueHolder, RAND_FIELD_OFFSET, rand);
        GridUnsafe.putObjectField(valueHolder, VALUE_FIELD_OFFSET, value);
    }

    private Class<?> findClass(final String className)
    {
        try
        {
            return Class.forName(className);
        }
        catch (final ClassNotFoundException cnfe)
        {
            throw new BinaryObjectException("Failed to find class", cnfe);
        }
    }
}
