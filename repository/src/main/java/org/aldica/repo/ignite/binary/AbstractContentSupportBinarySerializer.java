/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.alfresco.repo.domain.encoding.EncodingDAO;
import org.alfresco.repo.domain.locale.LocaleDAO;
import org.alfresco.repo.domain.mimetype.MimetypeDAO;
import org.alfresco.repo.domain.node.ContentDataWithId;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
import org.apache.ignite.internal.util.GridUnsafe;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.extensions.surf.util.I18NUtil;

/**
 * Instances of this class provide additional (de-)serialisation support for content-related data.
 *
 * @author Axel Faust
 */
public abstract class AbstractContentSupportBinarySerializer<T> extends AbstractExtendedBinarySerializer<T>
        implements ApplicationContextAware
{

    protected static final String ID = "id";

    protected static final String CONTENT_URL = "contentUrl";

    protected static final String SIZE = "size";

    protected static final String MIMETYPE = "mimetype";

    protected static final String ENCODING = "encoding";

    protected static final String LOCALE = "locale";

    protected static final Map<String, Locale> LOCALE_CACHE = new HashMap<>();

    private static final short FLAG_CONTENT_ID = Short.MIN_VALUE;

    private static final short FLAG_CONTENT_ID_UNSIGNED = 0x4000;

    private static final short FLAG_CONTENT_URL = 0x2000;

    private static final short FLAG_MIMETYPE_ID = 0x1000;

    private static final short FLAG_MIMETYPE_ID_UNSIGNED_OR_MIMETYPE_NULL = 0x0800;

    private static final short FLAG_ENCODING_ID = 0x0400;

    private static final short FLAG_ENCODING_ID_UNSIGNED_OR_ENCODING_NULL = 0x0200;

    private static final short FLAG_LOCALE_ID = 0x0100;

    private static final short FLAG_LOCALE_ID_UNSIGNED_OR_LOCALE_NULL = 0x0080;

    private static final long CONTENT_DATA_ID_FIELD_OFFSET = getFieldOffset(ContentDataWithId.class, ID, Long.class);

    private static final long CONTENT_DATA_URL_FIELD_OFFSET = getFieldOffset(ContentData.class, CONTENT_URL, String.class);

    private static final long CONTENT_DATA_MIMETYPE_FIELD_OFFSET = getFieldOffset(ContentData.class, MIMETYPE, String.class);

    private static final long CONTENT_DATA_SIZE_FIELD_OFFSET = getFieldOffset(ContentData.class, SIZE, long.class);

    private static final long CONTENT_DATA_ENCODING_FIELD_OFFSET = getFieldOffset(ContentData.class, ENCODING, String.class);

    private static final long CONTENT_DATA_LOCALE_FIELD_OFFSET = getFieldOffset(ContentData.class, LOCALE, Locale.class);

    protected ApplicationContext applicationContext;

    protected MimetypeDAO mimetypeDAO;

    protected EncodingDAO encodingDAO;

    protected LocaleDAO localeDAO;

    protected AbstractContentSupportBinarySerializer(final Class<T> handledType)
    {
        super(handledType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void ensureDAOsAvailable()
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

    protected void writeContentData(final ContentData contentData, final BinaryOutputStream out)
    {
        // use a short for a 2 byte flag
        final int startPos = out.position();
        out.unsafeEnsure(2);
        out.position(startPos + 2);

        short flags = 0;

        final Long contentDataId = contentData instanceof ContentDataWithId ? ((ContentDataWithId) contentData).getId() : null;
        if (contentDataId != null)
        {
            flags |= FLAG_CONTENT_ID;
            flags |= this.writeWithFlagIfUnsigned(contentDataId.longValue(), FLAG_CONTENT_ID_UNSIGNED, out);
        }

        this.writeUnsigned(contentData.getSize(), out);

        final String contentUrl = contentData.getContentUrl();
        if (contentUrl != null)
        {
            flags |= FLAG_CONTENT_URL;
            this.writeString(contentUrl, out);
        }

        flags |= this.writeValueOrId(contentData.getMimetype(), this::mimetypeLookup, FLAG_MIMETYPE_ID_UNSIGNED_OR_MIMETYPE_NULL,
                FLAG_MIMETYPE_ID, FLAG_MIMETYPE_ID_UNSIGNED_OR_MIMETYPE_NULL, out);
        flags |= this.writeValueOrId(contentData.getEncoding(), this::encodingLookup, FLAG_ENCODING_ID_UNSIGNED_OR_ENCODING_NULL,
                FLAG_ENCODING_ID, FLAG_ENCODING_ID_UNSIGNED_OR_ENCODING_NULL, out);
        flags |= this.writeValueOrId(contentData.getLocale(), this::localeLookup, FLAG_LOCALE_ID_UNSIGNED_OR_LOCALE_NULL, FLAG_LOCALE_ID,
                FLAG_LOCALE_ID_UNSIGNED_OR_LOCALE_NULL, out);

        // reposition + write flags
        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteShort(flags);
        out.position(endPos);
    }

    protected ContentData readContentData(final BinaryRawReader rawReader)
    {
        // dummy content data with ID
        final ContentDataWithId cd = new ContentDataWithId(new ContentData(null, null, 0, null), null);
        this.readContentData(cd, rawReader);

        if (cd.getId() != null)
        {
            return cd;
        }

        return new ContentData(cd.getContentUrl(), cd.getMimetype(), cd.getSize(), cd.getEncoding(), cd.getLocale());
    }

    protected void readContentData(final ContentData contentData, final BinaryRawReader rawReader)
    {
        final short flags = rawReader.readShort();

        if (contentData instanceof ContentDataWithId && (flags & FLAG_CONTENT_ID) != 0)
        {
            final long contentId = this.readLong(rawReader, (flags & FLAG_CONTENT_ID_UNSIGNED) != 0);
            this.setContentDataId((ContentDataWithId) contentData, contentId);
        }

        final long size = this.readUnsignedLong(rawReader);

        final String contentUrl;
        if ((flags & FLAG_CONTENT_URL) != 0)
        {
            contentUrl = this.readString(rawReader);
        }
        else
        {
            contentUrl = null;
        }

        final String mimetype;
        final String encoding;
        final Locale locale;

        mimetype = this.readValueOrId(rawReader, this::mimetypeLookup, Function.identity(), flags,
                FLAG_MIMETYPE_ID_UNSIGNED_OR_MIMETYPE_NULL, FLAG_MIMETYPE_ID, FLAG_MIMETYPE_ID_UNSIGNED_OR_MIMETYPE_NULL);
        encoding = this.readValueOrId(rawReader, this::encodingLookup, Function.identity(), flags,
                FLAG_ENCODING_ID_UNSIGNED_OR_ENCODING_NULL, FLAG_ENCODING_ID, FLAG_ENCODING_ID_UNSIGNED_OR_ENCODING_NULL);
        locale = this.readValueOrId(rawReader, this::localeLookup, this::convertToLocale, flags, FLAG_LOCALE_ID_UNSIGNED_OR_LOCALE_NULL,
                FLAG_LOCALE_ID, FLAG_LOCALE_ID_UNSIGNED_OR_LOCALE_NULL);

        this.fillContentData(contentData, size, contentUrl, mimetype, encoding, locale);
    }

    protected void setContentDataId(final ContentDataWithId contentData, final Long id)
    {
        GridUnsafe.putObjectField(contentData, CONTENT_DATA_ID_FIELD_OFFSET, id);
    }

    protected void fillContentData(final ContentData contentData, final long size, final String contentUrl, final String mimetype,
            final String encoding, final Locale locale)
    {
        GridUnsafe.putLongField(contentData, CONTENT_DATA_SIZE_FIELD_OFFSET, size);
        GridUnsafe.putObjectField(contentData, CONTENT_DATA_URL_FIELD_OFFSET, contentUrl);
        GridUnsafe.putObjectField(contentData, CONTENT_DATA_MIMETYPE_FIELD_OFFSET, mimetype);
        GridUnsafe.putObjectField(contentData, CONTENT_DATA_ENCODING_FIELD_OFFSET, encoding);
        GridUnsafe.putObjectField(contentData, CONTENT_DATA_LOCALE_FIELD_OFFSET, locale);
    }

    protected Pair<Long, String> mimetypeLookup(final String mimetype)
    {
        Pair<Long, String> result = null;
        if (this.useIdsWhenReasonable)
        {
            result = this.doLookup(LOOKUP_BUCKET_MIMETYPE, mimetype, this.mimetypeDAO::getMimetype);
        }
        return result;
    }

    protected Pair<Long, String> encodingLookup(final String encoding)
    {
        Pair<Long, String> result = null;
        if (this.useIdsWhenReasonable)
        {
            result = this.doLookup(LOOKUP_BUCKET_ENCODING, encoding, this.encodingDAO::getEncoding);
        }
        return result;
    }

    protected Pair<Long, Locale> localeLookup(final Locale locale)
    {
        Pair<Long, Locale> result = null;
        if (this.useIdsWhenReasonable)
        {
            result = this.doLookup(LOOKUP_BUCKET_LOCALE, locale, this.localeDAO::getLocalePair);
        }
        return result;
    }

    protected Pair<Long, String> mimetypeLookup(final Long id)
    {
        Pair<Long, String> result = null;
        if (this.useIdsWhenReasonable)
        {
            result = this.doLookup(LOOKUP_BUCKET_MIMETYPE, id, this.mimetypeDAO::getMimetype);
        }
        return result;
    }

    protected Pair<Long, String> encodingLookup(final Long id)
    {
        Pair<Long, String> result = null;
        if (this.useIdsWhenReasonable)
        {
            result = this.doLookup(LOOKUP_BUCKET_ENCODING, id, this.encodingDAO::getEncoding);
        }
        return result;
    }

    protected Pair<Long, Locale> localeLookup(final Long id)
    {
        Pair<Long, Locale> result = null;
        if (this.useIdsWhenReasonable)
        {
            result = this.doLookup(LOOKUP_BUCKET_LOCALE, id, this.localeDAO::getLocalePair);
        }
        return result;
    }

    protected Locale convertToLocale(final String s)
    {
        return LOCALE_CACHE.computeIfAbsent(s, I18NUtil::parseLocale);
    }
}
