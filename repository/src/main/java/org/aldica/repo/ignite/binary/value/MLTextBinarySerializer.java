/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.domain.locale.LocaleDAO;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryMarshaller;
import org.apache.ignite.internal.marshaller.optimized.OptimizedMarshaller;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Instances of this class handle (de-)serialisations of {@link MLText} instances. As a custom sub-class of {@link HashMap}, MLText
 * instances cannot be covered by Ignite's default/preferred {@link BinaryMarshaller} and falls back to {@link OptimizedMarshaller}. While
 * already faster than regular JVM serialisation, it does not necessarily result in the smallest serial form, and we are able to apply
 * optional optimisations during marshalling via this class.
 *
 *
 * @author Axel Faust
 */
public class MLTextBinarySerializer extends AbstractCustomBinarySerializer implements ApplicationContextAware
{

    private static final String VALUES = "values";

    private static final byte FLAG_LOCALE_ID = 1;

    private static final byte FLAG_VALUE_NULL = 2;

    protected ApplicationContext applicationContext;

    protected LocaleDAO localeDAO;

    protected boolean useIdsWhenReasonable = false;

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
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(MLText.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        if (this.useIdsWhenReasonable)
        {
            this.ensureLocaleDAOAvailable();
        }

        final MLText mlText = (MLText) obj;

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();
            this.write(mlText.size(), true, rawWriter);

            for (final Entry<Locale, String> entry : mlText.entrySet())
            {
                final Locale key = entry.getKey();
                final String value = entry.getValue();

                final byte flags = value != null ? 0 : FLAG_VALUE_NULL;

                if (this.useIdsWhenReasonable)
                {
                    final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(key);

                    if (localePair != null)
                    {
                        rawWriter.writeByte((byte) (flags | FLAG_LOCALE_ID));
                        this.writeDbId(localePair.getFirst(), rawWriter);
                    }
                    else
                    {
                        rawWriter.writeByte(flags);
                        this.write(key, rawWriter);
                    }
                }
                else
                {
                    rawWriter.writeByte(flags);
                    this.write(key.toString(), rawWriter);
                }

                if (value != null)
                {
                    this.write(value, rawWriter);
                }
            }
        }
        else
        {
            if (this.useIdsWhenReasonable)
            {
                final Map<Object, String> values = new HashMap<>();
                mlText.forEach((l, s) -> {
                    final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(l);
                    values.put(localePair != null ? localePair.getFirst() : l.toString(), s);
                });
                writer.writeMap(VALUES, values);
            }
            else
            {
                final Map<Object, String> values = new HashMap<>();
                mlText.forEach((l, s) -> {
                    values.put(l.toString(), s);
                });
                writer.writeMap(VALUES, values);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(MLText.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        if (this.useIdsWhenReasonable)
        {
            this.ensureLocaleDAOAvailable();
        }

        final MLText mlText = (MLText) obj;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();
            final int size = this.readInt(true, rawReader);

            for (int idx = 0; idx < size; idx++)
            {
                final byte flags = rawReader.readByte();
                Locale key;

                if (!this.useIdsWhenReasonable && ((FLAG_LOCALE_ID) & flags) != 0)
                {
                    throw new BinaryObjectException("Serializer is not configured to use IDs in place of Locale");
                }

                if (this.useIdsWhenReasonable && ((FLAG_LOCALE_ID) & flags) != 0)
                {
                    final long id = this.readDbId(rawReader);
                    final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(id);
                    if (localePair == null)
                    {
                        throw new BinaryObjectException("Cannot resolve Locale for ID " + id);
                    }
                    key = localePair.getSecond();
                }
                else
                {
                    key = this.readLocale(rawReader);
                }

                final String value;
                if ((flags & FLAG_VALUE_NULL) != 0)
                {
                    value = null;
                }
                else
                {
                    value = this.readString(rawReader);
                }
                mlText.addValue(key, value);
            }
        }
        else
        {
            final Map<Object, String> values = reader.readMap(VALUES);
            values.forEach((k, v) -> {
                Locale k2;
                if (k instanceof Long)
                {
                    if (!this.useIdsWhenReasonable)
                    {
                        throw new BinaryObjectException("Serializer is not configured to use IDs in place of Locale");
                    }
                    final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair((Long) k);
                    if (localePair == null)
                    {
                        throw new BinaryObjectException("Cannot resolve Locale for ID " + k);
                    }
                    k2 = localePair.getSecond();
                }
                else if (k instanceof String)
                {
                    // we know there is at least a default converter for Locale, maybe even an optimised (caching) one
                    k2 = DefaultTypeConverter.INSTANCE.convert(Locale.class, k);
                }
                else
                {
                    k2 = (Locale) k;
                }
                mlText.addValue(k2, v);
            });
        }
    }

    protected void ensureLocaleDAOAvailable() throws BinaryObjectException
    {
        if (this.localeDAO == null)
        {
            try
            {
                this.localeDAO = this.applicationContext.getBean("localeDAO", LocaleDAO.class);
            }
            catch (final BeansException be)
            {
                throw new BinaryObjectException("Cannot (de-)serialise MLText in current configuration without access to LocaleDAO", be);
            }
        }
    }
}
