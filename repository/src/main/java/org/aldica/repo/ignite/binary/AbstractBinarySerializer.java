/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryReaderEx;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryInputStream;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
import org.apache.ignite.internal.util.GridUnsafe;

/**
 * Instances of this class provide for the (de-)serialisation of specific value objects. In this class, various common patterns have been
 * aggregated that are reused across all sub-classes. The raw binary serialisation logic relies on big endianess for its optimisations.
 *
 * @author Axel Faust
 */
public abstract class AbstractBinarySerializer<T> implements BinarySerializer
{

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static class LookupContext
    {

        private Map[] lookupMaps = new Map[4];

        private <V, K> V doLookup(final int lookupBucket, final K key, final Function<K, V> lazyLookup)
        {
            if (lookupBucket >= this.lookupMaps.length)
            {
                final Map[] oldMaps = this.lookupMaps;
                final Map[] newMaps = new Map[oldMaps.length * 2];
                System.arraycopy(oldMaps, 0, newMaps, 0, oldMaps.length);
                this.lookupMaps = newMaps;
            }

            Map<K, V> lookupMap;
            if (this.lookupMaps[lookupBucket] == null)
            {
                lookupMap = this.lookupMaps[lookupBucket] = new HashMap<>();
            }
            else
            {
                lookupMap = this.lookupMaps[lookupBucket];
            }

            return lookupMap.computeIfAbsent(key, lazyLookup);
        }
    }

    // copied from TransactionSupportUtil
    private static final String RESOURCE_KEY_TXN_ID = "AlfrescoTransactionSupport.txnId";

    private static final String RESOURCE_KEY_LOOKUP_CONTEXT = AbstractBinarySerializer.class.getName() + "-lookupContext";

    private static final byte MASK_UNSIGNED_INTEGER_LENGTH = Byte.MIN_VALUE | 0x40;

    private static final byte MASK_UNSIGNED_LONG_LENGTH = Byte.MIN_VALUE | 0x60;

    private static final byte FLAGS_UNSIGNED_INTEGER_BYTE = 0x0;

    private static final byte FLAGS_UNSIGNED_INTEGER_SHORT = 0x40;

    private static final byte FLAGS_UNSIGNED_LONG_BYTE = 0x0;

    private static final byte FLAGS_UNSIGNED_LONG_SHORT = 0x20;

    private static final byte FLAGS_UNSIGNED_LONG_INT = 0x40;

    private static final byte FLAGS_UNSIGNED_MAX_LENGTH = Byte.MIN_VALUE;

    private static final byte MASK_UNSIGNED_MAX_LENGTH = 0x7f;

    private static final byte MASK_UNSIGNED_INTEGER_BYTE = 0x3f;

    private static final short MASK_UNSIGNED_INTEGER_SHORT = 0x3fff;

    private static final byte MASK_UNSIGNED_LONG_BYTE = 0x1f;

    private static final short MASK_UNSIGNED_LONG_SHORT = 0x1fff;

    private static final int MASK_UNSIGNED_LONG_INT = 0x1fffffff;

    private final ThreadLocal<LookupContext> lookupContext = new ThreadLocal<>();

    protected final Class<T> handledType;

    protected boolean useIdsWhenReasonable = false;

    protected boolean useRawSerialForm = false;

    protected AbstractBinarySerializer(final Class<T> handledType)
    {
        ParameterCheck.mandatory("handledType", handledType);
        this.handledType = handledType;
    }

    /**
     * @param useIdsWhenReasonable
     *     the useIdsWhenReasonable to set
     */
    public void setUseIdsWhenReasonable(final boolean useIdsWhenReasonable)
    {
        this.useIdsWhenReasonable = useIdsWhenReasonable;
    }

    /**
     * @param useRawSerialForm
     *     the useRawSerialForm to set
     */
    public void setUseRawSerialForm(final boolean useRawSerialForm)
    {
        this.useRawSerialForm = useRawSerialForm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void writeBinary(final Object obj, final BinaryWriter writer)
    {
        this.validateObject(obj);

        if (this.useIdsWhenReasonable)
        {
            this.ensureDAOsAvailable();
        }

        final T t = this.handledType.cast(obj);

        this.doInValidContext(t, writer, this::doWriteBinary);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void readBinary(final Object obj, final BinaryReader reader)
    {
        this.validateObject(obj);

        if (this.useIdsWhenReasonable)
        {
            this.ensureDAOsAvailable();
        }

        final T t = this.handledType.cast(obj);

        this.doInValidContext(t, reader, this::doReadBinary);
    }

    protected void validateObject(final Object obj)
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!this.handledType.equals(cls))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }
    }

    protected void ensureDAOsAvailable()
    {
        // NO-OP in base class
    }

    /**
     * Writes the state of the object in a raw serial form.
     *
     * @param obj
     *     the object for which to write the state
     * @param rawWriter
     *     the raw binary writer
     */
    abstract protected void writeRawSerialForm(T obj, BinaryWriterEx rawWriter);

    /**
     * Writes the state of the object in a regular serial form (with underlying Ignite schema)
     *
     * @param obj
     *     the object for which to write the state
     * @param writer
     *     the binary writer
     */
    abstract protected void writeRegularSerialForm(T obj, BinaryWriter writer);

    /**
     * Reads the state of the object from a raw serial form.
     *
     * @param obj
     *     the object for which to read the state
     * @param rawReader
     *     the raw binary reader
     */
    abstract protected void readRawSerialForm(T obj, BinaryRawReader rawReader);

    /**
     * Reads the state of the object from a regular serial form.
     *
     * @param obj
     *     the object for which to read the state
     * @param reader
     *     the binary reader
     */
    abstract protected void readRegularSerialForm(T obj, BinaryReader reader);

    protected <K> short writeValueOrId(final K value, final Function<K, Pair<Long, K>> entityLookup, final short nullFlag,
            final short idFlag, final short unsignedFlag, final BinaryOutputStream out)
    {
        return this.writeValueOrId(value, entityLookup, this::writeValueAsString, nullFlag, idFlag, unsignedFlag, out);
    }

    protected <K> byte writeValueOrId(final K value, final Function<K, Pair<Long, K>> entityLookup, final byte nullFlag, final byte idFlag,
            final byte unsignedFlag, final BinaryOutputStream out)
    {
        return this.writeValueOrId(value, entityLookup, this::writeValueAsString, nullFlag, idFlag, unsignedFlag, out);
    }

    protected <K> short writeValueOrId(final K value, final Function<K, Pair<Long, K>> entityLookup,
            final BiConsumer<K, BinaryOutputStream> valueWriter, final short nullFlag, final short idFlag, final short unsignedFlag,
            final BinaryOutputStream out)
    {
        short flags = 0;
        if (value == null)
        {
            flags |= nullFlag;
        }
        else
        {
            final Long entityId = getId(value, entityLookup);
            if (entityId == null)
            {
                valueWriter.accept(value, out);
            }
            else
            {
                flags |= idFlag;
                flags |= this.writeWithFlagIfUnsigned(entityId.longValue(), unsignedFlag, out);
            }
        }
        return flags;
    }

    protected <K> byte writeValueOrId(final K value, final Function<K, Pair<Long, K>> entityLookup,
            final BiConsumer<K, BinaryOutputStream> valueWriter, final byte nullFlag, final byte idFlag, final byte unsignedFlag,
            final BinaryOutputStream out)
    {
        byte flags = 0;
        if (value == null)
        {
            flags |= nullFlag;
        }
        else
        {
            final Long entityId = getId(value, entityLookup);
            if (entityId == null)
            {
                valueWriter.accept(value, out);
            }
            else
            {
                flags |= idFlag;
                flags |= this.writeWithFlagIfUnsigned(entityId.longValue(), unsignedFlag, out);
            }
        }
        return flags;
    }

    protected <K> void writeValue(final K value, final BinaryOutputStream out)
    {
        this.writeValue(value, this::writeValueAsString, (byte) 0, out);
    }

    protected <K> short writeValue(final K value, final short nullFlag, final BinaryOutputStream out)
    {
        return this.writeValue(value, this::writeValueAsString, nullFlag, out);
    }

    protected <K> byte writeValue(final K value, final byte nullFlag, final BinaryOutputStream out)
    {
        return this.writeValue(value, this::writeValueAsString, nullFlag, out);
    }

    protected <K> short writeValue(final K value, final BiConsumer<K, BinaryOutputStream> valueWriter, final short nullFlag,
            final BinaryOutputStream out)
    {
        short flags = 0;
        if (value == null)
        {
            flags |= nullFlag;
        }
        else
        {
            valueWriter.accept(value, out);
        }
        return flags;
    }

    protected <K> byte writeValue(final K value, final BiConsumer<K, BinaryOutputStream> valueWriter, final byte nullFlag,
            final BinaryOutputStream out)
    {
        byte flags = 0;
        if (value == null)
        {
            flags |= nullFlag;
        }
        else
        {
            valueWriter.accept(value, out);
        }
        return flags;
    }

    protected <K> short writeValueId(final K value, final Function<K, Pair<Long, K>> entityLookup, final short unsignedFlag,
            final BinaryOutputStream out)
    {
        final Long entityId = getId(value, entityLookup);
        if (entityId == null)
        {
            throw new BinaryObjectException("Cannot resolve " + value + " to DB ID");
        }
        return this.writeWithFlagIfUnsigned(entityId.longValue(), unsignedFlag, out);
    }

    protected <K> byte writeValueId(final K value, final Function<K, Pair<Long, K>> entityLookup, final byte unsignedFlag,
            final BinaryOutputStream out)
    {
        final Long entityId = getId(value, entityLookup);
        if (entityId == null)
        {
            throw new BinaryObjectException("Cannot resolve " + value + " to DB ID");
        }
        return this.writeWithFlagIfUnsigned(entityId.longValue(), unsignedFlag, out);
    }

    protected <K> K readValueOrId(final BinaryRawReader reader, final Function<Long, Pair<Long, K>> lookup,
            final Function<String, K> converter, final short flags, final short nullFlag, final short idFlag, final short unsignedFlag)
    {
        return this.doReadValueOrId(reader, lookup, rawReader -> {
            final String string = this.readString(rawReader);
            return converter.apply(string);
        }, flags, nullFlag, idFlag, unsignedFlag);
    }

    protected <K> K readValueOrId(final BinaryRawReader reader, final Function<Long, Pair<Long, K>> lookup,
            final Function<String, K> converter, final byte flags, final byte nullFlag, final byte idFlag, final byte unsignedFlag)
    {
        return this.doReadValueOrId(reader, lookup, rawReader -> {
            final String string = this.readString(rawReader);
            return converter.apply(string);
        }, flags, nullFlag, idFlag, unsignedFlag);
    }

    protected <K> K doReadValueOrId(final BinaryRawReader reader, final Function<Long, Pair<Long, K>> lookup,
            final Function<BinaryRawReader, K> valueReader, final short flags, final short nullFlag, final short idFlag,
            final short unsignedFlag)
    {
        final K value;
        if ((flags & idFlag) != 0)
        {
            final long entityId = this.readLong(reader, (flags & unsignedFlag) != 0);
            final Pair<Long, K> pair = lookup.apply(Long.valueOf(entityId));
            value = pair != null ? pair.getSecond() : null;
        }
        else if ((flags & (idFlag | nullFlag)) == 0)
        {
            value = valueReader.apply(reader);
        }
        else
        {
            value = null;
        }
        return value;
    }

    protected <K> K doReadValueOrId(final BinaryRawReader reader, final Function<Long, Pair<Long, K>> lookup,
            final Function<BinaryRawReader, K> valueReader, final byte flags, final byte nullFlag, final byte idFlag,
            final byte unsignedFlag)
    {
        final K value;
        if ((flags & idFlag) != 0)
        {
            final long entityId = this.readLong(reader, (flags & unsignedFlag) != 0);
            final Pair<Long, K> pair = lookup.apply(Long.valueOf(entityId));
            value = pair != null ? pair.getSecond() : null;
        }
        else if ((flags & (idFlag | nullFlag)) == 0)
        {
            value = valueReader.apply(reader);
        }
        else
        {
            value = null;
        }
        return value;
    }

    protected <K> K readValue(final BinaryRawReader reader, final Function<String, K> converter)
    {
        final String s = this.readString(reader);
        return converter.apply(s);
    }

    protected <K> K readValue(final BinaryRawReader reader, final Function<String, K> converter, final short flags, final short nullFlag,
            final short idFlag)
    {
        return this.doReadValue(reader, r -> {
            final String s = this.readString(r);
            return converter.apply(s);
        }, flags, nullFlag, idFlag);
    }

    protected <K> K doReadValue(final BinaryRawReader reader, final Function<BinaryRawReader, K> valueReader, final short flags,
            final short nullFlag, final short idFlag)
    {
        final K value;
        if ((flags & (idFlag | nullFlag)) == 0)
        {
            value = valueReader.apply(reader);
        }
        else
        {
            value = null;
        }
        return value;
    }

    protected <K> K readValueId(final BinaryRawReader reader, final Function<Long, Pair<Long, K>> lookup, final boolean unsigned)
    {
        final K value;

        final long entityId = this.readLong(reader, unsigned);
        final Pair<Long, K> pair = lookup.apply(Long.valueOf(entityId));
        value = pair != null ? pair.getSecond() : null;

        return value;
    }

    protected void writeString(final String str, final BinaryOutputStream out)
    {
        final byte[] bytes = str.getBytes(StandardCharsets.UTF_8);

        final int length = bytes.length;
        this.writeUnsigned(length, out);

        out.unsafeEnsure(length);
        for (final byte b : bytes)
        {
            out.unsafeWriteByte(b);
        }
    }

    protected String readString(final BinaryRawReader rawReader)
    {
        final int length = this.readUnsignedInt(rawReader);
        final byte[] bytes;

        if (rawReader instanceof BinaryReaderEx)
        {
            final BinaryInputStream in = ((BinaryReaderEx) rawReader).in();
            bytes = in.readByteArray(length);
        }
        else
        {
            bytes = new byte[length];
            for (int idx = 0; idx < length; idx++)
            {
                bytes[idx] = rawReader.readByte();
            }
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }

    protected int writeWithFlagIfUnsigned(final long value, final int unsignedFlag, final BinaryOutputStream out)
    {
        long l = value;
        if (l >= 0)
        {
            this.writeUnsigned(l, out);
            return unsignedFlag;
        }
        l = Math.abs(l + 1);
        this.writeUnsigned(l, out);
        return 0;
    }

    protected int writeWithFlagIfUnsigned(final int value, final int unsignedFlag, final BinaryOutputStream out)
    {
        long l = value;
        if (l >= 0)
        {
            this.writeUnsigned(l, out);
            return unsignedFlag;
        }
        l = Math.abs(l + 1);
        this.writeUnsigned(l, out);
        return 0;
    }

    protected short writeWithFlagIfUnsigned(final long value, final short unsignedFlag, final BinaryOutputStream out)
    {
        long l = value;
        if (l >= 0)
        {
            this.writeUnsigned(l, out);
            return unsignedFlag;
        }
        l = Math.abs(l + 1);
        this.writeUnsigned(l, out);
        return 0;
    }

    protected short writeWithFlagIfUnsigned(final int value, final short unsignedFlag, final BinaryOutputStream out)
    {
        long l = value;
        if (l >= 0)
        {
            this.writeUnsigned(l, out);
            return unsignedFlag;
        }
        l = Math.abs(l + 1);
        this.writeUnsigned(l, out);
        return 0;
    }

    protected byte writeWithFlagIfUnsigned(final long value, final byte unsignedFlag, final BinaryOutputStream out)
    {
        long l = value;
        if (l >= 0)
        {
            this.writeUnsigned(l, out);
            return unsignedFlag;
        }
        l = Math.abs(l + 1);
        this.writeUnsigned(l, out);
        return 0;
    }

    protected byte writeWithFlagIfUnsigned(final int value, final byte unsignedFlag, final BinaryOutputStream out)
    {
        int l = value;
        if (l >= 0)
        {
            this.writeUnsigned(l, out);
            return unsignedFlag;
        }
        l = Math.abs(l + 1);
        this.writeUnsigned(l, out);
        return 0;
    }

    protected void writeUnsigned(final long value, final BinaryOutputStream out)
    {
        if (value <= MASK_UNSIGNED_LONG_BYTE)
        {
            out.unsafeEnsure(1);
            writeByteOf(out, value, 0, MASK_UNSIGNED_LONG_BYTE, FLAGS_UNSIGNED_LONG_BYTE);
        }
        else if (value <= MASK_UNSIGNED_LONG_SHORT)
        {
            out.unsafeEnsure(2);
            writeByteOf(out, value, 8, MASK_UNSIGNED_LONG_BYTE, FLAGS_UNSIGNED_LONG_SHORT);
            writeByteOf(out, value);
        }
        else if (value <= MASK_UNSIGNED_LONG_INT)
        {
            out.unsafeEnsure(4);
            writeByteOf(out, value, 24, MASK_UNSIGNED_LONG_BYTE, FLAGS_UNSIGNED_LONG_INT);
            writeByteOf(out, value, 16);
            writeByteOf(out, value, 8);
            writeByteOf(out, value);
        }
        else
        {
            out.unsafeEnsure(8);
            writeByteOf(out, value, 56, FLAGS_UNSIGNED_MAX_LENGTH);
            writeByteOf(out, value, 48);
            writeByteOf(out, value, 40);
            writeByteOf(out, value, 32);
            writeByteOf(out, value, 24);
            writeByteOf(out, value, 16);
            writeByteOf(out, value, 8);
            writeByteOf(out, value);
        }
    }

    protected void writeUnsigned(final int value, final BinaryOutputStream out)
    {
        if (value <= MASK_UNSIGNED_INTEGER_BYTE)
        {
            out.unsafeEnsure(1);
            writeByteOf(out, value, 0, MASK_UNSIGNED_INTEGER_BYTE, FLAGS_UNSIGNED_INTEGER_BYTE);
        }
        else if (value <= MASK_UNSIGNED_INTEGER_SHORT)
        {
            out.unsafeEnsure(2);
            writeByteOf(out, value, 8, MASK_UNSIGNED_INTEGER_BYTE, FLAGS_UNSIGNED_INTEGER_SHORT);
            writeByteOf(out, value);
        }
        else
        {
            out.unsafeEnsure(4);
            writeByteOf(out, value, 24, FLAGS_UNSIGNED_MAX_LENGTH);
            writeByteOf(out, value, 16);
            writeByteOf(out, value, 8);
            writeByteOf(out, value);
        }
    }

    protected long readLong(final BinaryRawReader rawReader, final boolean unsigned)
    {
        long value = this.readUnsignedLong(rawReader);

        if (!unsigned)
        {
            value = value * -1;
            value -= 1;
        }

        return value;
    }

    protected int readInt(final BinaryRawReader rawReader, final boolean unsigned)
    {
        int value = this.readUnsignedInt(rawReader);

        if (!unsigned)
        {
            value = value * -1;
            value -= 1;
        }

        return value;
    }

    protected long readUnsignedLong(final BinaryRawReader rawReader)
    {
        final byte topByte = rawReader.readByte();
        final byte flags = (byte) (topByte & MASK_UNSIGNED_LONG_LENGTH);

        long value = 0;

        switch (flags)
        {
            case FLAGS_UNSIGNED_LONG_BYTE:
                value = topByte;
                break;
            case FLAGS_UNSIGNED_LONG_SHORT:
                value = (topByte & MASK_UNSIGNED_LONG_BYTE) << 8;
                value |= ((long) rawReader.readByte()) & 0xff;
                break;
            case FLAGS_UNSIGNED_LONG_INT:
                value = (topByte & MASK_UNSIGNED_LONG_BYTE) << 24;
                value |= (((long) rawReader.readByte()) & 0xff) << 16;
                value |= (((long) rawReader.readByte()) & 0xff) << 8;
                value |= ((long) rawReader.readByte()) & 0xff;
                break;
            default:
                value = ((long) (topByte & MASK_UNSIGNED_MAX_LENGTH)) << 56;
                value |= (((long) rawReader.readByte()) & 0xff) << 48;
                value |= (((long) rawReader.readByte()) & 0xff) << 40;
                value |= (((long) rawReader.readByte()) & 0xff) << 32;
                value |= (((long) rawReader.readByte()) & 0xff) << 24;
                value |= (((long) rawReader.readByte()) & 0xff) << 16;
                value |= (((long) rawReader.readByte()) & 0xff) << 8;
                value |= ((long) rawReader.readByte()) & 0xff;
                break;
        }

        return value;
    }

    protected int readUnsignedInt(final BinaryRawReader rawReader)
    {
        final byte topByte = rawReader.readByte();
        final byte flags = (byte) (topByte & MASK_UNSIGNED_INTEGER_LENGTH);

        int value = 0;

        switch (flags)
        {
            case FLAGS_UNSIGNED_INTEGER_BYTE:
                value = topByte & 0xff;
                break;
            case FLAGS_UNSIGNED_INTEGER_SHORT:
                value = (topByte & MASK_UNSIGNED_INTEGER_BYTE) << 8;
                value |= rawReader.readByte() & 0xff;
                break;
            default:
                value = (topByte & MASK_UNSIGNED_MAX_LENGTH) << 24;
                value |= (rawReader.readByte() & 0xff) << 16;
                value |= (rawReader.readByte() & 0xff) << 8;
                value |= rawReader.readByte() & 0xff;
                break;
        }

        return value;
    }

    protected <K> void writeValueAsString(final K value, final BinaryOutputStream out)
    {
        final String str = value instanceof String ? (String) value : value.toString();
        this.writeString(str, out);
    }

    protected <K, V> V doLookup(final int lookupBucket, final K key, final Function<K, V> lazyLookup)
    {
        final LookupContext lookupContext = this.lookupContext.get();
        final V value = lookupContext != null ? lookupContext.doLookup(lookupBucket, key, lazyLookup) : lazyLookup.apply(key);
        return value;
    }

    protected static long getFieldOffset(final Class<?> cls, final String fieldName, final Class<?> fieldType)
    {
        Field field;
        try
        {
            field = cls.getDeclaredField(fieldName);
        }
        catch (NoSuchFieldException | SecurityException e)
        {
            throw new IllegalStateException("Cannot get field " + fieldName + " in class " + cls, e);
        }

        final Class<?> type = field.getType();
        if (!fieldType.equals(type))
        {
            throw new IllegalStateException("Field " + fieldName + " in class " + cls + " is not of type " + fieldType);
        }

        return GridUnsafe.objectFieldOffset(field);
    }

    private <T1, T2> void doInValidContext(final T1 t1, final T2 t2, final BiConsumer<T1, T2> operation)
    {
        // make sure any txn cache writes through - no guarantee they will run their afterCommit after this
        // this has the side effect of not reading any elements in txn caches yet to be pushed to global cache
        boolean inAfterCompletion = false;
        String txnId = null;
        if (this.useIdsWhenReasonable)
        {
            final long txnStartTime = TransactionSupportUtil.getTransactionStartTime();
            txnId = TransactionSupportUtil.getTransactionId();
            inAfterCompletion = txnStartTime == -1 && txnId != null;
            if (inAfterCompletion)
            {
                TransactionSupportUtil.unbindResource(RESOURCE_KEY_TXN_ID);
            }

            if (txnId != null)
            {
                LookupContext lookupContext = TransactionSupportUtil.getResource(RESOURCE_KEY_LOOKUP_CONTEXT);
                if (lookupContext == null)
                {
                    lookupContext = new LookupContext();
                    TransactionSupportUtil.bindResource(RESOURCE_KEY_LOOKUP_CONTEXT, lookupContext);
                }
                this.lookupContext.set(lookupContext);
            }
            else
            {
                this.lookupContext.set(new LookupContext());
            }
        }

        try
        {
            operation.accept(t1, t2);
        }
        finally
        {
            if (this.useIdsWhenReasonable && inAfterCompletion)
            {
                TransactionSupportUtil.bindResource(RESOURCE_KEY_TXN_ID, txnId);
            }
            this.lookupContext.remove();
        }
    }

    private void doWriteBinary(final T t, final BinaryWriter writer)
    {
        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();
            // BinaryMetadataCollector.rawWriter() yields a dummy proxy with interface
            if (rawWriter instanceof BinaryWriterEx && !Proxy.isProxyClass(rawWriter.getClass()))
            {
                this.writeRawSerialForm(t, (BinaryWriterEx) rawWriter);
            }
            // else: no need to write - BinaryMetadataCollector only used to collect schema info
            // we have no schema in rawSerialForm
        }
        else
        {
            this.writeRegularSerialForm(t, writer);
        }
    }

    private void doReadBinary(final T t, final BinaryReader reader)
    {
        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            this.readRawSerialForm(t, rawReader);
        }
        else
        {
            this.readRegularSerialForm(t, reader);
        }
    }

    private static void writeByteOf(final BinaryOutputStream out, final long value, final int shift, final int mask, final byte flags)
    {
        out.unsafeWriteByte((byte) (((value >> shift) & mask) | flags));
    }

    private static void writeByteOf(final BinaryOutputStream out, final long value, final int shift)
    {
        out.unsafeWriteByte((byte) ((value >> shift) & 0xff));
    }

    private static void writeByteOf(final BinaryOutputStream out, final long value, final int shift, final byte flags)
    {
        out.unsafeWriteByte((byte) (((value >> shift) & 0xff) | flags));
    }

    private static void writeByteOf(final BinaryOutputStream out, final long value)
    {
        out.unsafeWriteByte((byte) (value & 0xff));
    }

    private static void writeByteOf(final BinaryOutputStream out, final int value, final int shift, final int mask, final byte flags)
    {
        out.unsafeWriteByte((byte) (((value >> shift) & mask) | flags));
    }

    private static void writeByteOf(final BinaryOutputStream out, final int value, final int shift)
    {
        out.unsafeWriteByte((byte) ((value >> shift) & 0xff));
    }

    private static void writeByteOf(final BinaryOutputStream out, final int value, final int shift, final byte flags)
    {
        out.unsafeWriteByte((byte) (((value >> shift) & 0xff) | flags));
    }

    private static void writeByteOf(final BinaryOutputStream out, final int value)
    {
        out.unsafeWriteByte((byte) (value & 0xff));
    }

    private static <K> Long getId(final K key, final Function<K, Pair<Long, K>> lookup)
    {
        final Pair<Long, K> pair = key != null ? lookup.apply(key) : null;
        return pair != null ? pair.getFirst() : null;
    }
}
