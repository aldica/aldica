/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.base;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.UUID;

import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.util.GUID;
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

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(true);

                int positionOffset = 0;

                // test unsigned positive values
                serializer.write(0l, true, brw);
                positionOffset += 2;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serializer.readLong(true, brr));
                for (int shorts = 1; shorts < 5; shorts++)
                {
                    final int bits = shorts * 16 - 2;
                    final long value = (0x01l << bits) - 1;

                    serializer.write(value, true, brw);
                    positionOffset += shorts * 2;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readLong(true, brr));
                }

                // test signed positive values
                serializer.write(1l, false, brw);
                positionOffset += 2;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(1, serializer.readLong(false, brr));
                for (int shorts = 1; shorts < 5; shorts++)
                {
                    final int bits = shorts * 16 - 3;
                    final long value = (0x01l << bits) - 1;

                    serializer.write(value, false, brw);
                    positionOffset += shorts * 2;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readLong(false, brr));
                }

                // test signed negative values
                serializer.write(-1l, false, brw);
                positionOffset += 2;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(-1, serializer.readLong(false, brr));
                for (int shorts = 1; shorts < 5; shorts++)
                {
                    final int bits = shorts * 16 - 3;
                    final long value = -(0x01l << bits);

                    serializer.write(value, false, brw);
                    positionOffset += shorts * 2;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readLong(false, brr));
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

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);

            this.exEx.expect(BinaryObjectException.class);
            serializer.write(AbstractCustomBinarySerializer.LONG_AS_SHORT_UNSIGNED_MAX + 1, true, brw);
        }
    }

    @Test
    public void testVariableLongUnsignedNegativeValue() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);

            serializer.write(1l, true, brw);

            this.exEx.expect(BinaryObjectException.class);
            serializer.write(-1l, true, brw);
        }
    }

    @Test
    public void testVariableLongSignedValueTooHigh() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);

            this.exEx.expect(BinaryObjectException.class);
            serializer.write(AbstractCustomBinarySerializer.LONG_AS_SHORT_SIGNED_POSITIVE_MAX + 1, false, brw);
        }
    }

    @Test
    public void testVariableLongSignedValueTooLow() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);

            this.exEx.expect(BinaryObjectException.class);
            serializer.write(AbstractCustomBinarySerializer.LONG_AS_SHORT_SIGNED_NEGATIVE_MAX - 1, false, brw);
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

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(false);

                int positionOffset = 0;

                // test signed positive values
                serializer.write(0l, true, brw);
                positionOffset += 8;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serializer.readLong(true, brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    int bits = Math.min(63, bytes * 8);
                    long value = 0;
                    for (; bits >= 0; bits--)
                    {
                        value += 0x01l << bits;
                    }

                    serializer.write(value, true, brw);
                    positionOffset += 8;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readLong(true, brr));
                }

                // test signed negative values
                serializer.write(-1l, false, brw);
                positionOffset += 8;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(-1, serializer.readLong(false, brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    int bits = Math.min(63, bytes * 8);
                    long value = 0;
                    for (; bits >= 0; bits--)
                    {
                        value += 0x01l << bits;
                    }
                    value = -value - 1;

                    serializer.write(value, false, brw);
                    positionOffset += 8;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readLong(false, brr));
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

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(true);
                serializer.setHandleNegativeIds(false);

                int positionOffset = 0;

                // test unsigned positive values
                serializer.writeDbId(0l, brw);
                positionOffset += 2;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serializer.readLong(true, brr));
                for (int shorts = 1; shorts < 5; shorts++)
                {
                    final int bits = shorts * 16 - 2;
                    final long value = (0x01l << bits) - 1;

                    serializer.writeDbId(value, brw);
                    positionOffset += shorts * 2;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readLong(true, brr));
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

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(true);
                serializer.setHandleNegativeIds(true);

                int positionOffset = 0;

                // test signed positive values
                serializer.writeDbId(0l, brw);
                positionOffset += 2;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serializer.readLong(false, brr));
                for (int shorts = 1; shorts < 5; shorts++)
                {
                    final int bits = shorts * 16 - 3;
                    final long value = (0x01l << bits) - 1;

                    serializer.writeDbId(value, brw);
                    positionOffset += shorts * 2;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readLong(false, brr));
                }

                // test signed negative values
                serializer.writeDbId(-1l, brw);
                positionOffset += 2;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(-1, serializer.readLong(false, brr));
                for (int shorts = 1; shorts < 5; shorts++)
                {
                    final int bits = shorts * 16 - 3;
                    final long value = -(0x01l << bits);

                    serializer.writeDbId(value, brw);
                    positionOffset += shorts * 2;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readLong(false, brr));
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

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);
            serializer.setHandleNegativeIds(false);

            this.exEx.expect(BinaryObjectException.class);
            serializer.writeDbId(-1, brw);
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

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(false);

                int positionOffset = 0;

                // test unsigned positive values
                serializer.write(0, true, brw);
                positionOffset += 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serializer.readInt(true, brr));
                for (int bytes = 1; bytes < 5; bytes++)
                {
                    final int bits = bytes * 8 - Math.min(3, bytes);
                    final int value = (0x01 << bits) - 1;

                    serializer.write(value, true, brw);
                    positionOffset += 4;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readInt(true, brr));
                }

                // test signed positive values
                serializer.write(0, false, brw);
                positionOffset += 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serializer.readInt(false, brr));
                for (int bytes = 1; bytes < 5; bytes++)
                {
                    final int bits = bytes * 8 - (1 + Math.min(3, bytes));
                    final int value = (0x01 << bits) - 1;

                    serializer.write(value, false, brw);
                    positionOffset += 4;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readInt(false, brr));
                }

                // test signed negative values
                serializer.write(-1, false, brw);
                positionOffset += 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(-1, serializer.readInt(false, brr));
                for (int bytes = 1; bytes < 5; bytes++)
                {
                    final int bits = bytes * 8 - (1 + Math.min(3, bytes));
                    final int value = -(0x01 << bits);

                    serializer.write(value, false, brw);
                    positionOffset += 4;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readInt(false, brr));
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

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);

            this.exEx.expect(BinaryObjectException.class);
            serializer.write(AbstractCustomBinarySerializer.INT_AS_BYTE_UNSIGNED_MAX + 1, true, brw);
        }
    }

    @Test
    public void testVariableIntegerUnsignedNegativeValue() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);

            serializer.write(1, true, brw);

            this.exEx.expect(BinaryObjectException.class);
            serializer.write(-1, true, brw);
        }
    }

    @Test
    public void testVariableIntegerSignedValueTooHigh() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);

            this.exEx.expect(BinaryObjectException.class);
            serializer.write(AbstractCustomBinarySerializer.INT_AS_BYTE_SIGNED_POSITIVE_MAX + 1, false, brw);
        }
    }

    @Test
    public void testVariableIntegerSignedValueTooLow() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);

            this.exEx.expect(BinaryObjectException.class);
            serializer.write(AbstractCustomBinarySerializer.INT_AS_BYTE_SIGNED_NEGATIVE_MAX - 1, false, brw);
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

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(false);

                int positionOffset = 0;

                // test signed positive values
                serializer.write(0, true, brw);
                positionOffset += 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serializer.readInt(true, brr));
                for (int bytes = 1; bytes < 3; bytes++)
                {
                    int bits = Math.min(31, bytes * 8);
                    int value = 0;
                    for (; bits >= 0; bits--)
                    {
                        value += 0x01 << bits;
                    }

                    serializer.write(value, true, brw);
                    positionOffset += 4;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readInt(true, brr));
                }

                // test signed negative values
                serializer.write(-1, false, brw);
                positionOffset += 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(-1, serializer.readInt(false, brr));
                for (int bytes = 1; bytes < 3; bytes++)
                {
                    int bits = Math.min(31, bytes * 8);
                    int value = 0;
                    for (; bits >= 0; bits--)
                    {
                        value += 0x01 << bits;
                    }
                    value = -value - 1;

                    serializer.write(value, false, brw);
                    positionOffset += 4;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readInt(false, brr));
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

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(true);
                serializer.setHandle4EiBFileSizes(false);

                int positionOffset = 0;

                serializer.writeFileSize(0l, brw);
                positionOffset += 2;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serializer.readFileSize(brr));
                for (int shorts = 1; shorts < 5; shorts++)
                {
                    final int bits = shorts * 16 - 2;
                    final long value = (0x01l << bits) - 1;

                    serializer.writeFileSize(value, brw);
                    positionOffset += shorts * 2;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readFileSize(brr));
                }
            }
        }
    }

    @Test
    public void testNonVariable4EiBFileSize() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(true);
                serializer.setHandle4EiBFileSizes(true);

                int positionOffset = 0;

                serializer.writeFileSize(0l, brw);
                positionOffset += 8;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(0, serializer.readFileSize(brr));
                for (int bytes = 1; bytes < 9; bytes++)
                {
                    int bits = Math.min(63, bytes * 8);
                    long value = 0;
                    for (; bits >= 0; bits--)
                    {
                        value += 0x01l << bits;
                    }

                    serializer.writeFileSize(value, brw);
                    positionOffset += 8;
                    Assert.assertEquals(positionOffset, bos.position());
                    Assert.assertEquals(value, serializer.readFileSize(brr));
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

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);
            serializer.setHandle4EiBFileSizes(false);

            this.exEx.expect(BinaryObjectException.class);
            serializer.writeFileSize(-1, brw);
        }
    }

    @Test
    public void testNegativeNonVariableFileSize4EiB() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);
            serializer.setHandle4EiBFileSizes(true);

            serializer.writeFileSize(-1, brw);
        }
    }

    @Test
    public void testVariableFileSizeValueTooHigh() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(10240);

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            final BinaryRawWriter brw = bw.rawWriter();

            final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
            serializer.setUseVariableLengthIntegers(true);
            serializer.setHandle4EiBFileSizes(false);

            this.exEx.expect(BinaryObjectException.class);
            serializer.writeFileSize(AbstractCustomBinarySerializer.LONG_AS_SHORT_UNSIGNED_MAX + 1, brw);
        }
    }

    @Test
    public void testStringWithVariableLengthInteger() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(8192000);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(true);

                int positionOffset = 0;
                String testStr;

                // empty string
                testStr = "";
                serializer.write(testStr, brw);
                positionOffset += testStr.length() + 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serializer.readString(brr));

                final StringBuilder longStrBuilder = new StringBuilder(4096);
                // String with length fitting in 6 bits
                for (int it = 0; it < (1 << 5) / 16; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }
                longStrBuilder.delete(longStrBuilder.length() - 1, longStrBuilder.length());
                serializer.write(testStr, brw);
                positionOffset += testStr.length() + 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serializer.readString(brr));

                // String with length fitting in 14 bits
                longStrBuilder.delete(0, longStrBuilder.length());
                for (int it = 0; it < (1 << 13) / 16; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }
                longStrBuilder.delete(longStrBuilder.length() - 1, longStrBuilder.length());

                testStr = longStrBuilder.toString();
                serializer.write(testStr, brw);
                positionOffset += testStr.length() + 2;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serializer.readString(brr));

                // String with length fitting in 22 bits
                longStrBuilder.delete(0, longStrBuilder.length());
                for (int it = 0; it < (1 << 21) / 16; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }
                longStrBuilder.delete(longStrBuilder.length() - 1, longStrBuilder.length());

                testStr = longStrBuilder.toString();
                serializer.write(testStr, brw);
                positionOffset += testStr.length() + 3;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serializer.readString(brr));
            }
        }
    }

    @Test
    public void testStringWithNonVariableLengthInteger() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(8192000);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(false);

                int positionOffset = 0;
                String testStr;

                // empty string
                testStr = "";
                serializer.write(testStr, brw);
                positionOffset += testStr.length() + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serializer.readString(brr));

                final StringBuilder longStrBuilder = new StringBuilder(4096);
                // String with length fitting in 6 bits
                for (int it = 0; it < (1 << 5) / 16; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }
                longStrBuilder.delete(longStrBuilder.length() - 1, longStrBuilder.length());
                serializer.write(testStr, brw);
                positionOffset += testStr.length() + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serializer.readString(brr));

                // String with length fitting in 14 bits
                longStrBuilder.delete(0, longStrBuilder.length());
                for (int it = 0; it < (1 << 13) / 16; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }
                longStrBuilder.delete(longStrBuilder.length() - 1, longStrBuilder.length());

                testStr = longStrBuilder.toString();
                serializer.write(testStr, brw);
                positionOffset += testStr.length() + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serializer.readString(brr));

                // String with length fitting in 22 bits
                longStrBuilder.delete(0, longStrBuilder.length());
                for (int it = 0; it < (1 << 21) / 16; it++)
                {
                    longStrBuilder.append("0123456789ABCDEF");
                }
                longStrBuilder.delete(longStrBuilder.length() - 1, longStrBuilder.length());

                testStr = longStrBuilder.toString();
                serializer.write(testStr, brw);
                positionOffset += testStr.length() + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testStr, serializer.readString(brr));
            }
        }
    }

    @Test
    public void testLocaleWithVariableLengthInteger() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(1024);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(true);

                int positionOffset = 0;
                Locale testLocale;

                // simple language locale
                testLocale = Locale.ENGLISH;
                serializer.write(testLocale, brw);
                positionOffset += 2 + 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testLocale, serializer.readLocale(brr));

                // language + country locale
                testLocale = Locale.GERMANY;
                serializer.write(testLocale, brw);
                positionOffset += 5 + 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testLocale, serializer.readLocale(brr));

                // language + country + variant locale
                testLocale = new Locale("de", "DE", "1901");
                serializer.write(testLocale, brw);
                positionOffset += 10 + 1;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testLocale, serializer.readLocale(brr));
            }
        }
    }

    @Test
    public void testLocaleWithNonVariableLengthInteger() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(1024);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(false);

                int positionOffset = 0;
                Locale testLocale;

                // simple language locale
                testLocale = Locale.ENGLISH;
                serializer.write(testLocale, brw);
                positionOffset += 2 + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testLocale, serializer.readLocale(brr));

                // language + country locale
                testLocale = Locale.GERMANY;
                serializer.write(testLocale, brw);
                positionOffset += 5 + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testLocale, serializer.readLocale(brr));

                // language + country + variant locale
                testLocale = new Locale("de", "DE", "1901");
                serializer.write(testLocale, brw);
                positionOffset += 10 + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testLocale, serializer.readLocale(brr));
            }
        }
    }

    @Test
    public void testContentURLVariableLengthInteger() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(40960);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(true);
                serializer.setUseOptimisedContentURL(true);

                int positionOffset = 0;
                String testURL;

                // dynamically created without volumes + buckets
                testURL = createNewTimeBasedFileContentUrl(0);
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // dynamically created without volumes
                testURL = createNewTimeBasedFileContentUrl(256);
                serializer.writeContentURL(testURL, brw);
                positionOffset += 22;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // dynamically created without buckets
                testURL = createNewVolumeAwareTimeBasedFileContentUrl(0, "volume1");
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21 + 8;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // dynamically created
                testURL = createNewVolumeAwareTimeBasedFileContentUrl(256, "volume2");
                serializer.writeContentURL(testURL, brw);
                positionOffset += 22 + 8;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // minimum values for date path elements (no buckets / volumes)
                testURL = "store://0/1/1/0/0/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // minimum values for date path elements and buckets (no volumes)
                testURL = "store://0/1/1/0/0/0/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 22;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // maximum values for date path elements (no buckets / volumes)
                testURL = "store://4095/12/31/23/59/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // maximum values for date path elements and buckets (no volumes)
                testURL = "store://4095/12/31/23/59/255/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 22;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // unsupported path / invalid value for year path element
                testURL = "store://4096/12/31/23/59/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17 + 17;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // unsupported path / invalid value for year month element
                testURL = "store://4095/13/31/23/59/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17 + 17;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // unsupported path / invalid value for day path element
                testURL = "store://4095/12/32/23/59/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17 + 17;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // unsupported path / invalid value for hour path element
                testURL = "store://4095/12/31/24/59/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17 + 17;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // unsupported path / invalid value for minute path element
                testURL = "store://4095/12/31/23/60/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17 + 17;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // custom store (no buckets / volumes)
                testURL = "my-store://1/1/1/1/1/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21 + 9;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // no suffix (no buckets / volumes)
                testURL = "store://1/1/1/1/1/" + UUID.randomUUID().toString();
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // custom suffix (no buckets / volumes)
                testURL = "store://1/1/1/1/1/" + UUID.randomUUID().toString() + ".byn";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21 + 4;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // custom name (no buckets / volumes)
                testURL = "store://1/1/1/1/1/file.bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 5 + 5; // (1 byte flag + 4 byte path) + 5 byte name
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // no path (no buckets / volumes)
                testURL = "store://" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // non-optimisable URL
                testURL = "my-store://path/to/file.byn";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 29;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));
            }
        }
    }

    @Test
    public void testContentURLNonVariableLengthInteger() throws IOException
    {
        final BinaryHeapOutputStream bos = new BinaryHeapOutputStream(4096);
        final BinaryHeapInputStream bis = new BinaryHeapInputStream(bos.array());

        try (final BinaryWriterExImpl bw = new BinaryWriterExImpl(null, bos, null, null))
        {
            try (final BinaryReaderExImpl br = new BinaryReaderExImpl(null, bis, null, false))
            {
                final BinaryRawWriter brw = bw.rawWriter();
                final BinaryRawReader brr = br.rawReader();

                final SpecificCustomBinarySerializer serializer = new SpecificCustomBinarySerializer();
                serializer.setUseVariableLengthIntegers(false);
                serializer.setUseOptimisedContentURL(true);

                int positionOffset = 0;
                String testURL;

                // dynamically created without volumes + buckets
                testURL = createNewTimeBasedFileContentUrl(0);
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // dynamically created without volumes
                testURL = createNewTimeBasedFileContentUrl(256);
                serializer.writeContentURL(testURL, brw);
                positionOffset += 22;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // dynamically created without buckets
                testURL = createNewVolumeAwareTimeBasedFileContentUrl(0, "volume1");
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21 + 11;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // dynamically created
                testURL = createNewVolumeAwareTimeBasedFileContentUrl(256, "volume2");
                serializer.writeContentURL(testURL, brw);
                positionOffset += 22 + 11;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // minimum values for date path elements (no buckets / volumes)
                testURL = "store://0/1/1/0/0/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // minimum values for date path elements and buckets (no volumes)
                testURL = "store://0/1/1/0/0/0/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 22;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // maximum values for date path elements (no buckets / volumes)
                testURL = "store://4095/12/31/23/59/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // maximum values for date path elements and buckets (no volumes)
                testURL = "store://4095/12/31/23/59/255/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 22;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // unsupported path / invalid value for year path element
                testURL = "store://4096/12/31/23/59/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17 + 20;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // unsupported path / invalid value for year month element
                testURL = "store://4095/13/31/23/59/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17 + 20;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // unsupported path / invalid value for day path element
                testURL = "store://4095/12/32/23/59/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17 + 20;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // unsupported path / invalid value for hour path element
                testURL = "store://4095/12/31/24/59/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17 + 20;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // unsupported path / invalid value for minute path element
                testURL = "store://4095/12/31/23/60/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17 + 20;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // custom store (no buckets / volumes)
                testURL = "my-store://1/1/1/1/1/" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21 + 12;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // no suffix (no buckets / volumes)
                testURL = "store://1/1/1/1/1/" + UUID.randomUUID().toString();
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // custom suffix (no buckets / volumes)
                testURL = "store://1/1/1/1/1/" + UUID.randomUUID().toString() + ".byn";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 21 + 7;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // custom name (no buckets / volumes)
                testURL = "store://1/1/1/1/1/file.bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 5 + 8; // (1 byte flag + 4 byte path) + 8 byte name
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // no path (no buckets / volumes)
                testURL = "store://" + UUID.randomUUID().toString() + ".bin";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 17;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));

                // non-optimisable URL
                testURL = "my-store://path/to/file.byn";
                serializer.writeContentURL(testURL, brw);
                positionOffset += 32;
                Assert.assertEquals(positionOffset, bos.position());
                Assert.assertEquals(testURL, serializer.readContentURL(brr));
            }
        }
    }

    // needed to duplicate Alfresco code in static methods because it is not accessible (package-protected)
    protected static String createNewTimeBasedFileContentUrl(final int bucketsPerMinute)
    {
        final StringBuilder sb = new StringBuilder(20);
        sb.append(FileContentStore.STORE_PROTOCOL);
        sb.append(ContentStore.PROTOCOL_DELIMITER);

        final Calendar calendar = new GregorianCalendar();
        final int year = calendar.get(Calendar.YEAR);
        final int month = calendar.get(Calendar.MONTH) + 1;
        final int day = calendar.get(Calendar.DAY_OF_MONTH);
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int minute = calendar.get(Calendar.MINUTE);
        sb.append(year).append('/').append(month).append('/').append(day).append('/').append(hour).append('/').append(minute).append('/');

        if (bucketsPerMinute != 0)
        {
            final long seconds = System.currentTimeMillis() % (60 * 1000);
            final int actualBucket = (int) seconds / ((60 * 1000) / bucketsPerMinute);
            sb.append(actualBucket).append('/');
        }

        sb.append(GUID.generate()).append(".bin");
        return sb.toString();
    }

    // needed to duplicate Alfresco code in static methods because it is not accessible (package-protected)
    protected static String createNewVolumeAwareTimeBasedFileContentUrl(final int bucketsPerMinute, final String volumeName)
    {
        final StringBuilder sb = new StringBuilder(20);
        sb.append(FileContentStore.STORE_PROTOCOL);
        sb.append(ContentStore.PROTOCOL_DELIMITER);

        // for reproducibility we do not randomly pick a volume and require that the caller specify it
        sb.append(volumeName).append('/');

        final Calendar calendar = new GregorianCalendar();
        final int year = calendar.get(Calendar.YEAR);
        final int month = calendar.get(Calendar.MONTH) + 1;
        final int day = calendar.get(Calendar.DAY_OF_MONTH);
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int minute = calendar.get(Calendar.MINUTE);
        sb.append(year).append('/').append(month).append('/').append(day).append('/').append(hour).append('/').append(minute).append('/');

        if (bucketsPerMinute != 0)
        {
            final long seconds = System.currentTimeMillis() % (60 * 1000);
            final int actualBucket = (int) seconds / ((60 * 1000) / bucketsPerMinute);
            sb.append(actualBucket).append('/');
        }

        sb.append(GUID.generate()).append(".bin");
        return sb.toString();
    }
}
