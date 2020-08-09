/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.base;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;

import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.internal.binary.BinaryReaderExImpl;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.binary.streams.BinaryHeapInputStream;
import org.apache.ignite.internal.binary.streams.BinaryHeapOutputStream;
import org.jetbrains.annotations.NotNull;

/**
 * This class performs a micro benchmark test of various methods to handle long values in the serialised form of an object via the Ignite
 * raw binary writer and reader. This class is to be run as a regular Java application and will output the semi-colon separated nanosecond
 * per write/read value of the following handling variants:
 * <ol>
 * <li>using Ignite default {@link BinaryRawWriter#writeLong(long) writeLong} / {@link BinaryRawReader#readLong() readLong}</li>
 * <li>handling long values as a iterative stream of bytes with the top bit of each byte (except the last) determining if another byte
 * follows</li>
 * <li>handling long values as a iterative stream of bytes with the top bit of each byte (except the last) determining if another byte
 * follows, using a switch instead of loop to optimise the write operation</li>
 * <li>handling long values as a iterative stream of shorts with the top bit of each short (except the last) determining if another short
 * follows</li>
 * <li>handling long values as a iterative stream of shorts with the top bit of each short (except the last) determining if another short
 * follows, using a switch instead of loop to optimise the write operation</li>
 * <li>handling long values as a stream of shorts with the top two bits of the first short determining how many additional shorts are part
 * of the same value</li>
 * </ol>
 *
 * The CSV output can be used in any spreadsheet / analysis program to determine statistically significant values (percentiles / average or
 * median with standard deviation). Depending on JVM and JIT warmup, the first dozens of rows in the CSV output need to be ignored in order
 * to get a more realistic output for a stable, production-grade cost of each handling variant.
 *
 * @author Axel Faust
 */
public class VariableLengthLongMicroBenchmark
{

    private static final long LONG_AS_BYTE_SIGNED_POSITIVE_MAX = 0x00ffffffffffffffL;

    private static final long LONG_AS_BYTE_SIGNED_NEGATIVE_MAX = 0x80ffffffffffffffL;

    private final byte[] seed = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);

    public static void main(final String... args) throws Exception
    {
        final VariableLengthLongMicroBenchmark instance = new VariableLengthLongMicroBenchmark();
        for (int i = 0; i < 500; i++)
        {
            instance.nativeLong();
            instance.iterativeBytes();
            instance.iterativeBytesSwitched();
            instance.iterativeShorts();
            instance.iterativeShortsSwitched();
            instance.lengthPrefixedShortsSwitched();
            System.out.print("\n");
        }
    }

    private void nativeLong() throws Exception
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(81920);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        final SecureRandom rn = new SecureRandom(this.seed);
        final long[] values = new long[10240];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = rn.nextLong();
        }

        final long startWrite = System.nanoTime();
        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            for (final long value : values)
            {
                this.writeNative(value, bw);
            }
        }
        final long endWrite = System.nanoTime();

        System.out.print("" + (endWrite - startWrite) / 10240 + ";");

        final long startRead = System.nanoTime();
        try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
        {
            for (int i = 0; i < values.length; i++)
            {
                this.readNative(br);
            }
        }
        final long endRead = System.nanoTime();

        System.out.print("" + (endRead - startRead) / 10240 + ";");
    }

    private void iterativeBytes() throws Exception
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(81920);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        final SecureRandom rn = new SecureRandom(this.seed);
        final long[] values = new long[10240];
        for (int i = 0; i < values.length; i++)
        {
            // don't use upper 8 bits - reserved for sign + length mark
            values[i] = rn.nextInt(0x00ffffff) << 32 + rn.nextInt(0x7fffffff);
            if (rn.nextBoolean())
            {
                values[i] = -values[i];
            }
        }

        final long startWrite = System.nanoTime();
        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            for (final long value : values)
            {
                this.writeIterativeBytes(value, bw);
            }
        }
        final long endWrite = System.nanoTime();

        System.out.print("" + (endWrite - startWrite) / 10240 + ";");

        final long startRead = System.nanoTime();
        try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
        {
            for (int i = 0; i < values.length; i++)
            {
                this.readIterativeBytes(br);
            }
        }
        final long endRead = System.nanoTime();

        System.out.print("" + (endRead - startRead) / 10240 + ";");
    }

    private void iterativeBytesSwitched() throws Exception
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(81920);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        final SecureRandom rn = new SecureRandom(this.seed);
        final long[] values = new long[10240];
        for (int i = 0; i < values.length; i++)
        {
            // don't use upper 8 bits - reserved for sign + length mark
            values[i] = rn.nextInt(0x00ffffff) << 32 + rn.nextInt(0x7fffffff);
            if (rn.nextBoolean())
            {
                values[i] = -values[i];
            }
        }

        final long startWrite = System.nanoTime();
        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            for (final long value : values)
            {
                this.writeIterativeBytesSwitched(value, bw);
            }
        }
        final long endWrite = System.nanoTime();

        System.out.print("" + (endWrite - startWrite) / 10240 + ";");

        final long startRead = System.nanoTime();
        try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
        {
            for (int i = 0; i < values.length; i++)
            {
                this.readIterativeBytesSwitched(br);
            }
        }
        final long endRead = System.nanoTime();

        System.out.print("" + (endRead - startRead) / 10240 + ";");
    }

    private void iterativeShorts() throws Exception
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(81920);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        final SecureRandom rn = new SecureRandom(this.seed);
        final long[] values = new long[10240];
        for (int i = 0; i < values.length; i++)
        {
            // don't use upper 4 bits - reserved for sign + length mark
            values[i] = rn.nextInt(0x0fffffff) << 32 + rn.nextInt(0x7fffffff);
            if (rn.nextBoolean())
            {
                values[i] = -values[i];
            }
        }

        final long startWrite = System.nanoTime();
        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            for (final long value : values)
            {
                this.writeIterativeShorts(value, bw);
            }
        }
        final long endWrite = System.nanoTime();

        System.out.print("" + (endWrite - startWrite) / 10240 + ";");

        final long startRead = System.nanoTime();
        try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
        {
            for (int i = 0; i < values.length; i++)
            {
                this.readIterativeShorts(br);
            }
        }
        final long endRead = System.nanoTime();

        System.out.print("" + (endRead - startRead) / 10240 + ";");
    }

    private void iterativeShortsSwitched() throws Exception
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(81920);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        final SecureRandom rn = new SecureRandom(this.seed);
        final long[] values = new long[10240];
        for (int i = 0; i < values.length; i++)
        {
            // don't use upper 4 bits - reserved for sign + length mark
            values[i] = rn.nextInt(0x0fffffff) << 32 + rn.nextInt(0x7fffffff);
            if (rn.nextBoolean())
            {
                values[i] = -values[i];
            }
        }

        final long startWrite = System.nanoTime();
        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            for (final long value : values)
            {
                this.writeIterativeShortsSwitched(value, bw);
            }
        }
        final long endWrite = System.nanoTime();

        System.out.print("" + (endWrite - startWrite) / 10240 + ";");

        final long startRead = System.nanoTime();
        try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
        {
            for (int i = 0; i < values.length; i++)
            {
                this.readIterativeShortsSwitched(br);
            }
        }
        final long endRead = System.nanoTime();

        System.out.print("" + (endRead - startRead) / 10240 + ";");
    }

    private void lengthPrefixedShortsSwitched() throws Exception
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(81920);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        final SecureRandom rn = new SecureRandom(this.seed);
        final long[] values = new long[10240];
        for (int i = 0; i < values.length; i++)
        {
            // don't use upper 4 bits - reserved for sign + length mark
            values[i] = rn.nextInt(0x0fffffff) << 32 + rn.nextInt(0x7fffffff);
            if (rn.nextBoolean())
            {
                values[i] = -values[i];
            }
        }

        final long startWrite = System.nanoTime();
        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            for (final long value : values)
            {
                this.writeLengthPrefixedShortsSwitched(value, bw);
            }
        }
        final long endWrite = System.nanoTime();

        System.out.print("" + (endWrite - startWrite) / 10240 + ";");

        final long startRead = System.nanoTime();
        try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
        {
            for (int i = 0; i < values.length; i++)
            {
                this.readLengthPrefixedShortsSwitched(br);
            }
        }
        final long endRead = System.nanoTime();

        System.out.print("" + (endRead - startRead) / 10240 + ";");
    }

    private final void writeNative(final long value, @NotNull final BinaryRawWriter rawWriter) throws BinaryObjectException
    {
        rawWriter.writeLong(value);
    }

    private final void writeIterativeBytes(final long value, @NotNull final BinaryRawWriter rawWriter) throws BinaryObjectException
    {
        if (value == 0)
        {
            final byte b = 0;
            rawWriter.writeByte(b);
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

    private final void writeIterativeBytesSwitched(final long value, @NotNull final BinaryRawWriter rawWriter) throws BinaryObjectException
    {
        if (value == 0)
        {
            final byte b = 0;
            rawWriter.writeByte(b);
        }
        else
        {
            final boolean negative = value < 0;
            final long effV = negative ? (-value - 1) : value;
            final int leadingZeros = Long.numberOfLeadingZeros(effV);

            switch (leadingZeros / 8)
            {
                case 1:
                    rawWriter.writeByte((byte) ((effV & 0x3f) | (negative ? 0xc0 : 0x80)));
                    rawWriter.writeByte((byte) (((effV >> 6) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 13) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 20) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 27) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 34) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 41) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) ((effV >> 48) & 0xff));
                    break;
                case 2:
                    rawWriter.writeByte((byte) ((effV & 0x3f) | (negative ? 0xc0 : 0x80)));
                    rawWriter.writeByte((byte) (((effV >> 6) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 13) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 20) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 27) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 34) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) ((effV >> 41) & 0xff));
                    break;
                case 3:
                    rawWriter.writeByte((byte) ((effV & 0x3f) | (negative ? 0xc0 : 0x80)));
                    rawWriter.writeByte((byte) (((effV >> 6) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 13) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 20) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 27) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) ((effV >> 34) & 0xff));
                    break;
                case 4:
                    rawWriter.writeByte((byte) ((effV & 0x3f) | (negative ? 0xc0 : 0x80)));
                    rawWriter.writeByte((byte) (((effV >> 6) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 13) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 20) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) ((effV >> 27) & 0xff));
                    break;
                case 5:
                    rawWriter.writeByte((byte) ((effV & 0x3f) | (negative ? 0xc0 : 0x80)));
                    rawWriter.writeByte((byte) (((effV >> 6) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) (((effV >> 13) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) ((effV >> 20) & 0xff));
                    break;
                case 6:
                    rawWriter.writeByte((byte) ((effV & 0x3f) | (negative ? 0xc0 : 0x80)));
                    rawWriter.writeByte((byte) (((effV >> 6) & 0x7f) | 0x80));
                    rawWriter.writeByte((byte) ((effV >> 13) & 0xff));
                    break;
                case 7:
                    rawWriter.writeByte((byte) ((effV & 0x3f) | (negative ? 0xc0 : 0x80)));
                    rawWriter.writeByte((byte) ((effV >> 6) & 0xff));
                    break;
                case 8:
                    rawWriter.writeByte((byte) ((effV & 0x3f) | (negative ? 0x40 : 0)));
                    break;
                default:
                    throw new BinaryObjectException("Long value exceeds value range for signed values in variable length integer mode");
            }
        }
    }

    private final void writeIterativeShorts(final long value, @NotNull final BinaryRawWriter rawWriter) throws BinaryObjectException
    {
        if (value == 0)
        {
            final short b = 0;
            rawWriter.writeShort(b);
        }
        else
        {
            long remainder = value;
            short s = 0;
            if (value < 0)
            {
                // the 0x4000 bit is used as the sign
                s = (short) (s | 0x4000);
                remainder = -remainder - 1;
            }
            s = (short) (s | (remainder & 0x3fff));
            remainder = remainder >> 6;
            if (remainder > 0)
            {
                s = (short) (s | 0x8000);
            }
            rawWriter.writeShort(s);

            this.writeVariableLengthShortRemainder(remainder, 3, rawWriter);
        }
    }

    private final void writeIterativeShortsSwitched(final long value, @NotNull final BinaryRawWriter rawWriter) throws BinaryObjectException
    {
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

            if (leadingZeros < 4)
            {
                throw new BinaryObjectException("Long value exceeds value range for signed values in variable length integer mode");
            }

            switch (leadingZeros / 16)
            {
                case 1:
                    rawWriter.writeShort((short) ((effV & 0x3fff) | (negative ? 0xc000 : 0x8000)));
                    rawWriter.writeShort((short) (((effV >> 14) & 0x7fff) | 0x8000));
                    rawWriter.writeShort((short) (((effV >> 29) & 0x7fff) | 0x8000));
                    rawWriter.writeShort((short) ((effV >> 44) & 0xffff));
                    break;
                case 2:
                    rawWriter.writeShort((short) ((effV & 0x3fff) | (negative ? 0xc000 : 0x8000)));
                    rawWriter.writeShort((short) (((effV >> 14) & 0x7fff) | 0x8000));
                    rawWriter.writeShort((short) ((effV >> 29) & 0xffff));
                    break;
                case 3:
                    rawWriter.writeShort((short) ((effV & 0x3fff) | (negative ? 0xc000 : 0x8000)));
                    rawWriter.writeShort((short) ((effV >> 14) & 0xffff));
                    break;
                case 4:
                    rawWriter.writeShort((short) ((effV & 0x3fff) | (negative ? 0x4000 : 0)));
                    break;
            }
        }
    }

    private final void writeLengthPrefixedShortsSwitched(final long value, @NotNull final BinaryRawWriter rawWriter)
            throws BinaryObjectException
    {
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
            }
        }
    }

    private final long readNative(@NotNull final BinaryRawReader rawReader)
    {
        return rawReader.readLong();
    }

    private final long readIterativeBytes(@NotNull final BinaryRawReader rawReader)
    {
        long value;

        final byte b = rawReader.readByte();
        value = (b & 0x3fl);

        if ((b & 0x80) == 0x80)
        {
            value = this.readVariableLengthRemainder(6, value, 6, rawReader);
        }
        if ((b & 0x40) == 0x40)
        {
            value = -(value + 1);
        }

        return value;
    }

    private final long readIterativeBytesSwitched(@NotNull final BinaryRawReader rawReader) throws BinaryObjectException
    {
        return this.readIterativeBytes(rawReader);
    }

    private final long readIterativeShorts(@NotNull final BinaryRawReader rawReader)
    {
        long value;

        final short s = rawReader.readShort();
        value = (s & 0x3fffl);

        if ((s & 0x8000) == 0x8000)
        {
            value = this.readVariableLengthShortRemainder(6, value, 3, rawReader);
        }
        if ((s & 0x4000) == 0x4000)
        {
            value = -(value + 1);
        }

        return value;
    }

    private final long readIterativeShortsSwitched(@NotNull final BinaryRawReader rawReader)
    {
        return this.readIterativeShorts(rawReader);
    }

    private final long readLengthPrefixedShortsSwitched(@NotNull final BinaryRawReader rawReader) throws BinaryObjectException
    {
        long value;

        final short s = rawReader.readShort();
        value = (s & 0x1fffl);

        final int additionalShorts = (s & 0xc000) >> 14;
        switch (additionalShorts)
        {
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
        }

        if ((s & 0x2000) == 0x2000)
        {
            value = -(value + 1);
        }

        return value;
    }

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
