/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.util.ParameterCheck;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinarySerializer;
import org.jetbrains.annotations.NotNull;

/**
 * This class provides a base class for custom {@link BinarySerializer serializer} implementations for Alfresco key and value types,
 * providing the following reusable features:
 * <ul>
 * <li>writing/reading long values as variable length values with a reduced value space of 61/62 bits, depending on the used of the
 * signed/unsigned values</li>
 * <li>writing/reading integer values as variable length values with a reduced value space of 29/30 bits, depending on the used of the
 * signed/unsigned values</li>
 * <li>writing/reading database IDs as variable length values with a reduced value space of 62 bits, or 61 bits if
 * {@link #setHandleNegativeIds(boolean) negative IDs must be supported}</li>
 * <li>writing/reading file / content size values as variable length values with a reduced value space of 62 bits unless
 * {@link #setHandle4EiBFileSizes(boolean) 4 EiB} is set to support larger file sizes</li>
 * </ul>
 *
 * @author Axel Faust
 */
public abstract class AbstractCustomBinarySerializer implements BinarySerializer
{

    public static final long LONG_AS_SHORT_UNSIGNED_MAX = 0x3fffffffffffffffL;

    public static final long LONG_AS_SHORT_SIGNED_POSITIVE_MAX = 0x1fffffffffffffffL;

    public static final long LONG_AS_SHORT_SIGNED_NEGATIVE_MAX = 0x9fffffffffffffffL;

    public static final int INT_AS_BYTE_UNSIGNED_MAX = 0x3fffffff;

    public static final int INT_AS_BYTE_SIGNED_POSITIVE_MAX = 0x1fffffff;

    public static final int INT_AS_BYTE_SIGNED_NEGATIVE_MAX = 0x9fffffff;

    protected boolean useRawSerialForm;

    protected boolean useVariableLengthIntegers;

    protected boolean handleNegativeIds;

    protected boolean handle4EiBFileSizes;

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
     * Specifies whether this instance must support files sizes of 4 EiB or higher when dealing with objects in a raw serialised form.
     *
     * @param handle4EiBFileSizes
     *            {@code true} if files sizes of at least 4 EiB must be supported, {@code false otherwise}
     */
    public void setHandle4EiBFileSizes(final boolean handle4EiBFileSizes)
    {
        this.handle4EiBFileSizes = handle4EiBFileSizes;
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
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (this.handle4EiBFileSizes || !this.useVariableLengthIntegers)
        {
            rawWriter.writeLong(size);
        }
        else
        {
            this.writeUnsignedLong(size, rawWriter);
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
        ParameterCheck.mandatory("rawReader", rawReader);

        long fileSize;
        if (this.handle4EiBFileSizes || !this.useVariableLengthIntegers)
        {
            fileSize = rawReader.readLong();
        }
        else
        {
            fileSize = this.readUnsignedLong(rawReader);
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
        ParameterCheck.mandatory("value", value);
        ParameterCheck.mandatory("rawWriter", rawWriter);

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
        ParameterCheck.mandatory("rawReader", rawReader);

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
    protected final void write(@NotNull final Long value, final boolean nonNegativeOnly, @NotNull final BinaryRawWriter rawWriter)
            throws BinaryObjectException
    {
        ParameterCheck.mandatory("value", value);
        this.write(value.longValue(), nonNegativeOnly, rawWriter);
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
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (!this.useVariableLengthIntegers)
        {
            rawWriter.writeLong(value);
        }
        else
        {
            if (nonNegativeOnly)
            {
                this.writeUnsignedLong(value, rawWriter);
            }
            else
            {
                this.writeSignedLong(value, rawWriter);
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
        ParameterCheck.mandatory("rawReader", rawReader);

        long value;
        if (!this.useVariableLengthIntegers)
        {
            value = rawReader.readLong();
        }
        else
        {
            if (nonNegativeOnly)
            {
                value = this.readUnsignedLong(rawReader);
            }
            else
            {
                value = this.readSignedLong(rawReader);
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
    protected final void write(@NotNull final Integer value, final boolean nonNegativeOnly, @NotNull final BinaryRawWriter rawWriter)
            throws BinaryObjectException
    {
        ParameterCheck.mandatory("value", value);
        this.write(value.intValue(), nonNegativeOnly, rawWriter);
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
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (!this.useVariableLengthIntegers)
        {
            rawWriter.writeInt(value);
        }
        else
        {
            if (nonNegativeOnly)
            {
                this.writeUnsignedInteger(value, rawWriter);
            }
            else
            {
                this.writeSignedInteger(value, rawWriter);
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
        ParameterCheck.mandatory("rawReader", rawReader);

        int value;
        if (!this.useVariableLengthIntegers)
        {
            value = rawReader.readInt();
        }
        else
        {
            if (nonNegativeOnly)
            {
                value = this.readUnsignedInteger(rawReader);
            }
            else
            {
                value = this.readSignedInteger(rawReader);
            }
        }

        return value;
    }

    /**
     * Writes an unsigned long value as a series of short fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    private final void writeUnsignedLong(final long value, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (value == 0)
        {
            final short s = 0;
            rawWriter.writeShort(s);
        }
        else if (value < 0)
        {
            throw new BinaryObjectException("Long value exceeds value range for unsigned values in variable length integer mode");
        }
        else
        {
            final int leadingZeros = Long.numberOfLeadingZeros(value);

            if (leadingZeros < 2)
            {
                throw new BinaryObjectException("Long value exceeds value range for unsigned values in variable length integer mode");
            }

            // 64 possible bits per long
            // 2 additional bits for length prefix => 66 bits
            // 1 bit negative offset to shift exact matches of "bits necessary vs bits per byte" => 65 bits
            // subtraction of leading zero count as number non-significant bits
            // ex. 0x3fffl => 50 leading zeroes => 65 - 50 = 15 => 15 / 16 = 0
            // ex. 0x7fffl => 49 leading zeroes => 65 - 49 = 16 => 16 / 16 = 1
            final int additionalShorts = (65 - leadingZeros) / 16;
            rawWriter.writeShort((short) ((additionalShorts << 14) | (value & 0x3fff)));

            switch (additionalShorts)
            {
                case 0: // NO-OP
                    break;
                case 1:
                    rawWriter.writeShort((short) ((value >> 14) & 0xffff));
                    break;
                case 2:
                    rawWriter.writeShort((short) ((value >> 14) & 0xffff));
                    rawWriter.writeShort((short) ((value >> 30) & 0xffff));
                    break;
                case 3:
                    rawWriter.writeShort((short) ((value >> 14) & 0xffff));
                    rawWriter.writeShort((short) ((value >> 30) & 0xffff));
                    rawWriter.writeShort((short) ((value >> 46) & 0xffff));
                    break;
                default:
                    throw new BinaryObjectException("Invalid number of shorts to write for an unsigned long");
            }
        }
    }

    /**
     * Writes an signed long value as a series of short fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    private final void writeSignedLong(final long value, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (value == 0)
        {
            final short s = 0;
            rawWriter.writeShort(s);
        }
        else
        {
            final boolean negative = value < 0;
            final long effV = negative ? (-value - 1) : value;
            final int leadingZeros = Long.numberOfLeadingZeros(effV);

            if (leadingZeros < 3)
            {
                throw new BinaryObjectException("Long value exceeds value range for signed values in variable length integer mode");
            }

            // 64 possible bits per long
            // 3 additional bits for length prefix and sign => 67 bits
            // 1 bit negative offset to shift exact matches of "bits necessary vs bits per byte" => 66 bits
            // subtraction of leading zero count as number non-significant bits
            // ex. 0x1fffl => 51 leading zeroes => 66 - 51 = 15 => 15 / 16 = 0
            // ex. 0x3fffl => 50 leading zeroes => 66 - 50 = 16 => 16 / 16 = 1
            final int additionalShorts = (66 - leadingZeros) / 16;
            rawWriter.writeShort((short) ((additionalShorts << 14) | (effV & 0x1fff) | (negative ? 0x2000 : 0)));

            switch (additionalShorts)
            {
                case 0: // NO-OP
                    break;
                case 1:
                    rawWriter.writeShort((short) ((effV >> 13) & 0xffff));
                    break;
                case 2:
                    rawWriter.writeShort((short) ((effV >> 13) & 0xffff));
                    rawWriter.writeShort((short) ((effV >> 29) & 0xffff));
                    break;
                case 3:
                    rawWriter.writeShort((short) ((effV >> 13) & 0xffff));
                    rawWriter.writeShort((short) ((effV >> 29) & 0xffff));
                    rawWriter.writeShort((short) ((effV >> 45) & 0xffff));
                    break;
                default:
                    throw new BinaryObjectException("Invalid number of shorts to write for a signed long");
            }
        }
    }

    /**
     * Reads an unsigned long value from a series of short fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the unsigned long value
     */
    private final long readUnsignedLong(@NotNull final BinaryRawReader rawReader) throws BinaryObjectException
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        long value;

        final short s = rawReader.readShort();
        value = (s & 0x3fffl);

        final int additionalShorts = (s & 0xc000) >> 14;
        switch (additionalShorts)
        {
            case 0: // NO-OP
                break;
            case 1:
                value = value | ((rawReader.readShort() & 0xffffl) << 14);
                break;
            case 2:
                value = value | ((rawReader.readShort() & 0xffffl) << 14);
                value = value | ((rawReader.readShort() & 0xffffl) << 30);
                break;
            case 3:
                value = value | ((rawReader.readShort() & 0xffffl) << 14);
                value = value | ((rawReader.readShort() & 0xffffl) << 30);
                value = value | ((rawReader.readShort() & 0xffffl) << 46);
                break;
            default:
                throw new BinaryObjectException("Invalid number of shorts to read for an unsigned long");
        }

        return value;
    }

    /**
     * Reads a signed long value from a series of short fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the signed long value
     */
    private final long readSignedLong(@NotNull final BinaryRawReader rawReader) throws BinaryObjectException
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        long value;

        final short s = rawReader.readShort();
        value = (s & 0x1fffl);

        final int additionalShorts = (s & 0xc000) >> 14;
        switch (additionalShorts)
        {
            case 0: // NO-OP
                break;
            case 1:
                value = value | ((rawReader.readShort() & 0xffffl) << 13);
                break;
            case 2:
                value = value | ((rawReader.readShort() & 0xffffl) << 13);
                value = value | ((rawReader.readShort() & 0xffffl) << 29);
                break;
            case 3:
                value = value | ((rawReader.readShort() & 0xffffl) << 13);
                value = value | ((rawReader.readShort() & 0xffffl) << 29);
                value = value | ((rawReader.readShort() & 0xffffl) << 45);
                break;
            default:
                throw new BinaryObjectException("Invalid number of shorts to read for a signed long");
        }

        if ((s & 0x2000) == 0x2000)
        {
            value = -(value + 1);
        }

        return value;
    }

    /**
     * Writes an unsigned integer value as a series of byte fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    private final void writeUnsignedInteger(final int value, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (value == 0)
        {
            final byte b = 0;
            rawWriter.writeByte(b);
        }
        else if (value < 0)
        {
            throw new BinaryObjectException("Integer value exceeds value range for unsigned values in variable length integer mode");
        }
        else
        {
            final int leadingZeros = Integer.numberOfLeadingZeros(value);

            if (leadingZeros < 2)
            {
                throw new BinaryObjectException("Integer value exceeds value range for unsigned values in variable length integer mode");
            }

            // 32 possible bits per integer
            // 2 additional bits for length prefix => 34 bits
            // 1 bit negative offset to shift exact matches of "bits necessary vs bits per byte" => 33 bits
            // subtraction of leading zero count as number non-significant bits
            // ex. 0x3f => 26 leading zeroes => 33 - 26 = 7 => 7 / 8 = 0
            // ex. 0x7f => 25 leading zeroes => 33 - 25 = 8 => 8 / 8 = 1
            final int additionalBytes = (33 - leadingZeros) / 8;
            rawWriter.writeByte((byte) ((additionalBytes << 6) | (value & 0x3f)));

            switch (additionalBytes)
            {
                case 0: // NO-OP
                    break;
                case 1:
                    rawWriter.writeByte((byte) ((value >> 6) & 0xff));
                    break;
                case 2:
                    rawWriter.writeByte((byte) ((value >> 6) & 0xff));
                    rawWriter.writeByte((byte) ((value >> 14) & 0xff));
                    break;
                case 3:
                    rawWriter.writeByte((byte) ((value >> 6) & 0xff));
                    rawWriter.writeByte((byte) ((value >> 14) & 0xff));
                    rawWriter.writeByte((byte) ((value >> 22) & 0xff));
                    break;
                default:
                    throw new BinaryObjectException("Invalid number of bytes to write for an unsigned integer");
            }
        }
    }

    /**
     * Writes a signed integer value as a series of byte fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    private final void writeSignedInteger(final int value, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (value == 0)
        {
            final byte b = 0;
            rawWriter.writeByte(b);
        }
        else
        {
            final boolean negative = value < 0;
            final int effV = negative ? (-value - 1) : value;
            final int leadingZeros = Integer.numberOfLeadingZeros(effV);

            if (leadingZeros < 3)
            {
                throw new BinaryObjectException("Integer value exceeds value range for unsigned values in variable length integer mode");
            }

            // 32 possible bits per integer
            // 3 additional bits for length prefix and sign => 35 bits
            // 1 bit negative offset to shift exact matches of "bits necessary vs bits per byte" => 34 bits
            // subtraction of leading zero count as number non-significant bits
            // ex. 0x1f => 27 leading zeroes => 34 - 27 = 7 => 7 / 8 = 0
            // ex. 0x3f => 26 leading zeroes => 34 - 26 = 8 => 8 / 8 = 1
            final int additionalBytes = (34 - leadingZeros) / 8;
            rawWriter.writeByte((byte) ((additionalBytes << 6) | (effV & 0x1f) | (negative ? 0x20 : 0)));

            switch (additionalBytes)
            {
                case 0: // NO-OP
                    break;
                case 1:
                    rawWriter.writeByte((byte) ((effV >> 5) & 0xff));
                    break;
                case 2:
                    rawWriter.writeByte((byte) ((effV >> 5) & 0xff));
                    rawWriter.writeByte((byte) ((effV >> 13) & 0xff));
                    break;
                case 3:
                    rawWriter.writeByte((byte) ((effV >> 5) & 0xff));
                    rawWriter.writeByte((byte) ((effV >> 13) & 0xff));
                    rawWriter.writeByte((byte) ((effV >> 21) & 0xff));
                    break;
                default:
                    throw new BinaryObjectException("Invalid number of bytes to write for a signed integer");
            }
        }
    }

    /**
     * Reads an unsigned integer value from a series of byte fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the unsigned integer value
     */
    private final int readUnsignedInteger(@NotNull final BinaryRawReader rawReader) throws BinaryObjectException
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        int value;

        final byte b = rawReader.readByte();
        value = (b & 0x3f);

        final int additionalBytes = (b & 0xc0) >> 6;
        switch (additionalBytes)
        {
            case 0: // NO-OP
                break;
            case 1:
                value = value | ((rawReader.readByte() & 0xff) << 6);
                break;
            case 2:
                value = value | ((rawReader.readByte() & 0xff) << 6);
                value = value | ((rawReader.readByte() & 0xff) << 14);
                break;
            case 3:
                value = value | ((rawReader.readByte() & 0xff) << 6);
                value = value | ((rawReader.readByte() & 0xff) << 14);
                value = value | ((rawReader.readByte() & 0xff) << 22);
                break;
            default:
                throw new BinaryObjectException("Invalid number of bytes to read for an unsigned integer");
        }

        return value;
    }

    /**
     * Reads a signed integer value from a series of byte fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the signed integer value
     */
    private final int readSignedInteger(@NotNull final BinaryRawReader rawReader) throws BinaryObjectException
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        int value;

        final byte b = rawReader.readByte();
        value = (b & 0x1f);

        final int additionalBytes = (b & 0xc0) >> 6;
        switch (additionalBytes)
        {
            case 0: // NO-OP
                break;
            case 1:
                value = value | ((rawReader.readByte() & 0xff) << 5);
                break;
            case 2:
                value = value | ((rawReader.readByte() & 0xff) << 5);
                value = value | ((rawReader.readByte() & 0xff) << 13);
                break;
            case 3:
                value = value | ((rawReader.readByte() & 0xff) << 5);
                value = value | ((rawReader.readByte() & 0xff) << 13);
                value = value | ((rawReader.readByte() & 0xff) << 21);
                break;
            default:
                throw new BinaryObjectException("Invalid number of bytes to read for a signed integer");
        }

        if ((b & 0x20) == 0x20)
        {
            value = -(value + 1);
        }

        return value;
    }
}
