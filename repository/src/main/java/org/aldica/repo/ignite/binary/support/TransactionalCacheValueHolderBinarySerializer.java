/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.support;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.cache.TransactionalCache;
import org.alfresco.repo.cache.TransactionalCache.ValueHolder;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link TransactionalCache transactional cache} {@link ValueHolder value holder}
 * instances in order to optimise their serial form.
 *
 * @author Axel Faust
 */
public class TransactionalCacheValueHolderBinarySerializer extends AbstractCustomBinarySerializer
{

    // copied from EntityLookupCache as both constants are inaccessible
    public static final String ENTITY_LOOKUP_CACHE_NULL = "@@VALUE_NULL@@";

    public static final String ENTITY_LOOKUP_CACHE_NOT_FOUND = "@@VALUE_NOT_FOUND@@";

    private static final String TYPE = "type";

    private static final byte TYPE_NULL = 0;

    private static final byte TYPE_NOT_FOUND = 1;

    private static final byte TYPE_OBJECT = 2;

    // used in PropertyValueDAOImpl, also nodeOwner and singletonEntity cache (e.g. Encoding / Mimetype / Namespace)
    private static final byte TYPE_STRING = 3;

    // used in PropertyValueDAOImpl - can be written as long or series of short fragments
    private static final byte TYPE_DATE = 4;

    private static final byte TYPE_DATE_OPT = 5;

    // used in PersonServiceImpl
    private static final byte TYPE_SET = 6;

    // used in PermissionServiceImpl / MessageServiceImpl / AuthorityDAOImpl
    private static final byte TYPE_STRING_SET = 7;

    // used in RuleServiceImpl / AuthorityDAOImpl
    private static final byte TYPE_LIST = 8;

    // used in TagScopePropertyMethodInterceptor
    private static final byte TYPE_STRING_LIST = 9;

    // the internal random integer is a very dodgy tool to check for co-modification (instead of using proper hash code)
    // but not handling it as-is would break TransactionalCache handling, and trying to handle it "better" via this external serialiser
    // would be subject to any number of side-effects, let alone reduce performance of the serialiser for saving at most 4 bytes
    private static final String RAND = "rand";

    private static final String VALUE = "value";

    private static final Field RAND_FIELD;

    private static final Field VALUE_FIELD;

    static
    {
        try
        {
            RAND_FIELD = ValueHolder.class.getDeclaredField(RAND);
            VALUE_FIELD = ValueHolder.class.getDeclaredField(VALUE);

            RAND_FIELD.setAccessible(true);
            VALUE_FIELD.setAccessible(true);
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
        if (!cls.equals(ValueHolder.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        try
        {
            final Integer rand = (Integer) RAND_FIELD.get(obj);
            final Object value = VALUE_FIELD.get(obj);

            final byte type = this.determineValueType(value);

            if (this.useRawSerialForm)
            {
                this.writeRawSerialForm(writer, rand, value, type);
            }
            else
            {
                this.writeDefaultSerialForm(writer, rand, value, type);
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
        if (!cls.equals(ValueHolder.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        Integer rand;
        Object value;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();
            rand = rawReader.readInt();
            value = this.readRawSerialForm(rawReader);
        }
        else
        {
            rand = reader.readInt(RAND);
            value = this.readDefaultSerialForm(reader);
        }

        try
        {
            RAND_FIELD.set(obj, rand);
            VALUE_FIELD.set(obj, value);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

    /**
     * Writes a value holder's state in raw serial form.
     *
     * @param writer
     *            the writer to use
     * @param rand
     *            the random value used in lieu of a hash code in the value holder
     * @param value
     *            the value within the holder
     * @param type
     *            the determined type of value
     */
    protected void writeRawSerialForm(final BinaryWriter writer, final Integer rand, final Object value, final byte type)
    {
        final BinaryRawWriter rawWriter = writer.rawWriter();
        rawWriter.writeInt(rand);
        rawWriter.writeByte(type);
        switch (type)
        {
            case TYPE_NULL:
            case TYPE_NOT_FOUND: // NO-OP
                break;
            case TYPE_OBJECT:
                rawWriter.writeObject(value);
                break;
            case TYPE_STRING:
                this.write((String) value, rawWriter);
                break;
            case TYPE_DATE:
                rawWriter.writeLong(((Date) value).getTime());
                break;
            case TYPE_DATE_OPT:
                this.write(((Date) value).getTime(), false, rawWriter);
                break;
            case TYPE_STRING_LIST:
            case TYPE_STRING_SET:
                @SuppressWarnings("unchecked")
                final Collection<String> scol = (Collection<String>) value;
                this.write(scol.size(), true, rawWriter);
                for (final String s : scol)
                {
                    this.write(s, rawWriter);
                }
                break;
            case TYPE_LIST:
            case TYPE_SET:
                @SuppressWarnings("unchecked")
                final Collection<Object> ocol = (Collection<Object>) value;
                this.write(ocol.size(), true, rawWriter);
                for (final Object o : ocol)
                {
                    rawWriter.writeObject(o);
                }
                break;
            default:
                throw new BinaryObjectException("Unsupported type value");
        }
    }

    /**
     * Writes a value holder's state in default serial form.
     *
     * @param writer
     *            the writer to use
     * @param rand
     *            the random value used in lieu of a hash code in the value holder
     * @param value
     *            the value within the holder
     * @param type
     *            the determined type of value
     */
    protected void writeDefaultSerialForm(final BinaryWriter writer, final Integer rand, final Object value, final byte type)
    {
        writer.writeInt(RAND, rand);
        writer.writeByte(TYPE, type);

        switch (type)
        {
            case TYPE_NULL:
            case TYPE_NOT_FOUND: // NO-OP
                break;
            case TYPE_OBJECT:
                writer.writeObject(VALUE, value);
                break;
            case TYPE_STRING:
                writer.writeString(VALUE, (String) value);
                break;
            case TYPE_DATE:
            case TYPE_DATE_OPT:
                writer.writeLong(VALUE, ((Date) value).getTime());
                break;
            case TYPE_STRING_LIST:
            case TYPE_STRING_SET:
                writer.writeStringArray(VALUE, ((Collection<?>) value).toArray(new String[0]));
                break;
            case TYPE_LIST:
            case TYPE_SET:
                writer.writeObjectArray(VALUE, ((Collection<?>) value).toArray(new Object[0]));
                break;
            default:
                throw new BinaryObjectException("Unsupported type value");
        }
    }

    /**
     * Determines the value type flag to use in the serialised form.
     *
     * @param value
     *            the value from which to derive the value type flag
     * @return the value type flag
     */
    protected byte determineValueType(final Object value)
    {
        byte type = TYPE_OBJECT;
        if (ENTITY_LOOKUP_CACHE_NULL.equals(value))
        {
            type = TYPE_NULL;
        }
        else if (ENTITY_LOOKUP_CACHE_NOT_FOUND.equals(value))
        {
            type = TYPE_NOT_FOUND;
        }
        else if (value instanceof String)
        {
            type = TYPE_STRING;
        }
        else if (value instanceof Set<?>)
        {
            final boolean allString = ((Set<?>) value).stream().allMatch(v -> v instanceof String);
            type = allString ? TYPE_STRING_SET : TYPE_SET;
        }
        else if (value instanceof List<?>)
        {
            final boolean allString = ((List<?>) value).stream().allMatch(v -> v instanceof String);
            type = allString ? TYPE_STRING_LIST : TYPE_LIST;
        }
        else if (value instanceof Date)
        {
            final long time = ((Date) value).getTime();
            if (time < LONG_AS_SHORT_SIGNED_NEGATIVE_MAX || time > LONG_AS_SHORT_SIGNED_POSITIVE_MAX)
            {
                type = TYPE_DATE;
            }
            else
            {
                type = TYPE_DATE_OPT;
            }
        }
        return type;
    }

    /**
     * Reads a value holder's contained value from raw serial form.
     *
     * @param rawReader
     *            the raw reader to use
     * @return value of a value holder instance
     */
    protected Object readRawSerialForm(final BinaryRawReader rawReader)
    {
        final byte type = rawReader.readByte();
        Object value;

        switch (type)
        {
            case TYPE_NULL:
                value = ENTITY_LOOKUP_CACHE_NULL;
                break;
            case TYPE_NOT_FOUND:
                value = ENTITY_LOOKUP_CACHE_NOT_FOUND;
                break;
            case TYPE_OBJECT:
                value = rawReader.readObject();
                break;
            case TYPE_STRING:
                value = this.readString(rawReader);
                break;
            case TYPE_DATE:
                value = new Date(rawReader.readLong());
                break;
            case TYPE_DATE_OPT:
                value = new Date(this.readLong(false, rawReader));
                break;
            case TYPE_STRING_LIST:
            case TYPE_STRING_SET:
            {
                final int size = this.readInt(true, rawReader);
                final Collection<String> scol = type == TYPE_STRING_LIST ? new ArrayList<>(size) : new HashSet<>(size);
                value = scol;
                for (int idx = 0; idx < size; idx++)
                {
                    scol.add(this.readString(rawReader));
                }
            }
                break;
            case TYPE_LIST:
            case TYPE_SET:
            {
                final int size = this.readInt(true, rawReader);
                final Collection<Object> ocol = type == TYPE_LIST ? new ArrayList<>(size) : new HashSet<>(size);
                value = ocol;
                for (int idx = 0; idx < size; idx++)
                {
                    ocol.add(rawReader.readObject());
                }
            }
                break;
            default:
                throw new BinaryObjectException("Unsupported type value");
        }

        return value;
    }

    /**
     * Reads a value holder's contained value from default serial form.
     *
     * @param reader
     *            the reader to use
     * @return value of a value holder instance
     */
    protected Object readDefaultSerialForm(final BinaryReader reader)
    {
        final byte type = reader.readByte(TYPE);
        Object value;

        switch (type)
        {
            case TYPE_NULL:
                value = ENTITY_LOOKUP_CACHE_NULL;
                break;
            case TYPE_NOT_FOUND:
                value = ENTITY_LOOKUP_CACHE_NOT_FOUND;
                break;
            case TYPE_OBJECT:
                value = reader.readObject(VALUE);
                break;
            case TYPE_STRING:
                value = reader.readString(VALUE);
                break;
            case TYPE_DATE:
            case TYPE_DATE_OPT:
                value = new Date(reader.readLong(VALUE));
                break;
            case TYPE_STRING_LIST:
            case TYPE_STRING_SET:
            {
                final String[] sarr = reader.readStringArray(VALUE);
                final Collection<String> scol = type == TYPE_STRING_LIST ? new ArrayList<>(sarr.length) : new HashSet<>(sarr.length);
                value = scol;
                for (final String s : sarr)
                {
                    scol.add(s);
                }
            }
                break;
            case TYPE_LIST:
            case TYPE_SET:
            {
                final Object[] oarr = reader.readObjectArray(VALUE);
                final Collection<Object> ocol = type == TYPE_LIST ? new ArrayList<>(oarr.length) : new HashSet<>(oarr.length);
                value = ocol;
                for (final Object o : oarr)
                {
                    ocol.add(o);
                }
            }
                break;
            default:
                throw new BinaryObjectException("Unsupported type value");
        }

        return value;
    }
}
