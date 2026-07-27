/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.Locale;

import org.alfresco.repo.domain.node.ContentDataWithId;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
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
public class ContentDataBinarySerializer extends AbstractContentSupportBinarySerializer<ContentData> implements ApplicationContextAware
{

    private static final String MIMETYPE_ID = "mimetypeId";

    private static final String ENCODING_ID = "encodingId";

    private static final String LOCALE_ID = "localeId";

    public ContentDataBinarySerializer()
    {
        super(ContentData.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void validateObject(final Object obj)
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!this.handledType.equals(cls) && !ContentDataWithId.class.equals(cls))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final ContentData contentData, final BinaryWriterEx rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();
        this.writeContentData(contentData, out);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final ContentData contentData, final BinaryWriter writer)
    {
        if (contentData instanceof ContentDataWithId)
        {
            writer.writeObject(ID, ((ContentDataWithId) contentData).getId());
        }

        writer.writeLong(SIZE, contentData.getSize());

        final String contentUrl = contentData.getContentUrl();
        if (contentUrl != null)
        {
            writer.writeString(CONTENT_URL, contentUrl);
        }

        final String mimetype = contentData.getMimetype();
        Long mimetypeId = null;
        if (mimetype != null && this.useIdsWhenReasonable)
        {
            final Pair<Long, String> mimetypePair = this.mimetypeDAO.getMimetype(mimetype);
            if (mimetypePair != null)
            {
                mimetypeId = mimetypePair.getFirst();
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

        final String encoding = contentData.getEncoding();
        Long encodingId = null;
        if (encoding != null && this.useIdsWhenReasonable)
        {
            final Pair<Long, String> encodingPair = this.encodingDAO.getEncoding(encoding);
            if (encodingPair != null)
            {
                encodingId = encodingPair.getFirst();
            }
        }

        if (encodingId != null)
        {
            writer.writeObject(ENCODING_ID, encodingId);
        }
        else if (encoding != null)
        {
            writer.writeString(ENCODING, encoding);
        }

        final Locale locale = contentData.getLocale();
        Long localeId = null;
        if (locale != null && this.useIdsWhenReasonable)
        {
            final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(locale);
            if (localePair != null)
            {
                localeId = localePair.getFirst();
            }
        }

        if (localeId != null)
        {
            writer.writeObject(LOCALE_ID, localeId);
        }
        else if (locale != null)
        {
            writer.writeString(LOCALE, locale.toString());
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final ContentData contentData, final BinaryRawReader rawReader)
    {
        this.readContentData(contentData, rawReader);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final ContentData contentData, final BinaryReader reader)
    {
        if (contentData instanceof ContentDataWithId)
        {
            final Long id = reader.readObject(ID);
            this.setContentDataId((ContentDataWithId) contentData, id);
        }

        final String contentUrl = reader.readString(CONTENT_URL);
        final long size = reader.readLong(SIZE);

        String mimetype = reader.readString(MIMETYPE);
        String encoding = reader.readString(ENCODING);
        final String localeStr = reader.readString(LOCALE);

        if (mimetype == null && this.useIdsWhenReasonable)
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

        if (encoding == null && this.useIdsWhenReasonable)
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

        Locale locale = null;
        if (localeStr == null && this.useIdsWhenReasonable)
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
        else
        {
            locale = this.convertToLocale(localeStr);
        }

        this.fillContentData(contentData, size, contentUrl, mimetype, encoding, locale);
    }
}
