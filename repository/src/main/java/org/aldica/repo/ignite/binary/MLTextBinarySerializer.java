/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.alfresco.repo.domain.locale.LocaleDAO;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
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
public class MLTextBinarySerializer implements BinarySerializer, ApplicationContextAware
{

    protected ApplicationContext applicationContext;

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
            rawWriter.writeInt(mlText.size());

            for (final Entry<Locale, String> entry : mlText.entrySet())
            {
                final Locale key = entry.getKey();
                final String value = entry.getValue();
                if (this.useIdsWhenReasonable)
                {
                    final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(key);
                    rawWriter.writeBoolean(localePair != null);
                    if (localePair != null)
                    {
                        rawWriter.writeLong(localePair.getFirst());
                    }
                    else
                    {
                        rawWriter.writeObject(key);
                    }
                }
                else
                {
                    rawWriter.writeObject(key);
                }
                rawWriter.writeString(value);
            }
        }
        else
        {
            if (this.useIdsWhenReasonable)
            {
                final Map<Object, String> values = new HashMap<>();
                mlText.forEach((l, s) -> {
                    final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(l);
                    values.put(localePair != null ? localePair.getFirst() : l, s);
                });
                writer.writeMap("values", values);
            }
            else
            {
                // must be wrapped otherwise it would be written as self-referential handle
                // effectively preventing ANY values from being written
                writer.writeMap("values", Collections.unmodifiableMap(mlText));
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
            final int size = rawReader.readInt();

            for (int idx = 0; idx < size; idx++)
            {
                Locale key;
                if (this.useIdsWhenReasonable)
                {
                    final boolean isId = rawReader.readBoolean();
                    if (isId)
                    {
                        final long id = rawReader.readLong();
                        final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair(id);
                        if (localePair == null)
                        {
                            throw new BinaryObjectException("Cannot resolve Locale for ID " + id);
                        }
                        key = localePair.getSecond();
                    }
                    else
                    {
                        key = rawReader.readObject();
                    }
                }
                else
                {
                    key = rawReader.readObject();
                }
                final String value = rawReader.readObject();
                mlText.addValue(key, value);
            }
        }
        else
        {
            final Map<Object, String> values = reader.readMap("values");
            if (this.useIdsWhenReasonable)
            {
                values.forEach((k, v) -> {
                    Locale k2;
                    if (k instanceof Long)
                    {
                        final Pair<Long, Locale> localePair = this.localeDAO.getLocalePair((Long) k);
                        if (localePair == null)
                        {
                            throw new BinaryObjectException("Cannot resolve Locale for ID " + k);
                        }
                        k2 = localePair.getSecond();
                    }
                    else
                    {
                        k2 = (Locale) k;
                    }
                    mlText.addValue(k2, v);
                });
            }
            else
            {
                values.forEach((k, v) -> mlText.addValue((Locale) k, v));
            }
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
