/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.aldica.repo.ignite.cache.NodePropertiesCacheMap;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.node.ContentDataWithId;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.namespace.QName;
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
 * Instances of this class handle (de-)serialisations of {@link NodePropertiesCacheMap} instances. By using that sub-class of
 * {@link HashMap} to persist cached property values and this serializer implementation we are able to apply optimisations during
 * marshalling, resulting in generally smaller binary representations.
 *
 * This implementation is capable of replacing {@link QName property keys} and {@link ContentDataWithId content data values} with their
 * corresponding IDs for a more efficient serial form. These two replacements are guarded by separate configuration flags as both have
 * different levels of impact on performance. It can be reasonably expected that QName instances can be efficiently resolved using fully
 * replicated caches, due to reasonably low numbers of class/feature qualified names from dictionary models (in the hundreds to low
 * thousands range). But ContentDataWithId instances can well be in the millions or billions for larger systems, and their resolution miss
 * partitioned caches and/or require network calls to retrieve values from different grid members.
 *
 * @author Axel Faust
 */
public class NodePropertiesBinarySerializer implements BinarySerializer, ApplicationContextAware
{

    private static final String VALUES = "values";

    private static final String REGULAR_VALUES = "regularValues";

    private static final String CONTENT_ID_VALUES = "contentIdValues";

    private static final byte FLAG_QNAME_ID = 1;

    private static final byte FLAG_CONTENT_DATA_VALUE_ID = 2;

    private static final byte FLAG_MULTI_VALUED = 4;

    private static final byte FLAG_NULL = 8;

    private static final byte TYPE_NULL = 0;

    private static final byte TYPE_DEFAULT = 1;

    private static final byte TYPE_LIST = 2;

    private static final byte TYPE_BOOLEAN = 3;

    private static final byte TYPE_INTEGER = 4;

    private static final byte TYPE_LONG = 5;

    private static final byte TYPE_FLOAT = 6;

    private static final byte TYPE_DOUBLE = 7;

    private static final byte TYPE_STRING = 8;

    private static final byte TYPE_DATE = 9;

    protected ApplicationContext applicationContext;

    protected QNameDAO qnameDAO;

    protected ContentDataDAO contentDataDAO;

    protected boolean useIdsWhenReasonable = false;

    protected boolean useIdsWhenPossible = false;

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
     * @param useIdsWhenPossible
     *            the useIdsWhenPossible to set
     */
    public void setUseIdsWhenPossible(final boolean useIdsWhenPossible)
    {
        this.useIdsWhenPossible = useIdsWhenPossible;
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
        if (!cls.equals(NodePropertiesCacheMap.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        this.ensureDAOsAvailable();

        final NodePropertiesCacheMap properties = (NodePropertiesCacheMap) obj;

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();
            this.writePropertiesRawSerialForm(properties, rawWriter);
        }
        else
        {
            this.writePropertiesRegularSerialForm(properties, writer);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(NodePropertiesCacheMap.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        this.ensureDAOsAvailable();

        final NodePropertiesCacheMap properties = (NodePropertiesCacheMap) obj;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();
            this.readPropertiesRawSerialForm(properties, rawReader);
        }
        else
        {
            this.readPropertiesRegularSerialForm(properties, reader);
        }
    }

    protected void writePropertiesRawSerialForm(final NodePropertiesCacheMap properties, final BinaryRawWriter rawWriter)
    {
        final int size = properties.size();
        rawWriter.writeInt(size);

        for (final Entry<QName, Serializable> entry : properties.entrySet())
        {
            final QName key = entry.getKey();
            Long keyId = null;
            final Serializable value = entry.getValue();
            long[] valueIds = null;

            byte flags = 0;

            if (value instanceof List<?>)
            {
                flags |= FLAG_MULTI_VALUED;
            }
            else if (value == null)
            {
                flags |= FLAG_NULL;
            }

            if (this.useIdsWhenReasonable)
            {
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(key);
                // technically may be null, but practically guaranteed to always be valid
                if (qnamePair == null)
                {
                    throw new AlfrescoRuntimeException("Cannot resolve " + key + " to DB ID");
                }
                keyId = qnamePair.getFirst();

                if (this.useIdsWhenPossible)
                {
                    if (value instanceof ContentDataWithId)
                    {
                        valueIds = new long[] { ((ContentDataWithId) value).getId() };
                    }
                    else if (value instanceof List<?>)
                    {
                        final long[] ids = new long[((List<?>) value).size()];
                        int idx = 0;
                        boolean allIds = !((List<?>) value).isEmpty();
                        for (final Object element : (List<?>) value)
                        {
                            if (element instanceof ContentDataWithId)
                            {
                                ids[idx++] = ((ContentDataWithId) element).getId();
                            }
                            else
                            {
                                allIds = false;
                            }
                        }

                        if (allIds)
                        {
                            valueIds = ids;
                        }
                    }
                }
            }

            if (keyId != null)
            {
                flags |= FLAG_QNAME_ID;
            }
            if (valueIds != null)
            {
                flags |= FLAG_CONTENT_DATA_VALUE_ID;
            }

            rawWriter.writeByte(flags);
            if (keyId != null)
            {
                rawWriter.writeLong(keyId);
            }
            else
            {
                rawWriter.writeObject(key);
            }

            if (valueIds != null)
            {
                if ((flags & FLAG_MULTI_VALUED) == FLAG_MULTI_VALUED)
                {
                    rawWriter.writeLongArray(valueIds);
                }
                else
                {
                    rawWriter.writeLong(valueIds[0]);
                }
            }
            else if (value != null)
            {
                this.writeValueRawSerialForm(value, rawWriter);
            }
        }
    }

    /**
     * Writes out property values in raw serial form. This operation tries to optimise any type of value that Alfresco supports in the out
     * of the box dictionary model, apart from generic or complex types, which should be handled by serializers for their specific types if
     * needed. THe aim of this operation is to optimise storage footprint for 80-90% of expected property values.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the raw binary writer to use
     */
    protected void writeValueRawSerialForm(final Object value, final BinaryRawWriter rawWriter)
    {
        if (value instanceof List<?>)
        {
            rawWriter.writeByte(TYPE_LIST);
            final List<?> list = (List<?>) value;
            rawWriter.writeInt(list.size());
            for (final Object element : list)
            {
                this.writeValueRawSerialForm(element, rawWriter);
            }
        }
        else if (value instanceof Boolean)
        {
            rawWriter.writeByte(TYPE_BOOLEAN);
            rawWriter.writeBoolean(Boolean.TRUE.equals(value));
        }
        else if (value instanceof Integer)
        {
            rawWriter.writeByte(TYPE_INTEGER);
            rawWriter.writeInt((Integer) value);
        }
        else if (value instanceof Long)
        {
            rawWriter.writeByte(TYPE_LONG);
            rawWriter.writeLong((Long) value);
        }
        else if (value instanceof Float)
        {
            rawWriter.writeByte(TYPE_FLOAT);
            rawWriter.writeFloat((Float) value);
        }
        else if (value instanceof Double)
        {
            rawWriter.writeByte(TYPE_DOUBLE);
            rawWriter.writeDouble((Double) value);
        }
        else if (value instanceof String)
        {
            rawWriter.writeByte(TYPE_STRING);
            rawWriter.writeString((String) value);
        }
        else if (value instanceof Date)
        {
            rawWriter.writeByte(TYPE_DATE);
            rawWriter.writeDate((Date) value);
        }
        // TODO Support Locale (d:locale) via ID resolution
        else if (value != null)
        {
            rawWriter.writeByte(TYPE_DEFAULT);
            rawWriter.writeObject(value);
        }
        else
        {
            rawWriter.writeByte(TYPE_NULL);
        }
    }

    protected void readPropertiesRawSerialForm(final NodePropertiesCacheMap properties, final BinaryRawReader rawReader)
            throws BinaryObjectException
    {
        final int size = rawReader.readInt();

        for (int idx = 0; idx < size; idx++)
        {
            final byte flags = rawReader.readByte();

            if (!this.useIdsWhenReasonable && (flags & FLAG_QNAME_ID) == FLAG_QNAME_ID)
            {
                throw new BinaryObjectException("Serializer is not configured to use IDs in place of QName keys");
            }
            if (!this.useIdsWhenPossible && (flags & FLAG_CONTENT_DATA_VALUE_ID) == FLAG_CONTENT_DATA_VALUE_ID)
            {
                throw new BinaryObjectException("Serializer is not configured to use IDs in place of ContentData values");
            }

            final QName key;
            if ((flags & FLAG_QNAME_ID) == FLAG_QNAME_ID)
            {
                final long id = rawReader.readLong();
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(id);
                if (qnamePair == null)
                {
                    throw new BinaryObjectException("Cannot resolve QName for ID " + id);
                }
                key = qnamePair.getSecond();
            }
            else
            {
                key = rawReader.readObject();
            }

            if ((flags & FLAG_NULL) == 0)
            {
                Serializable value;
                if ((flags & FLAG_MULTI_VALUED) == FLAG_MULTI_VALUED)
                {
                    if ((flags & FLAG_CONTENT_DATA_VALUE_ID) == FLAG_CONTENT_DATA_VALUE_ID)
                    {
                        final long[] ids = rawReader.readLongArray();
                        if (ids != null)
                        {
                            final ContentData[] cds = new ContentData[ids.length];
                            idx = 0;

                            for (final long id : ids)
                            {
                                final Pair<Long, ContentData> contentDataPair = this.contentDataDAO.getContentData(id);
                                if (contentDataPair == null)
                                {
                                    throw new BinaryObjectException("Cannot resolve ContentData for ID " + id);
                                }
                                cds[idx++] = contentDataPair.getSecond();
                            }

                            value = new ArrayList<>(Arrays.asList(cds));
                        }
                        // else should never occur, but technically can
                        else
                        {
                            value = new ArrayList<>();
                        }
                    }
                    else
                    {
                        value = this.readValueRawSerialForm(rawReader);
                    }
                }
                else
                {
                    if ((flags & FLAG_CONTENT_DATA_VALUE_ID) == FLAG_CONTENT_DATA_VALUE_ID)
                    {
                        final long id = rawReader.readLong();
                        final Pair<Long, ContentData> contentDataPair = this.contentDataDAO.getContentData(id);
                        if (contentDataPair == null)
                        {
                            throw new BinaryObjectException("Cannot resolve ContentData for ID " + id);
                        }
                        value = contentDataPair.getSecond();
                    }
                    else
                    {
                        value = this.readValueRawSerialForm(rawReader);
                    }
                }

                properties.put(key, value);
            }
            else
            {
                properties.put(key, null);
            }
        }
    }

    protected Serializable readValueRawSerialForm(final BinaryRawReader rawReader) throws BinaryObjectException
    {
        Serializable result;

        final byte type = rawReader.readByte();

        switch (type)
        {
            case TYPE_LIST:
                final int size = rawReader.readInt();
                final ArrayList<Serializable> list = new ArrayList<>(size);
                for (int idx = 0; idx < size; idx++)
                {
                    list.add(this.readValueRawSerialForm(rawReader));
                }
                result = list;
                break;
            case TYPE_BOOLEAN:
                result = rawReader.readBoolean();
                break;
            case TYPE_INTEGER:
                result = rawReader.readInt();
                break;
            case TYPE_LONG:
                result = rawReader.readLong();
                break;
            case TYPE_FLOAT:
                result = rawReader.readFloat();
                break;
            case TYPE_DOUBLE:
                result = rawReader.readDouble();
                break;
            case TYPE_STRING:
                result = rawReader.readString();
                break;
            case TYPE_DATE:
                result = rawReader.readDate();
                break;
            case TYPE_DEFAULT:
                result = rawReader.readObject();
                break;
            case TYPE_NULL:
                result = null;
                break;
            default:
                throw new BinaryObjectException("Read unsupported type flag value " + type);
        }

        return result;
    }

    protected void writePropertiesRegularSerialForm(final NodePropertiesCacheMap properties, final BinaryWriter writer)
    {
        if (this.useIdsWhenPossible)
        {
            final Map<Object, Serializable> contentProperties = new HashMap<>(10);
            final Map<Object, Serializable> regularProperties = new HashMap<>(10);

            properties.forEach((qn, v) -> {
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(qn);

                if (v instanceof ContentDataWithId)
                {
                    contentProperties.put(qnamePair != null ? qnamePair.getFirst() : qn, ((ContentDataWithId) v).getId());
                }
                else if (v instanceof List<?>)
                {
                    final Long[] ids = new Long[((List<?>) v).size()];
                    int idx = 0;
                    boolean allContent = true;
                    for (final Object element : (List<?>) v)
                    {
                        if (element instanceof ContentDataWithId)
                        {
                            ids[idx++] = ((ContentDataWithId) element).getId();
                        }
                        else
                        {
                            allContent = false;
                        }
                    }

                    if (allContent)
                    {
                        contentProperties.put(qnamePair != null ? qnamePair.getFirst() : qn, ids);
                    }
                    else
                    {
                        regularProperties.put(qnamePair != null ? qnamePair.getFirst() : qn, v);
                    }
                }
                else
                {
                    regularProperties.put(qnamePair != null ? qnamePair.getFirst() : qn, v);
                }
            });

            writer.writeMap(REGULAR_VALUES, regularProperties);
            writer.writeMap(CONTENT_ID_VALUES, contentProperties);
        }
        else if (this.useIdsWhenReasonable)
        {
            final Map<Serializable, Serializable> mappedProperties = new HashMap<>();
            properties.forEach((qn, v) -> {
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(qn);
                mappedProperties.put(qnamePair != null ? qnamePair.getFirst() : qn, v);
            });

            writer.writeMap(VALUES, mappedProperties);
        }
        else
        {
            // must be wrapped otherwise it would be written as self-referential handle
            // effectively preventing ANY values from being written
            writer.writeMap(VALUES, Collections.unmodifiableMap(properties));
        }
    }

    protected void readPropertiesRegularSerialForm(final NodePropertiesCacheMap properties, final BinaryReader reader)
    {
        final Function<Entry<Object, Serializable>, QName> resolveQName = entry -> {
            final Object key = entry.getKey();
            QName qn;
            if (key instanceof Long)
            {
                if (!this.useIdsWhenReasonable)
                {
                    throw new BinaryObjectException("Serializer is not configured to use IDs in place of QName keys");
                }

                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName((Long) key);
                if (qnamePair == null)
                {
                    throw new BinaryObjectException("Cannot resolve QName for ID " + key);
                }
                qn = qnamePair.getSecond();
            }
            else
            {
                qn = (QName) key;
            }
            return qn;
        };

        if (this.useIdsWhenPossible)
        {
            final Map<Object, Serializable> regularProperties = reader.readMap(REGULAR_VALUES);

            for (final Entry<Object, Serializable> regularEntry : regularProperties.entrySet())
            {
                properties.put(resolveQName.apply(regularEntry), regularEntry.getValue());
            }

            final Map<Object, Serializable> contentProperties = reader.readMap(CONTENT_ID_VALUES);
            for (final Entry<Object, Serializable> contentEntry : contentProperties.entrySet())
            {
                final QName qn = resolveQName.apply(contentEntry);

                final Serializable value = contentEntry.getValue();
                if (value instanceof Long)
                {
                    final Pair<Long, ContentData> contentDataPair = this.contentDataDAO.getContentData((Long) value);
                    if (contentDataPair == null)
                    {
                        throw new BinaryObjectException("Cannot resolve ContentData for ID " + value);
                    }
                    properties.put(qn, contentDataPair.getSecond());
                }
                else if (value instanceof Long[])
                {
                    final ArrayList<ContentData> cds = new ArrayList<>();
                    for (final Long id : (Long[]) value)
                    {
                        final Pair<Long, ContentData> contentDataPair = this.contentDataDAO.getContentData(id);
                        if (contentDataPair == null)
                        {
                            throw new BinaryObjectException("Cannot resolve ContentData for ID " + id);
                        }
                        cds.add(contentDataPair.getSecond());
                    }
                    properties.put(qn, cds);
                }
                else
                {
                    throw new BinaryObjectException("Unsupported value type for content property");
                }
            }
        }
        else
        {
            final Map<Object, Serializable> values = reader.readMap(VALUES);
            values.entrySet().forEach(entry -> {
                final QName qn = resolveQName.apply(entry);
                properties.put(qn, entry.getValue());
            });
        }
    }

    protected void ensureDAOsAvailable() throws BinaryObjectException
    {
        if (this.useIdsWhenReasonable || this.useIdsWhenPossible)
        {
            if (this.qnameDAO == null)
            {
                try
                {
                    this.qnameDAO = this.applicationContext.getBean("qnameDAO", QNameDAO.class);
                }
                catch (final BeansException be)
                {
                    throw new BinaryObjectException(
                            "Cannot (de-)serialise node properties in current configuration without access to QNameDAO", be);
                }
            }

            if (this.useIdsWhenPossible || this.contentDataDAO == null)
            {
                try
                {
                    this.contentDataDAO = this.applicationContext.getBean("contentDataDAO", ContentDataDAO.class);
                }
                catch (final BeansException be)
                {
                    throw new BinaryObjectException(
                            "Cannot (de-)serialise node properties in current configuration without access to ContentDataDAO", be);
                }
            }
        }
    }
}
