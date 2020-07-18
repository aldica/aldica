/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinarySerializer;
import org.jetbrains.annotations.NotNull;

/**
 * This class provides a base class for custom {@link BinarySerializer serializer} implementations for Alfresco key and value types,
 * providing the following reusable features:
 * <ul>
 * <li>writing/reading long values as variable length values with a reduced value space of 57/56 bits, depending on the used of the
 * signed/unsigned values</li>
 * <li>writing/reading integer values as variable length values with a reduced value space of 28/29 bits, depending on the used of the
 * signed/unsigned values</li>
 * <li>writing/reading database IDs as variable length values with a reduced value space of 57 bits, or 56 bits if
 * {@link #setHandleNegativeIds(boolean) negative IDs must be supported}</li>
 * <li>writing/reading file / content size values as variable length values with a reduced value space of 57 bits if neither
 * {@link #setHandle128PiBFileSizes(boolean) 128 PiB} or {@link #setHandle2EiBFileSizes(boolean) 2 EiB} and larger file sizes must be
 * supported, or a reduced value space of 61 bits if {@link #setHandle2EiBFileSizes(boolean) 2 EiB} and larger file sizes do not need to be
 * supported</li>
 * </ul>
 *
 * @author Axel Faust
 */
public abstract class AbstractCustomBinarySerializer implements BinarySerializer
{

    public static final long LONG_AS_SHORT_UNSIGNED_MAX = 0x1FffffffffffffffL;

    public static final long LONG_AS_BYTE_UNSIGNED_MAX = 0x01ffffffffffffffL;

    public static final long LONG_AS_BYTE_SIGNED_POSITIVE_MAX = 0x00ffffffffffffffL;

    public static final long LONG_AS_BYTE_SIGNED_NEGATIVE_MAX = 0x80ffffffffffffffL;

    public static final int INT_AS_BYTE_UNSIGNED_MAX = 0x1fffffff;

    public static final int INT_AS_BYTE_SIGNED_POSITIVE_MAX = 0x0fffffff;

    public static final int INT_AS_BYTE_SIGNED_NEGATIVE_MAX = 0x8fffffff;

    protected boolean useRawSerialForm;

    protected boolean useVariableLengthIntegers;

    protected boolean handleNegativeIds;

    protected boolean handle128PiBFileSizes;

    protected boolean handle2EiBFileSizes;

    /**
     * Specifies whether this instance should use/handle a raw serialised form for objects, meaning the elimination of any field metadata
     * and use of byte-level optimisations possible when writing/reading directly to/from the object byte stream via raw
     * {@link BinaryRawWriter writer}/{@link BinaryRawReader reader}.
     *
     * @param useRawSerialForm
     *            {@code true} if objects should be written in a raw serialised
     */
    public void setUseRawSerialForm(final boolean useRawSerialForm)
    {
        this.useRawSerialForm = useRawSerialForm;
    }

    /**
     * Specifies whether this instance should use/handle variable length integers when dealing with objects in a raw serialised form.
     *
     * @param useVariableLengthIntegers
     *            {@code true} if variable length integers should be be used
     */
    public void setUseVariableLengthIntegers(final boolean useVariableLengthIntegers)
    {
        this.useVariableLengthIntegers = useVariableLengthIntegers;
    }

    /**
     * Specifies whether this instance must support negative database IDs when dealing with objects in a raw serialised form. Alfresco by
     * default uses auto-incrementing database IDs starting from {@code 0}, so unless manual manipulation is performed at the database
     * level, negative IDs should not need to be supported and the sign bit could be used to optimise
     * {@link #setUseVariableLengthIntegers(boolean) variable length integers}.
     *
     * @param handleNegativeIds
     *            {@code true} if negative database IDs must be supported
     */
    public void setHandleNegativeIds(final boolean handleNegativeIds)
    {
        this.handleNegativeIds = handleNegativeIds;
    }

    /**
     * Specifies whether this instance must support files sizes of 128 PiB or higher when dealing with objects in a raw serialised form.
     *
     * @param handle128PiBFileSizes
     *            {@code true} if files sizes of at least 128 PiB must be supported, {@code false otherwise}
     */
    public void setHandle128PiBFileSizes(final boolean handle128PiBFileSizes)
    {
        this.handle128PiBFileSizes = handle128PiBFileSizes;
    }

    /**
     * Specifies whether this instance must support files sizes of 2 EiB or higher when dealing with objects in a raw serialised form.
     *
     * @param handle2EiBFileSizes
     *            {@code true} if files sizes of at least 2 EiB must be supported, {@code false otherwise}
     */
    public void setHandle2EiBFileSizes(final boolean handle2EiBFileSizes)
    {
        this.handle2EiBFileSizes = handle2EiBFileSizes;
    }

    /**
     * Writes the value of a database ID to a raw serialised form of an object.
     *
     * @param id
     *            the ID to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void writeDbId(final long id, @NotNull final BinaryRawWriter rawWriter)
    {
        this.write(id, !this.handleNegativeIds, rawWriter);
    }

    /**
     * Reads the value of a database ID from a raw serialised form of an object.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the database ID
     */
    protected final long readDbId(@NotNull final BinaryRawReader rawReader)
    {
        final long dbId = this.readLong(!this.handleNegativeIds, rawReader);
        return dbId;
    }

    /**
     * Writes the value of a file / content size to a raw serialised form of an object.
     *
     * @param size
     *            the file / content size to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void writeFileSize(final long size, @NotNull final BinaryRawWriter rawWriter)
    {

        if (this.handle2EiBFileSizes || !this.useVariableLengthIntegers)
        {
            rawWriter.writeLong(size);
        }
        else if (this.handle128PiBFileSizes)
        {
            if (size < 0)
            {
                throw new BinaryObjectException("Cannot write negative long value as unsigned long in variable length integer mode");
            }

            if (size > LONG_AS_SHORT_UNSIGNED_MAX)
            {
                throw new BinaryObjectException(
                        "File size exceeds value range in variable length integer mode with [128 PiB, 2EiB) file size support");
            }

            if (size == 0)
            {
                final short s = 0;
                rawWriter.writeShort(s);
            }
            else
            {
                this.writeVariableLengthShortRemainder(size, 3, rawWriter);
            }
        }
        else
        {
            this.write(size, true, rawWriter);
        }
    }

    /**
     * Reads the value of a file / content size from a raw serialised form of an object.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return
     *         the file / content size
     */
    protected final long readFileSize(@NotNull final BinaryRawReader rawReader)
    {
        long fileSize;
        if (this.handle2EiBFileSizes || !this.useVariableLengthIntegers)
        {
            fileSize = rawReader.readLong();
        }
        else if (this.handle128PiBFileSizes)
        {
            fileSize = this.readVariableLengthShortRemainder(0, 0, 3, rawReader);
        }
        else
        {
            fileSize = this.readLong(true, rawReader);
        }
        return fileSize;
    }

    /**
     * Writes a non-null String value to a raw serialised form of an object.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void write(@NotNull final String value, @NotNull final BinaryRawWriter rawWriter)
    {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        this.write(bytes.length, true, rawWriter);
        if (bytes.length != 0)
        {
            for (final byte b : bytes)
            {
                rawWriter.writeByte(b);
            }
        }
    }

    /**
     * Reads a non-null String value from a raw serialised form of an object.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the String value
     */
    @NotNull
    protected final String readString(@NotNull final BinaryRawReader rawReader)
    {
        final int size = this.readInt(true, rawReader);
        final byte[] bytes = new byte[size];
        for (int idx = 0; idx < size; idx++)
        {
            bytes[idx] = rawReader.readByte();
        }

        final String value = new String(bytes, StandardCharsets.UTF_8);
        return value;
    }

    /**
     * Writes a non-null {@link Locale} value to a raw serialised form of an object.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void write(@NotNull final Locale value, @NotNull final BinaryRawWriter rawWriter)
    {
        final String valueStr = value.toString();
        this.write(valueStr, rawWriter);
    }

    /**
     * Reads a non-null {@link Locale} value from a raw serialised form of an object.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the {@link Locale} value
     */
    @NotNull
    protected final Locale readLocale(@NotNull final BinaryRawReader rawReader)
    {
        final String valueStr = this.readString(rawReader);
        // we know there is at least a default converter for Locale, maybe even an optimised (caching) one
        final Locale value = DefaultTypeConverter.INSTANCE.convert(Locale.class, valueStr);
        return value;
    }

    /**
     * Writes a long value to a raw serialised form of an object.
     *
     * @param value
     *            the value to write
     * @param nonNegativeOnly
     *            {@code true} if the value can always be expected to hold non-negative values and will be read the same - this is an
     *            important input into handling {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     * @throws BinaryObjectException
     *             if the long value cannot be written, e.g. if {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     *             are enabled and the value exceeds the supported (reduced) value space for long values
     */
    protected final void write(final long value, final boolean nonNegativeOnly,
            @NotNull final BinaryRawWriter rawWriter)
            throws BinaryObjectException
    {
        if (!this.useVariableLengthIntegers)
        {
            rawWriter.writeLong(value);
        }
        else
        {
            if (nonNegativeOnly && value < 0)
            {
                throw new BinaryObjectException("Cannot write negative long value as unsigned long in variable length integer mode");
            }

            if (value == 0)
            {
                final byte b = 0;
                rawWriter.writeByte(b);
            }
            else if (nonNegativeOnly)
            {
                if (value > LONG_AS_BYTE_UNSIGNED_MAX)
                {
                    throw new BinaryObjectException(
                            "Long value exceeds value range for non-negative values in variable length integer mode");
                }

                this.writeVariableLengthRemainder(value, 7, rawWriter);
            }
            else
            {
                if (value > LONG_AS_BYTE_SIGNED_POSITIVE_MAX || value < LONG_AS_BYTE_SIGNED_NEGATIVE_MAX)
                {
                    throw new BinaryObjectException("Long value exceeds value range for signed values in variable length integer mode");
                }

                long remainder = value;
                byte b = 0;
                if (value < 0)
                {
                    // the 0x40 bit is used as the sign
                    b = (byte) (b | 0x40);
                    remainder = -remainder - 1;
                }
                b = (byte) (b | (remainder & 0x3f));
                remainder = remainder >> 6;
                if (remainder > 0)
                {
                    b = (byte) (b | 0x80);
                }
                rawWriter.writeByte(b);

                this.writeVariableLengthRemainder(remainder, 6, rawWriter);
            }
        }

    }

    /**
     * Reads a long value from a raw serialised form of an object.
     *
     * @param nonNegativeOnly
     *            {@code true} if the value can always be expected to hold non-negative values and will be read the same - this is an
     *            important input into handling {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return
     *         the value read from the raw serialised form
     */
    protected final long readLong(final boolean nonNegativeOnly, @NotNull final BinaryRawReader rawReader)
    {
        long value;
        if (!this.useVariableLengthIntegers)
        {
            value = rawReader.readLong();
        }
        else
        {
            if (nonNegativeOnly)
            {
                value = this.readVariableLengthRemainder(0, 0, 7, rawReader);
            }
            else
            {
                final byte b = rawReader.readByte();
                value = 0;

                value = value | (b & 0x3fl);

                if ((b & 0x80) == 0x80)
                {
                    value = this.readVariableLengthRemainder(6, value, 6, rawReader);
                }
                if ((b & 0x40) == 0x40)
                {
                    value = -(value + 1);
                }
            }
        }

        return value;
    }

    /**
     * Writes an integer value to a raw serialised form of an object.
     *
     * @param value
     *            the value to write
     * @param nonNegativeOnly
     *            {@code true} if the value can always be expected to hold non-negative values and will be read the same - this is an
     *            important input into handling {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     * @throws BinaryObjectException
     *             if the integer value cannot be written, e.g. if {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     *             are enabled and the value exceeds the supported (reduced) value space for long values
     */
    protected final void write(final int value, final boolean nonNegativeOnly,
            @NotNull final BinaryRawWriter rawWriter)
            throws BinaryObjectException
    {
        if (!this.useVariableLengthIntegers)
        {
            rawWriter.writeInt(value);
        }
        else
        {
            if (nonNegativeOnly && value < 0)
            {
                throw new BinaryObjectException("Cannot write negative integer value as unsigned integer in variable length integer mode");
            }

            if (value == 0)
            {
                final byte b = 0;
                rawWriter.writeByte(b);
            }
            else if (nonNegativeOnly)
            {
                if (value > INT_AS_BYTE_UNSIGNED_MAX)
                {
                    throw new BinaryObjectException(
                            "Integer value exceeds value range for non-negative values in variable length integer mode");
                }

                this.writeVariableLengthRemainder(value, 3, rawWriter);
            }
            else
            {
                if (value > INT_AS_BYTE_SIGNED_POSITIVE_MAX || value < INT_AS_BYTE_SIGNED_NEGATIVE_MAX)
                {
                    throw new BinaryObjectException("Integer value exceeds value range for signed values in variable length integer mode");
                }

                int remainder = value;
                byte b = 0;
                if (value < 0)
                {
                    // the 0x40 bit is used as the sign
                    b = (byte) (b | 0x40);
                    remainder = -remainder - 1;
                }
                b = (byte) (b | (remainder & 0x3f));
                remainder = remainder >> 6;
                if (remainder > 0)
                {
                    b = (byte) (b | 0x80);
                }
                rawWriter.writeByte(b);

                this.writeVariableLengthRemainder(remainder, 2, rawWriter);
            }
        }
    }

    /**
     * Reads an integer value from a raw serialised form of an object.
     *
     * @param nonNegativeOnly
     *            {@code true} if the value can always be expected to hold non-negative values and will be read the same - this is an
     *            important input into handling {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return
     *         the value read from the raw serialised form
     */
    protected final int readInt(final boolean nonNegativeOnly, @NotNull final BinaryRawReader rawReader)
    {
        int value;
        if (!this.useVariableLengthIntegers)
        {
            value = rawReader.readInt();
        }
        else
        {
            long valueL;
            if (nonNegativeOnly)
            {
                valueL = this.readVariableLengthRemainder(0, 0, 3, rawReader);
            }
            else
            {
                final byte b = rawReader.readByte();
                valueL = 0;

                valueL = valueL | (b & 0x3fl);

                if ((b & 0x80) == 0x80)
                {
                    valueL = this.readVariableLengthRemainder(6, valueL, 2, rawReader);
                }
                if ((b & 0x40) == 0x40)
                {
                    valueL = -(valueL + 1);
                }
            }
            value = (int) valueL;
        }

        return value;
    }

    /**
     * Writes the remainder of a variable length long/integer value in bytes.
     *
     * @param remainderIn
     *            the remainder to write
     * @param nonTerminalByteCount
     *            the number of non-terminal bytes to handle
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    private void writeVariableLengthRemainder(final long remainderIn, final int nonTerminalByteCount,
            @NotNull final BinaryRawWriter rawWriter)
    {
        long remainder = remainderIn;
        byte b;
        for (int i = 0; i < nonTerminalByteCount && (remainder > 0 || -remainder > 0); i++)
        {
            b = (byte) (remainder & 0x7f);
            remainder = remainder >> 7;
            if (remainder > 0)
            {
                b = (byte) (b | 0x80);
            }
            rawWriter.writeByte(b);
        }

        if (remainder > 0 || -remainder > 0)
        {
            rawWriter.writeByte((byte) (remainder & 0xff));
        }
    }

    /**
     * Reads the remainder of a variable length long/integer value in bytes and adds it to the base value.
     *
     * @param readBits
     *            the number of significant bits already read by the caller
     * @param baseValue
     *            the base value already read by the caller
     * @param nonTerminalByteCount
     *            the number of non-terminal bytes to handle
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return
     *         the value read from the raw serialised form
     */
    private long readVariableLengthRemainder(final int readBits, final long baseValue, final int nonTerminalByteCount,
            @NotNull final BinaryRawReader rawReader)
    {
        long value = baseValue;

        int offsetBits = readBits;
        boolean hasMore = true;

        byte b;
        for (int i = 0; i < nonTerminalByteCount && hasMore; i++)
        {
            b = rawReader.readByte();
            value = value | ((b & 0x7fl) << offsetBits);
            offsetBits += 7;
            hasMore = (b & 0x80) == 0x80;
        }

        if (hasMore)
        {
            b = rawReader.readByte();
            value = value | ((b & 0xffl) << offsetBits);
        }

        return value;
    }

    /**
     * Writes the remainder of a variable length long/integer value in shorts.
     *
     * @param remainderIn
     *            the remainder to write
     * @param nonTerminalShortCount
     *            the number of non-terminal shorts to handle
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    private void writeVariableLengthShortRemainder(final long remainderIn, final int nonTerminalShortCount,
            @NotNull final BinaryRawWriter rawWriter)
    {
        long remainder = remainderIn;
        short s;
        for (int i = 0; i < nonTerminalShortCount && (remainder > 0 || -remainder > 0); i++)
        {
            s = (short) (remainder & 0x7fff);
            remainder = remainder >> 15;
            if (remainder > 0)
            {
                s = (byte) (s | 0x8000);
            }
            rawWriter.writeShort(s);
        }

        if (remainder > 0 || -remainder > 0)
        {
            s = (short) (remainder & 0xffff);
            rawWriter.writeShort(s);
        }
    }

    /**
     * Reads the remainder of a variable length long/integer value in shorts and adds it to the base value.
     *
     * @param readBits
     *            the number of significant bits already read by the caller
     * @param baseValue
     *            the base value already read by the caller
     * @param nonTerminalShortCount
     *            the number of non-terminal shorts to handle
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return
     *         the value read from the raw serialised form
     */
    private long readVariableLengthShortRemainder(final int readBits, final long baseValue, final int nonTerminalShortCount,
            @NotNull final BinaryRawReader rawReader)
    {
        long value = baseValue;

        int offsetBits = readBits;
        boolean hasMore = true;

        short s;
        for (int i = 0; i < nonTerminalShortCount && hasMore; i++)
        {
            s = rawReader.readShort();
            value = value | ((s & 0x7fffl) << offsetBits);
            offsetBits += 15;
            hasMore = (s & 0x8000) == 0x8000;
        }

        if (hasMore)
        {
            s = rawReader.readShort();
            value = value | ((s & 0xffffl) << offsetBits);
        }

        return value;
    }
}
