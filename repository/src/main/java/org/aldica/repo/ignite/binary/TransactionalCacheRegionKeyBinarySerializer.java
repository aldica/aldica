/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.lang.reflect.Field;

import org.alfresco.repo.cache.TransactionalCache;
import org.alfresco.repo.cache.TransactionalCache.CacheRegionKey;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryMarshaller;

/**
 * Instances of this class handle (de-)serialisations of {@link TransactionalCache transactional cache} {@link CacheRegionKey region key}
 * instances into more efficient binary representations as would be possible by using the default {@link BinaryMarshaller} by optimising
 * away the hash code instance.
 *
 * @author Axel Faust
 */
public class TransactionalCacheRegionKeyBinarySerializer implements BinarySerializer
{

    private static final String CACHE_REGION_TYPE = "cacheRegionType";

    private static final String CACHE_REGION = "cacheRegion";

    private static final String CACHE_KEY = "cacheKey";

    private static final Field CACHE_REGION_FIELD;

    private static final Field CACHE_KEY_FIELD;

    private static final Field HASH_CODE_FIELD;

    static
    {
        try
        {
            CACHE_REGION_FIELD = CacheRegionKey.class.getDeclaredField(CACHE_REGION);
            CACHE_KEY_FIELD = CacheRegionKey.class.getDeclaredField(CACHE_KEY);
            HASH_CODE_FIELD = CacheRegionKey.class.getDeclaredField("hashCode");

            CACHE_REGION_FIELD.setAccessible(true);
            CACHE_KEY_FIELD.setAccessible(true);
            HASH_CODE_FIELD.setAccessible(true);
        }
        catch (final NoSuchFieldException nsfe)
        {
            throw new RuntimeException("Failed to initialise reflective field accessors", nsfe);
        }
    }

    protected boolean useRawSerialForm = false;

    /**
     * @param useRawSerialForm
     *            the useRawSerialForm to set
     */
    public void setUseRawSerialForm(final boolean useRawSerialForm)
    {
        this.useRawSerialForm = useRawSerialForm;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(CacheRegionKey.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        try
        {
            final String cacheRegion = (String) CACHE_REGION_FIELD.get(obj);
            final Object cacheKey = CACHE_KEY_FIELD.get(obj);

            final CacheRegion literal = CacheRegion.getLiteral(cacheRegion);

            if (this.useRawSerialForm)
            {
                final BinaryRawWriter rawWriter = writer.rawWriter();
                rawWriter.writeByte((byte) literal.ordinal());
                if (literal == CacheRegion.CUSTOM)
                {
                    rawWriter.writeString(cacheRegion);
                }
                rawWriter.writeObject(cacheKey);
            }
            else
            {
                writer.writeByte(CACHE_REGION_TYPE, (byte) literal.ordinal());
                if (literal == CacheRegion.CUSTOM)
                {
                    writer.writeString(CACHE_REGION, cacheRegion);
                }
                writer.writeObject(CACHE_KEY, cacheKey);
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
        if (!cls.equals(CacheRegionKey.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        String cacheRegion = null;
        CacheRegion literal;
        Object cacheKey;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            final byte literalOrdinal = rawReader.readByte();
            literal = CacheRegion.values()[literalOrdinal];
            if (literal == CacheRegion.CUSTOM)
            {
                cacheRegion = rawReader.readString();
            }
            cacheKey = rawReader.readObject();
        }
        else
        {
            final byte literalOrdinal = reader.readByte(CACHE_REGION_TYPE);
            literal = CacheRegion.values()[literalOrdinal];
            if (literal == CacheRegion.CUSTOM)
            {
                cacheRegion = reader.readString(CACHE_REGION);
            }
            cacheKey = reader.readObject(CACHE_KEY);
        }

        cacheRegion = cacheRegion != null ? cacheRegion : literal.getCacheRegionName();

        try
        {
            CACHE_REGION_FIELD.set(obj, cacheRegion);
            CACHE_KEY_FIELD.set(obj, cacheKey);
            // reconstruct the hash code
            HASH_CODE_FIELD.set(obj,
                    Integer.valueOf((cacheRegion != null ? cacheRegion.hashCode() : 0) + (cacheKey != null ? cacheKey.hashCode() : 0)));
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

}
