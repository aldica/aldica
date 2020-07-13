/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.io.IOException;

import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryReaderExImpl;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.binary.streams.BinaryHeapInputStream;
import org.apache.ignite.internal.binary.streams.BinaryHeapOutputStream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author Axel Faust
 */
public class AbstractCustomBinarySerializerTests
{

    /**
     * @author Axel Faust
     */
    protected static class SpecificCustomBinarySerializer extends AbstractCustomBinarySerializer
    {

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
        {
            // NO-OP
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
        {
            // NO-OP
        }
    }

    @Rule
    public final ExpectedException exEx = ExpectedException.none();

    @Test
    public void testVariableLong() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseVariableLengthPrimitives(true);

                int positionOffset = 0;

                // test unsigned positive values
                serialiser.write(0l, true, brw);
                positionOffset += 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readLong(true, brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    final int bits = bytes * 8 - Math.min(7, bytes);
                    final long value = (0x01l << bits) - 1;

                    serialiser.write(value, true, brw);
                    positionOffset += bytes;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readLong(true, brr));
                }

                // test signed positive values
                serialiser.write(0l, false, brw);
                positionOffset += 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readLong(false, brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    final int bits = bytes * 8 - (1 + Math.min(7, bytes));
                    final long value = (0x01l << bits) - 1;

                    serialiser.write(value, false, brw);
                    positionOffset += bytes;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readLong(false, brr));
                }

                // test signed negative values
                serialiser.write(-1l, false, brw);
                positionOffset += 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(-1, serialiser.readLong(false, brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    final int bits = bytes * 8 - (1 + Math.min(7, bytes));
                    final long value = -(0x01l << bits);

                    serialiser.write(value, false, brw);
                    positionOffset += bytes;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readLong(false, brr));
                }
            }
        }
    }

    @Test
    public void testVariableLongUnsignedValueTooHigh() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.write(AbstractCustomBinarySerializer.LONG_AS_BYTE_UNSIGNED_MAX + 1, true, brw);
        }
    }

    @Test
    public void testVariableLongUnsignedNegativeValue() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);

            serialiser.write(1l, true, brw);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.write(-1l, true, brw);
        }
    }

    @Test
    public void testVariableLongSignedValueTooHigh() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.write(AbstractCustomBinarySerializer.LONG_AS_BYTE_SIGNED_POSITIVE_MAX + 1, false, brw);
        }
    }

    @Test
    public void testVariableLongSignedValueTooLow() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.write(AbstractCustomBinarySerializer.LONG_AS_BYTE_SIGNED_NEGATIVE_MAX - 1, false, brw);
        }
    }

    @Test
    public void testNonVariableLong() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseVariableLengthPrimitives(false);

                int positionOffset = 0;

                // test signed positive values
                serialiser.write(0l, true, brw);
                positionOffset += 8;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readLong(true, brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    int bits = Math.min(63, bytes * 8);
                    long value = 0;
                    for (; bits >= 0; bits--)
                    {
                        value += 0x01l << bits;
                    }

                    serialiser.write(value, true, brw);
                    positionOffset += 8;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readLong(true, brr));
                }

                // test signed negative values
                serialiser.write(-1l, false, brw);
                positionOffset += 8;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(-1, serialiser.readLong(false, brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    int bits = Math.min(63, bytes * 8);
                    long value = 0;
                    for (; bits >= 0; bits--)
                    {
                        value += 0x01l << bits;
                    }
                    value = -value - 1;

                    serialiser.write(value, false, brw);
                    positionOffset += 8;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readLong(false, brr));
                }
            }
        }
    }

    @Test
    public void testVariablePositiveDbId() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseVariableLengthPrimitives(true);
                serialiser.setHandleNegativeIds(false);

                int positionOffset = 0;

                // test unsigned positive values
                serialiser.writeDbId(0l, brw);
                positionOffset += 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readLong(true, brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    final int bits = bytes * 8 - Math.min(7, bytes);
                    final long value = (0x01l << bits) - 1;

                    serialiser.writeDbId(value, brw);
                    positionOffset += bytes;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readLong(true, brr));
                }
            }
        }
    }

    @Test
    public void testVariableSignedDbId() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseVariableLengthPrimitives(true);
                serialiser.setHandleNegativeIds(true);

                int positionOffset = 0;

                // test signed positive values
                serialiser.writeDbId(0l, brw);
                positionOffset += 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readLong(false, brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    final int bits = bytes * 8 - (1 + Math.min(7, bytes));
                    final long value = (0x01l << bits) - 1;

                    serialiser.writeDbId(value, brw);
                    positionOffset += bytes;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readLong(false, brr));
                }

                // test signed negative values
                serialiser.writeDbId(-1l, brw);
                positionOffset += 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(-1, serialiser.readLong(false, brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    final int bits = bytes * 8 - (1 + Math.min(7, bytes));
                    final long value = -(0x01l << bits);

                    serialiser.writeDbId(value, brw);
                    positionOffset += bytes;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readLong(false, brr));
                }
            }
        }
    }

    @Test
    public void testVariableUnsupportedNegativeDbId() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);
            serialiser.setHandleNegativeIds(false);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.writeDbId(-1, brw);
        }
    }

    @Test
    public void testVariableInteger() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseVariableLengthPrimitives(false);

                int positionOffset = 0;

                // test unsigned positive values
                serialiser.write(0, true, brw);
                positionOffset += 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readInt(true, brr));
                for (int bytes = 1; bytes < 5; bytes++)
                {
                    final int bits = bytes * 8 - Math.min(3, bytes);
                    final int value = (0x01 << bits) - 1;

                    serialiser.write(value, true, brw);
                    positionOffset += 4;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readInt(true, brr));
                }

                // test signed positive values
                serialiser.write(0, false, brw);
                positionOffset += 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readInt(false, brr));
                for (int bytes = 1; bytes < 5; bytes++)
                {
                    final int bits = bytes * 8 - (1 + Math.min(3, bytes));
                    final int value = (0x01 << bits) - 1;

                    serialiser.write(value, false, brw);
                    positionOffset += 4;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readInt(false, brr));
                }

                // test signed negative values
                serialiser.write(-1, false, brw);
                positionOffset += 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(-1, serialiser.readInt(false, brr));
                for (int bytes = 1; bytes < 5; bytes++)
                {
                    final int bits = bytes * 8 - (1 + Math.min(3, bytes));
                    final int value = -(0x01 << bits);

                    serialiser.write(value, false, brw);
                    positionOffset += 4;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readInt(false, brr));
                }
            }
        }
    }

    @Test
    public void testVariableIntegerUnsignedValueTooHigh() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.write(AbstractCustomBinarySerializer.INT_AS_BYTE_UNSIGNED_MAX + 1, true, brw);
        }
    }

    @Test
    public void testVariableIntegerUnsignedNegativeValue() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);

            serialiser.write(1, true, brw);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.write(-1, true, brw);
        }
    }

    @Test
    public void testVariableIntegerSignedValueTooHigh() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.write(AbstractCustomBinarySerializer.INT_AS_BYTE_SIGNED_POSITIVE_MAX + 1, false, brw);
        }
    }

    @Test
    public void testVariableIntegerSignedValueTooLow() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.write(AbstractCustomBinarySerializer.INT_AS_BYTE_SIGNED_NEGATIVE_MAX - 1, false, brw);
        }
    }

    @Test
    public void testNonVariableInteger() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseVariableLengthPrimitives(false);

                int positionOffset = 0;

                // test signed positive values
                serialiser.write(0, true, brw);
                positionOffset += 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readInt(true, brr));
                for (int bytes = 1; bytes < 3; bytes++)
                {
                    int bits = Math.min(31, bytes * 8);
                    int value = 0;
                    for (; bits >= 0; bits--)
                    {
                        value += 0x01 << bits;
                    }

                    serialiser.write(value, true, brw);
                    positionOffset += 4;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readInt(true, brr));
                }

                // test signed negative values
                serialiser.write(-1, false, brw);
                positionOffset += 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(-1, serialiser.readInt(false, brr));
                for (int bytes = 1; bytes < 3; bytes++)
                {
                    int bits = Math.min(31, bytes * 8);
                    int value = 0;
                    for (; bits >= 0; bits--)
                    {
                        value += 0x01 << bits;
                    }
                    value = -value - 1;

                    serialiser.write(value, false, brw);
                    positionOffset += 4;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readInt(false, brr));
                }
            }
        }
    }

    @Test
    public void testVariableFileSize() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseVariableLengthPrimitives(true);
                serialiser.setHandle128PiBFileSizes(false);
                serialiser.setHandle2EiBFileSizes(false);

                int positionOffset = 0;

                serialiser.writeFileSize(0l, brw);
                positionOffset += 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readFileSize(brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    final int bits = bytes * 8 - Math.min(7, bytes);
                    final long value = (0x01l << bits) - 1;

                    serialiser.writeFileSize(value, brw);
                    positionOffset += bytes;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readFileSize(brr));
                }
            }
        }
    }

    @Test
    public void testVariable128PiBFileSize() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseVariableLengthPrimitives(true);
                serialiser.setHandle128PiBFileSizes(true);
                serialiser.setHandle2EiBFileSizes(false);

                int positionOffset = 0;

                serialiser.writeFileSize(0l, brw);
                positionOffset += 2;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readFileSize(brr));
                for (int shorts = 1; shorts < 5; shorts++)
                {
                    final int bits = shorts * 16 - Math.min(3, shorts);
                    final long value = (0x01l << bits) - 1;

                    serialiser.writeFileSize(value, brw);
                    positionOffset += (shorts * 2);
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readFileSize(brr));
                }
            }
        }
    }

    @Test
    public void testNonVariable2EiBFileSize() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseVariableLengthPrimitives(true);
                serialiser.setHandle128PiBFileSizes(false);
                serialiser.setHandle2EiBFileSizes(true);

                int positionOffset = 0;

                serialiser.writeFileSize(0l, brw);
                positionOffset += 8;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readFileSize(brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    int bits = Math.min(63, bytes * 8);
                    long value = 0;
                    for (; bits >= 0; bits--)
                    {
                        value += 0x01l << bits;
                    }

                    serialiser.writeFileSize(value, brw);
                    positionOffset += 8;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readFileSize(brr));
                }

                // test same with slightly different config constellation (handle2EiBFileSizes must be only effective flag)
                serialiser.setHandle128PiBFileSizes(true);
                serialiser.setHandle2EiBFileSizes(true);

                serialiser.writeFileSize(0l, brw);
                positionOffset += 8;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serialiser.readFileSize(brr));
                for (int shorts = 1; shorts < 5; shorts++)
                {
                    final int bits = shorts * 16 - Math.min(3, shorts);
                    final long value = (0x01l << bits) - 1;

                    serialiser.writeFileSize(value, brw);
                    positionOffset += 8;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serialiser.readFileSize(brr));
                }
            }
        }
    }

    @Test
    public void testNegativeVariableFileSize() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);
            serialiser.setHandle128PiBFileSizes(false);
            serialiser.setHandle2EiBFileSizes(false);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.writeFileSize(-1, brw);
        }
    }

    @Test
    public void testNegativeVariableFileSize128PiB() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);
            serialiser.setHandle128PiBFileSizes(true);
            serialiser.setHandle2EiBFileSizes(false);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.writeFileSize(-1, brw);
        }
    }

    @Test
    public void testNegativeNonVariableFileSize2EiB() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);
            serialiser.setHandle128PiBFileSizes(true);
            serialiser.setHandle2EiBFileSizes(true);

            serialiser.writeFileSize(-1, brw);
        }
    }

    @Test
    public void testVariableFileSizeValueTooHigh() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);
            serialiser.setHandle128PiBFileSizes(false);
            serialiser.setHandle2EiBFileSizes(false);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.writeFileSize(AbstractCustomBinarySerializer.LONG_AS_BYTE_UNSIGNED_MAX + 1, brw);
        }
    }

    @Test
    public void testVariableFileSize128PiBValueTooHigh() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
            serialiser.setUseVariableLengthPrimitives(true);
            serialiser.setHandle128PiBFileSizes(true);
            serialiser.setHandle2EiBFileSizes(false);

            this.exEx.expect(BinaryObjectException.class);
            serialiser.write(AbstractCustomBinarySerializer.LONG_AS_SHORT_UNSIGNED_MAX + 1, false, brw);
        }
    }

    @Test
    public void testOptimisedStringWithVariableLengthPrimitive() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseOptimisedString(true);
                serialiser.setUseVariableLengthPrimitives(true);

                int positionOffset = 0;
                String testStr;

                // empty string
                testStr = "";
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));

                // simple String where length fits in 7 bits
                testStr = "Test";
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));

                final StringBuilder longStrBuilder = new StringBuilder(256);
                // String with length between 7 and 14 bits
                for (int it = 0; it < 16; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }

                testStr = longStrBuilder.toString();
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 2;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));

                // String with exceeding 14 bits
                for (int it = 0; it < 1024; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }

                testStr = longStrBuilder.toString();
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 3;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));
            }
        }
    }

    @Test
    public void testOptimisedStringWithNonVariableLengthPrimitive() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(40960);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseOptimisedString(true);
                serialiser.setUseVariableLengthPrimitives(false);

                int positionOffset = 0;
                String testStr;

                // empty string
                testStr = "";
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));

                // simple String where length fits in 7 bits
                testStr = "Test";
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));

                final StringBuilder longStrBuilder = new StringBuilder(256);
                // String with length between 7 and 14 bits
                for (int it = 0; it < 16; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }

                testStr = longStrBuilder.toString();
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));

                // String with exceeding 14 bits
                for (int it = 0; it < 1024; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }

                testStr = longStrBuilder.toString();
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));
            }
        }
    }

    @Test
    public void testNonOptimisedString() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serialiser = new SpecificCustomBinarySerializer();
                serialiser.setUseOptimisedString(false);
                serialiser.setUseVariableLengthPrimitives(false);

                int positionOffset = 0;
                String testStr;

                // empty string
                testStr = "";
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 5;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));

                // simple String where length fits in 7 bits
                testStr = "Test";
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 5;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));

                final StringBuilder longStrBuilder = new StringBuilder(256);
                // String with length between 7 and 14 bits
                for (int it = 0; it < 16; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }

                testStr = longStrBuilder.toString();
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 5;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));

                // String with exceeding 14 bits
                for (int it = 0; it < 1024; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }

                testStr = longStrBuilder.toString();
                serialiser.write(testStr, brw);
                positionOffset += testStr.length() + 5;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serialiser.readString(brr));
            }
        }
    }
}
