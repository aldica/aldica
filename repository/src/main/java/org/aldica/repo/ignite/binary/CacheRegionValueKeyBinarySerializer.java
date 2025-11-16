/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.io.Serializable;

import org.alfresco.repo.cache.lookup.CacheRegionValueKey;
import org.alfresco.repo.cache.lookup.EntityLookupCache;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryMarshaller;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
import org.apache.ignite.internal.util.GridUnsafe;

/**
 * Instances of this class handle (de-)serialisations of {@link EntityLookupCache entity lookup} {@link CacheRegionValueKey cache region
 * value key} instances into more efficient binary representations as would be possible by using the default {@link BinaryMarshaller} by
 * optimising away the hash code instance and reducing the average cost of handling the (typically pre-defined / well-known) region names.
 *
 * @author Axel Faust
 */
public class CacheRegionValueKeyBinarySerializer extends AbstractKeyBinarySerializer<CacheRegionValueKey>
{

    private static final String CACHE_REGION_TYPE = "cacheRegionType";

    private static final String CACHE_REGION = "cacheRegion";

    private static final String CACHE_VALUE_KEY = "cacheValueKey";

    private static short MASK_DEFAULT_REGIONS = 0x1f;

    private static short FLAG_CUSTOM_REGION = 0x20;

    private static final long CACHE_REGION_FIELD_OFFSET = getFieldOffset(CacheRegionValueKey.class, CACHE_REGION, String.class);

    private static final long CACHE_VALUE_KEY_FIELD_OFFSET = getFieldOffset(CacheRegionValueKey.class, CACHE_VALUE_KEY, Serializable.class);

    private static final long HASH_CODE_FIELD_OFFSET = getFieldOffset(CacheRegionValueKey.class, "hashCode", int.class);

    public CacheRegionValueKeyBinarySerializer()
    {
        super(CacheRegionValueKey.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final CacheRegionValueKey cacheRegionValueKey, final BinaryWriterExImpl rawWriter)
            throws BinaryObjectException
    {
        final String cacheRegion = (String) GridUnsafe.getObjectField(cacheRegionValueKey, CACHE_REGION_FIELD_OFFSET);
        final Serializable cacheValueKey = (Serializable) GridUnsafe.getObjectField(cacheRegionValueKey, CACHE_VALUE_KEY_FIELD_OFFSET);

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

        flags |= this.writeKey(cacheValueKey, rawWriter, out);

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
    protected void writeRegularSerialForm(final CacheRegionValueKey cacheRegionValueKey, final BinaryWriter writer)
    {
        final String cacheRegion = (String) GridUnsafe.getObjectField(cacheRegionValueKey, CACHE_REGION_FIELD_OFFSET);
        final Serializable cacheValueKey = (Serializable) GridUnsafe.getObjectField(cacheRegionValueKey, CACHE_VALUE_KEY_FIELD_OFFSET);

        final CacheRegion literal = CacheRegion.getLiteral(cacheRegion);
        if (literal != null)
        {
            writer.writeByte(CACHE_REGION_TYPE, (byte) literal.ordinal());
        }
        else
        {
            writer.writeString(CACHE_REGION, cacheRegion);
        }
        writer.writeObject(CACHE_VALUE_KEY, cacheValueKey);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final CacheRegionValueKey cacheRegionValueKey, final BinaryRawReader rawReader)
            throws BinaryObjectException
    {
        final String cacheRegion;
        final Serializable cacheValueKey;

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

        cacheValueKey = this.readKey(rawReader, flags);

        GridUnsafe.putObjectField(cacheRegionValueKey, CACHE_REGION_FIELD_OFFSET, cacheRegion);
        GridUnsafe.putObjectField(cacheRegionValueKey, CACHE_VALUE_KEY_FIELD_OFFSET, cacheValueKey);
        GridUnsafe.putIntField(cacheRegionValueKey, HASH_CODE_FIELD_OFFSET, cacheRegion.hashCode() + cacheValueKey.hashCode());
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final CacheRegionValueKey cacheRegionValueKey, final BinaryReader reader)
            throws BinaryObjectException
    {
        String cacheRegion;
        final Serializable cacheValueKey;

        cacheRegion = reader.readString(CACHE_REGION);
        if (cacheRegion == null)
        {
            final byte literalOrdinal = reader.readByte(CACHE_REGION_TYPE);
            final CacheRegion literal = CacheRegion.values()[literalOrdinal];
            cacheRegion = literal.getCacheRegionName();
        }
        cacheValueKey = reader.readObject(CACHE_VALUE_KEY);

        GridUnsafe.putObjectField(cacheRegionValueKey, CACHE_REGION_FIELD_OFFSET, cacheRegion);
        GridUnsafe.putObjectField(cacheRegionValueKey, CACHE_VALUE_KEY_FIELD_OFFSET, cacheValueKey);
        GridUnsafe.putIntField(cacheRegionValueKey, HASH_CODE_FIELD_OFFSET, cacheRegion.hashCode() + cacheValueKey.hashCode());
    }
}
