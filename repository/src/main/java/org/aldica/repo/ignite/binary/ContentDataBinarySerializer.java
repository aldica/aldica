/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.lang.reflect.Field;
import java.util.Locale;

import org.alfresco.repo.domain.encoding.EncodingDAO;
import org.alfresco.repo.domain.locale.LocaleDAO;
import org.alfresco.repo.domain.mimetype.MimetypeDAO;
import org.alfresco.repo.domain.node.ContentDataWithId;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
import org.apache.ignite.binary.BinaryWriter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Instances of this class handle (de-)serialisations of {@link ContentData} and {@link ContentDataWithId} instances.
 *
 * This implementation is capable of replacing {@link ContentData#getMimetype() mimetype}, {@link ContentData#getEncoding() encoding} and
 * {@link ContentData#getLocale() locale} with their corresponding IDs for a more efficient serial form. It can be reasonably expected that
 * these IDs can be efficiently resolved using fully replicated caches, especially given their extremely low numbers / variety.
 *
 *
 * @author Axel Faust
 */
public class ContentDataBinarySerializer implements BinarySerializer, ApplicationContextAware
{

    private static final String ID = "id";

    private static final String CONTENT_URL = "contentUrl";

    private static final String SIZE = "size";

    private static final String MIMETYPE = "mimetype";

    private static final String MIMETYPE_ID = "mimetypeId";

    private static final String ENCODING = "encoding";

    private static final String ENCODING_ID = "encodingId";

    private static final String LOCALE = "locale";

    private static final String LOCALE_ID = "localeId";

    private static final byte FLAG_MIMETYPE_NULL = 1;

    private static final byte FLAG_MIMETYPE_ID = 2;

    private static final byte FLAG_ENCODING_NULL = 4;

    private static final byte FLAG_ENCODING_ID = 8;

    // locale is technically never null in ContentData (ensured via constructor)
    private static final byte FLAG_LOCALE_NULL = 16;

    private static final byte FLAG_LOCALE_ID = 32;

    private static final Field CONTENT_URL_FIELD;

    private static final Field MIMETYPE_FIELD;

    private static final Field SIZE_FIELD;

    private static final Field ENCODING_FIELD;

    private static final Field LOCALE_FIELD;

    private static final Field ID_FIELD;

    static
    {
        try
        {
            CONTENT_URL_FIELD = ContentData.class.getDeclaredField(CONTENT_URL);
            MIMETYPE_FIELD = ContentData.class.getDeclaredField(MIMETYPE);
            SIZE_FIELD = ContentData.class.getDeclaredField(SIZE);
            ENCODING_FIELD = ContentData.class.getDeclaredField(ENCODING);
            LOCALE_FIELD = ContentData.class.getDeclaredField(LOCALE);
            ID_FIELD = ContentDataWithId.class.getDeclaredField(ID);

            CONTENT_URL_FIELD.setAccessible(true);
            MIMETYPE_FIELD.setAccessible(true);
            SIZE_FIELD.setAccessible(true);
            ENCODING_FIELD.setAccessible(true);
            LOCALE_FIELD.setAccessible(true);
            ID_FIELD.setAccessible(true);
        }
        catch (final NoSuchFieldException nsfe)
        {
            throw new RuntimeException("Failed to initialise reflective field accessors", nsfe);
        }
    }

    protected ApplicationContext applicationContext;

    protected MimetypeDAO mimetypeDAO;

    protected EncodingDAO encodingDAO;

    protected LocaleDAO localeDAO;

    protected boolean useIdsWhenReasonable = false;

    protected boolean useRawSerialForm = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     * @param useIdsWhenReasonable
     *            the useIdsWhenReasonable to set
     */
    public void setUseIdsWhenReasonable(final boolean useIdsWhenReasonable)
    {
        this.useIdsWhenReasonable = useIdsWhenReasonable;
    }

    /**
     * @param useRawSerialForm
     *            the useRawSerialForm to set
     */
    public void setUseRawSerialForm(final boolean useRawSerialForm)
    {
        this.useRawSerialForm = useRawSerialForm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(ContentData.class) && !cls.equals(ContentDataWithId.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        if (this.useIdsWhenReasonable)
        {
            this.ensureDAOsAvailable();
        }

        final ContentData contentData = (ContentData) obj;

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();

            this.writeRawSerialForm(contentData, rawWriter);
        }
        else
        {
            this.writeRegularSerialForm(contentData, writer);
        }
    }

    protected void writeRawSerialForm(final ContentData contentData, final BinaryRawWriter rawWriter)
    {
        if (contentData instanceof ContentDataWithId)
        {
            rawWriter.writeLong(((ContentDataWithId) contentData).getId());
        }

        rawWriter.writeString(contentData.getContentUrl());
        rawWriter.writeLong(contentData.getSize());

        final String mimetype = contentData.getMimetype();
        Long mimetypeId = null;
        final String encoding = contentData.getEncoding();
        Long encodingId = null;
        final Locale locale = contentData.getLocale();
        Long localeId = null;

        byte flags = 0;
        if (mimetype == null)
        {
            flags = (byte) (flags | FLAG_MIMETYPE_NULL);
        }
        else if (this.useIdsWhenReasonable)
        {
            final Pair<Long, String> mimetypePair = this.mimetypeDAO.getMimetype(mimetype);
            if (mimetypePair != null)
            {
                flags = (byte) (flags | FLAG_MIMETYPE_ID);
                mimetypeId = mimetypePair.getFirst();
            }
        }

        if (encoding == null)
        {
            flags = (byte) (flags | FLAG_ENCODING_NULL);
        }
        else if (this.useIdsWhenReasonable)
        {
            final Pair<Long, String> encodingPair = this.encodingDAO.getEncoding(encoding);
            if (encodingPair != null)
            {
                flags = (byte) (flags | FLAG_ENCODING_ID);
                encodingId = encodingPair.getFirst();
            }
        }

        if (locale == null)
        {
            flags = (byte) (flags | FLAG_LOCALE_NULL);
        }
        else if (this.useIdsWhenReasonable)
        {
            final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(locale);
            if (localePair != null)
            {
                flags = (byte) (flags | FLAG_LOCALE_ID);
                localeId = localePair.getFirst();
            }
        }
        rawWriter.writeByte(flags);

        if (mimetypeId != null)
        {
            rawWriter.writeLong(mimetypeId);
        }
        else if (mimetype != null)
        {
            rawWriter.writeString(mimetype);
        }

        if (encodingId != null)
        {
            rawWriter.writeLong(encodingId);
        }
        else if (encoding != null)
        {
            rawWriter.writeString(encoding);
        }

        if (localeId != null)
        {
            rawWriter.writeLong(localeId);
        }
        else if (locale != null)
        {
            rawWriter.writeObject(locale);
        }
    }

    protected void writeRegularSerialForm(final ContentData contentData, final BinaryWriter writer)
    {
        if (contentData instanceof ContentDataWithId)
        {
            writer.writeLong(ID, ((ContentDataWithId) contentData).getId());
        }

        writer.writeString(CONTENT_URL, contentData.getContentUrl());
        writer.writeLong(SIZE, contentData.getSize());

        final String mimetype = contentData.getMimetype();
        Long mimetypeId = null;
        final String encoding = contentData.getEncoding();
        Long encodingId = null;
        final Locale locale = contentData.getLocale();
        Long localeId = null;

        if (mimetype != null && this.useIdsWhenReasonable)
        {
            final Pair<Long, String> mimetypePair = this.mimetypeDAO.getMimetype(mimetype);
            if (mimetypePair != null)
            {
                mimetypeId = mimetypePair.getFirst();
            }
        }

        if (encoding != null && this.useIdsWhenReasonable)
        {
            final Pair<Long, String> encodingPair = this.encodingDAO.getEncoding(encoding);
            if (encodingPair != null)
            {
                encodingId = encodingPair.getFirst();
            }
        }

        if (locale != null && this.useIdsWhenReasonable)
        {
            final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(locale);
            if (localePair != null)
            {
                localeId = localePair.getFirst();
            }
        }

        if (mimetypeId != null)
        {
            writer.writeObject(MIMETYPE_ID, mimetypeId);
        }
        else if (mimetype != null)
        {
            writer.writeString(MIMETYPE, mimetype);
        }

        if (encodingId != null)
        {
            writer.writeObject(ENCODING_ID, encodingId);
        }
        else if (encoding != null)
        {
            writer.writeString(ENCODING, encoding);
        }

        if (localeId != null)
        {
            writer.writeObject(LOCALE_ID, localeId);
        }
        else if (locale != null)
        {
            writer.writeObject(LOCALE, locale);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(ContentData.class) && !cls.equals(ContentDataWithId.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        if (this.useIdsWhenReasonable)
        {
            this.ensureDAOsAvailable();
        }

        final ContentData contentData = (ContentData) obj;
        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            this.readRawSerialForm(contentData, rawReader);
        }
        else
        {
            this.readRegularSerialForm(contentData, reader);
        }
    }

    protected void readRawSerialForm(final ContentData contentData, final BinaryRawReader rawReader)
    {
        if (contentData instanceof ContentDataWithId)
        {
            final long id = rawReader.readLong();
            try
            {
                ID_FIELD.set(contentData, id);
            }
            catch (final IllegalAccessException iae)
            {
                throw new BinaryObjectException("Failed to write deserialised field values", iae);
            }
        }

        final String contentUrl = rawReader.readString();
        final long size = rawReader.readLong();

        String mimetype = null;
        String encoding = null;
        Locale locale = null;

        final byte flags = rawReader.readByte();
        if (!this.useIdsWhenReasonable && ((FLAG_MIMETYPE_ID | FLAG_ENCODING_ID | FLAG_LOCALE_ID) & flags) != 0)
        {
            throw new BinaryObjectException("Serializer is not configured to use IDs in place of content data fragments");
        }

        if ((flags & FLAG_MIMETYPE_ID) == FLAG_MIMETYPE_ID)
        {
            final long mimetypeId = rawReader.readLong();
            final Pair<Long, String> mimetypePair = this.mimetypeDAO.getMimetype(mimetypeId);
            if (mimetypePair != null)
            {
                mimetype = mimetypePair.getSecond();
            }
            else
            {
                throw new BinaryObjectException("Cannot resolve mimetype for ID " + mimetypeId);
            }
        }
        else if ((flags & FLAG_MIMETYPE_NULL) == 0)
        {
            mimetype = rawReader.readString();
        }

        if ((flags & FLAG_ENCODING_ID) == FLAG_ENCODING_ID)
        {
            final long encodingId = rawReader.readLong();
            final Pair<Long, String> encodingPair = this.encodingDAO.getEncoding(encodingId);
            if (encodingPair != null)
            {
                encoding = encodingPair.getSecond();
            }
            else
            {
                throw new BinaryObjectException("Cannot resolve encoding for ID " + encodingId);
            }
        }
        else if ((flags & FLAG_ENCODING_NULL) == 0)
        {
            encoding = rawReader.readString();
        }

        if ((flags & FLAG_LOCALE_ID) == FLAG_LOCALE_ID)
        {
            final long localeId = rawReader.readLong();
            final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(localeId);
            if (localePair != null)
            {
                locale = localePair.getSecond();
            }
            else
            {
                throw new BinaryObjectException("Cannot resolve locale for ID " + localeId);
            }
        }
        else if ((flags & FLAG_LOCALE_NULL) == 0)
        {
            locale = rawReader.readObject();
        }

        try
        {
            CONTENT_URL_FIELD.set(contentData, contentUrl);
            MIMETYPE_FIELD.set(contentData, mimetype);
            SIZE_FIELD.set(contentData, size);
            ENCODING_FIELD.set(contentData, encoding);
            LOCALE_FIELD.set(contentData, locale);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

    protected void readRegularSerialForm(final ContentData contentData, final BinaryReader reader)
    {
        if (contentData instanceof ContentDataWithId)
        {
            final long id = reader.readLong(ID);
            try
            {
                ID_FIELD.set(contentData, id);
            }
            catch (final IllegalAccessException iae)
            {
                throw new BinaryObjectException("Failed to write deserialised field values", iae);
            }
        }

        final String contentUrl = reader.readString(CONTENT_URL);
        final long size = reader.readLong(SIZE);

        String mimetype = reader.readString(MIMETYPE);
        String encoding = reader.readString(ENCODING);
        Locale locale = reader.readObject(LOCALE);

        if (mimetype == null)
        {
            final Long mimetypeId = reader.readObject(MIMETYPE_ID);
            if (mimetypeId != null)
            {
                final Pair<Long, String> mimetypePair = this.mimetypeDAO.getMimetype(mimetypeId);
                if (mimetypePair != null)
                {
                    mimetype = mimetypePair.getSecond();
                }
                else if (mimetypeId != 0)
                {
                    throw new BinaryObjectException("Cannot resolve mimetype for ID " + mimetypeId);
                }
            }
        }

        if (encoding == null)
        {
            final Long encodingId = reader.readObject(ENCODING_ID);
            if (encodingId != null)
            {
                final Pair<Long, String> encodingPair = this.encodingDAO.getEncoding(encodingId);
                if (encodingPair != null)
                {
                    encoding = encodingPair.getSecond();
                }
                else if (encodingId != 0)
                {
                    throw new BinaryObjectException("Cannot resolve encoding for ID " + encodingId);
                }
            }
        }

        if (locale == null)
        {
            final Long localeId = reader.readObject(LOCALE_ID);
            if (localeId != null)
            {
                final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(localeId);
                if (localePair != null)
                {
                    locale = localePair.getSecond();
                }
                else if (localeId != 0)
                {
                    throw new BinaryObjectException("Cannot resolve locale for ID " + localeId);
                }
            }
        }

        try
        {
            CONTENT_URL_FIELD.set(contentData, contentUrl);
            MIMETYPE_FIELD.set(contentData, mimetype);
            SIZE_FIELD.set(contentData, size);
            ENCODING_FIELD.set(contentData, encoding);
            LOCALE_FIELD.set(contentData, locale);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

    protected void ensureDAOsAvailable() throws BinaryObjectException
    {
        if (this.mimetypeDAO == null)
        {
            try
            {
                this.mimetypeDAO = this.applicationContext.getBean("mimetypeDAO", MimetypeDAO.class);
            }
            catch (final BeansException be)
            {
                throw new BinaryObjectException(
                        "Cannot (de-)serialise ContentData/ContentDataWithId in current configuration without access to MImetypeDAO", be);
            }
        }

        if (this.encodingDAO == null)
        {
            try
            {
                this.encodingDAO = this.applicationContext.getBean("encodingDAO", EncodingDAO.class);
            }
            catch (final BeansException be)
            {
                throw new BinaryObjectException(
                        "Cannot (de-)serialise ContentData/ContentDataWithId in current configuration without access to EncodingDAO", be);
            }
        }

        if (this.localeDAO == null)
        {
            try
            {
                this.localeDAO = this.applicationContext.getBean("localeDAO", LocaleDAO.class);
            }
            catch (final BeansException be)
            {
                throw new BinaryObjectException(
                        "Cannot (de-)serialise ContentData/ContentDataWithId in current configuration without access to LocaleDAO", be);
            }
        }
    }
}
