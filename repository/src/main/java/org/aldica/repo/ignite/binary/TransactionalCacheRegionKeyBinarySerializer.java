/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.io.Serializable;

import org.alfresco.repo.cache.TransactionalCache;
import org.alfresco.repo.cache.TransactionalCache.CacheRegionKey;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryMarshaller;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
import org.apache.ignite.internal.util.GridUnsafe;

/**
 * Instances of this class handle (de-)serialisations of {@link TransactionalCache transactional cache} {@link CacheRegionKey region key}
 * instances into more efficient binary representations as would be possible by using the default {@link BinaryMarshaller} by optimising
 * away the hash code instance.
 *
 * @author Axel Faust
 */
public class TransactionalCacheRegionKeyBinarySerializer extends AbstractKeyBinarySerializer<CacheRegionKey>
{

    private static final String CACHE_REGION_TYPE = "cacheRegionType";

    private static final String CACHE_REGION = "cacheRegion";

    private static final String CACHE_KEY = "cacheKey";

    private static short MASK_DEFAULT_REGIONS = 0x1f;

    private static short FLAG_CUSTOM_REGION = 0x20;

    private static final long CACHE_REGION_FIELD_OFFSET = getFieldOffset(CacheRegionKey.class, CACHE_REGION, String.class);

    private static final long CACHE_KEY_FIELD_OFFSET = getFieldOffset(CacheRegionKey.class, CACHE_KEY, Serializable.class);

    private static final long HASH_CODE_FIELD_OFFSET = getFieldOffset(CacheRegionKey.class, "hashCode", int.class);

    public TransactionalCacheRegionKeyBinarySerializer()
    {
        super(CacheRegionKey.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final CacheRegionKey cacheRegionKey, final BinaryWriterEx rawWriter) throws BinaryObjectException
    {
        final String cacheRegion = (String) GridUnsafe.getObjectField(cacheRegionKey, CACHE_REGION_FIELD_OFFSET);
        final Serializable cacheKey = (Serializable) GridUnsafe.getObjectField(cacheRegionKey, CACHE_KEY_FIELD_OFFSET);

        final BinaryOutputStream out = rawWriter.out();

        short flags = 0;

        final int startPos = out.position();
        out.unsafeEnsure(2);
        out.position(startPos + 2);

        final CacheRegion literal = CacheRegion.getLiteral(cacheRegion);
        if (literal != null)
        {
            flags |= (short) literal.ordinal();
        }
        else
        {
            flags |= FLAG_CUSTOM_REGION;
        }

        if (literal == null)
        {
            this.writeString(cacheRegion, out);
        }

        flags |= this.writeKey(cacheKey, rawWriter, out);

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteShort(flags);
        out.position(endPos);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final CacheRegionKey cacheRegionKey, final BinaryWriter writer)
    {
        final String cacheRegion = (String) GridUnsafe.getObjectField(cacheRegionKey, CACHE_REGION_FIELD_OFFSET);
        final Serializable cacheKey = (Serializable) GridUnsafe.getObjectField(cacheRegionKey, CACHE_KEY_FIELD_OFFSET);

        final CacheRegion literal = CacheRegion.getLiteral(cacheRegion);
        if (literal != null)
        {
            writer.writeByte(CACHE_REGION_TYPE, (byte) literal.ordinal());
        }
        else
        {
            writer.writeString(CACHE_REGION, cacheRegion);
        }
        writer.writeObject(CACHE_KEY, cacheKey);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final CacheRegionKey cacheRegionKey, final BinaryRawReader rawReader) throws BinaryObjectException
    {
        final String cacheRegion;
        final Serializable cacheKey;

        final short flags = rawReader.readShort();

        if ((flags & FLAG_CUSTOM_REGION) != 0)
        {
            cacheRegion = this.readString(rawReader);
        }
        else
        {
            final int ordinal = (flags & MASK_DEFAULT_REGIONS);
            final CacheRegion literal = CacheRegion.values()[ordinal];
            cacheRegion = literal.getCacheRegionName();
        }

        cacheKey = this.readKey(rawReader, flags);

        GridUnsafe.putObjectField(cacheRegionKey, CACHE_REGION_FIELD_OFFSET, cacheRegion);
        GridUnsafe.putObjectField(cacheRegionKey, CACHE_KEY_FIELD_OFFSET, cacheKey);
        GridUnsafe.putIntField(cacheRegionKey, HASH_CODE_FIELD_OFFSET, cacheRegion.hashCode() + cacheKey.hashCode());
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final CacheRegionKey cacheRegionKey, final BinaryReader reader) throws BinaryObjectException
    {
        String cacheRegion;
        final Serializable cacheKey;

        cacheRegion = reader.readString(CACHE_REGION);
        if (cacheRegion == null)
        {
            final byte literalOrdinal = reader.readByte(CACHE_REGION_TYPE);
            final CacheRegion literal = CacheRegion.values()[literalOrdinal];
            cacheRegion = literal.getCacheRegionName();
        }
        cacheKey = reader.readObject(CACHE_KEY);

        GridUnsafe.putObjectField(cacheRegionKey, CACHE_REGION_FIELD_OFFSET, cacheRegion);
        GridUnsafe.putObjectField(cacheRegionKey, CACHE_KEY_FIELD_OFFSET, cacheKey);
        GridUnsafe.putIntField(cacheRegionKey, HASH_CODE_FIELD_OFFSET, cacheRegion.hashCode() + cacheKey.hashCode());
    }

}
