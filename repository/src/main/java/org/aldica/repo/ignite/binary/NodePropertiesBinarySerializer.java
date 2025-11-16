/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.aldica.repo.ignite.cache.NodePropertiesCacheMap;
import org.alfresco.repo.domain.contentdata.ContentDataDAO;
import org.alfresco.repo.domain.node.ContentDataWithId;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.repo.tenant.TenantService;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.Path;
import org.alfresco.service.cmr.repository.Path.AttributeElement;
import org.alfresco.service.cmr.repository.Path.ChildAssocElement;
import org.alfresco.service.cmr.repository.Path.DescendentOrSelfElement;
import org.alfresco.service.cmr.repository.Path.Element;
import org.alfresco.service.cmr.repository.Path.ParentElement;
import org.alfresco.service.cmr.repository.Path.SelfElement;
import org.alfresco.service.cmr.repository.Period;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.util.VersionNumber;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
import org.springframework.beans.BeansException;
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
public class NodePropertiesBinarySerializer extends AbstractContentSupportBinarySerializer<NodePropertiesCacheMap>
        implements ApplicationContextAware
{

    private static class SimplePathElement extends Element
    {

        private static final long serialVersionUID = 6114652324636942668L;

        private final String elementString;

        public SimplePathElement(final String elementString)
        {
            this.elementString = elementString;
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public String getElementString()
        {
            return this.elementString;
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public Element getBaseNameElement(final TenantService tenantService)
        {
            return new SimplePathElement(this.elementString);
        }
    }

    private static final String VALUES = "values";

    private static final String REGULAR_VALUES = "regularValues";

    private static final String CONTENT_ID_VALUES = "contentIdValues";

    private static final byte FLAG_GLOBAL_REASONABLE_ID = 0x01;

    private static final byte FLAG_GLOBAL_POSSIBLE_ID = 0x02;

    private static final byte FLAG_ENTRY_QNAME_ID_UNSIGNED = 0x01;

    @SuppressWarnings("unused")
    private static final byte MASK_ALL_TYPE_FLAGS = -128 | 0x7e;

    private static final byte MASK_CORE_TYPE_FLAGS = 0x3e;

    private static final byte FLAG_MLTEXT_LOCALE_ID_UNSIGNED = 0x01;

    private static final byte FLAG_MLTEXT_LOCALE_ID = 0x02;

    private static final byte FLAG_MLTEXT_STRING_NULL = 0x04;

    // bits 2-6 used for type information -> 32 potential Java types (implicit null)

    private static final byte FLAG_TYPE_LIST = 0x02;

    private static final byte FLAG_TYPE_STRING = 0x04;

    private static final byte FLAG_TYPE_INT = 0x06;

    private static final byte FLAG_TYPE_LONG = 0x08;

    private static final byte FLAG_TYPE_FLOAT = 0x0a;

    private static final byte FLAG_TYPE_DOUBLE = 0x0c;

    private static final byte FLAG_TYPE_DATE = 0x0e;

    private static final byte FLAG_TYPE_BOOLEAN = 0x10;

    private static final byte FLAG_TYPE_QNAME = 0x12;

    private static final byte FLAG_TYPE_NODEREF = 0x14;

    private static final byte FLAG_TYPE_CHILDASSOCREF = 0x16;

    private static final byte FLAG_TYPE_ASSOCREF = 0x18;

    private static final byte FLAG_TYPE_PATH = 0x1a;

    private static final byte FLAG_TYPE_LOCALE = 0x1c;

    private static final byte FLAG_TYPE_VERSION_NUMBER = 0x1e;

    private static final byte FLAG_TYPE_PERIOD = 0x20;

    private static final byte FLAG_TYPE_MLTEXT = 0x22;

    private static final byte FLAG_TYPE_CONTENT = 0x24;

    private static final byte FLAG_TYPE_OBJECT = 0x26;

    // we have room to handle quite a few additional Java value types

    // if value is referenced via a DAO-managed entity
    private static final byte FLAG_TYPE_ID = 0x40;

    // unsigned only used for int/long + Date + IDs
    private static final byte FLAG_TYPE_UNSIGNED = -128;

    private static final byte MASK_PATH_ELEMENT_TYPES = 0x07;

    private static final byte FLAG_PATH_ELEMENT_TYPE_SELF = 0x01;

    private static final byte FLAG_PATH_ELEMENT_TYPE_CHILD = 0x02;

    private static final byte FLAG_PATH_ELEMENT_TYPE_PARENT = 0x03;

    private static final byte FLAG_PATH_ELEMENT_TYPE_ATTRIBUTE = 0x04;

    private static final byte FLAG_PATH_ELEMENT_TYPE_DESCENDANT_OR_SELF = 0x05;

    private static final byte FLAG_PATH_ELEMENT_TYPE_SIMPLE = 0x06;

    protected QNameDAO qnameDAO;

    protected ContentDataDAO contentDataDAO;

    protected boolean useIdsWhenPossible = false;

    public NodePropertiesBinarySerializer()
    {
        super(NodePropertiesCacheMap.class);
    }

    /**
     * @param useIdsWhenPossible
     *     the useIdsWhenPossible to set
     */
    public void setUseIdsWhenPossible(final boolean useIdsWhenPossible)
    {
        this.useIdsWhenPossible = useIdsWhenPossible;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
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

            if (this.useIdsWhenPossible && this.contentDataDAO == null)
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

            super.ensureDAOsAvailable();
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final NodePropertiesCacheMap nodePropertiesCacheMap, final BinaryWriterExImpl rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();

        byte globalFlags = 0;
        if (this.useIdsWhenReasonable)
        {
            globalFlags |= FLAG_GLOBAL_REASONABLE_ID;
        }
        if (this.useIdsWhenPossible)
        {
            globalFlags |= FLAG_GLOBAL_POSSIBLE_ID;
        }

        final int size = nodePropertiesCacheMap.size();

        // assume 2 bytes for size + avg. of 50 bytes per property
        // (hard to efficiently estimate, especially with arbitrarily large textual values)
        // avoid multiple smaller allocations
        out.unsafeEnsure(3 + size * 50);
        out.unsafeWriteByte(globalFlags);
        this.writeUnsigned(size, out);

        for (final Entry<QName, Serializable> entry : nodePropertiesCacheMap.entrySet())
        {
            byte entryFlags = 0;

            final int entryStartPos = out.position();
            out.unsafeEnsure(1);
            out.position(entryStartPos + 1);

            // type/null flags not relevent (type flag is globally set and key is never null)
            entryFlags |= this.writeValueOrId(entry.getKey(), this::qnameLookup, this::writeQName, (byte) 0, (byte) 0,
                    FLAG_ENTRY_QNAME_ID_UNSIGNED, out);
            entryFlags |= this.writeElementValue(entry.getValue(), rawWriter, out);

            final int entryEndPos = out.position();
            out.position(entryStartPos);
            out.unsafeWriteByte(entryFlags);
            out.position(entryEndPos);
        }
    }

    protected byte writeElementValue(final Object value, final BinaryRawWriter rawWriter, final BinaryOutputStream out)
    {
        // implicit NULL is default
        byte retFlags = 0;

        if (value instanceof Integer)
        {
            retFlags = FLAG_TYPE_INT;
            retFlags |= this.writeWithFlagIfUnsigned(((Integer) value).intValue(), FLAG_TYPE_UNSIGNED, out);
        }
        else if (value instanceof Long)
        {
            retFlags = FLAG_TYPE_LONG;
            retFlags |= this.writeWithFlagIfUnsigned(((Long) value).longValue(), FLAG_TYPE_UNSIGNED, out);
        }
        else if (value instanceof Float)
        {
            retFlags = FLAG_TYPE_FLOAT;
            out.writeDouble(((Float) value).floatValue());
        }
        else if (value instanceof Double)
        {
            retFlags = FLAG_TYPE_DOUBLE;
            out.writeDouble(((Double) value).doubleValue());
        }
        else if (value instanceof Date)
        {
            retFlags = FLAG_TYPE_DATE;
            retFlags |= this.writeWithFlagIfUnsigned(((Date) value).getTime(), FLAG_TYPE_UNSIGNED, out);
        }
        else if (value instanceof Boolean)
        {
            retFlags = FLAG_TYPE_BOOLEAN;
            out.writeBoolean(((Boolean) value).booleanValue());
        }
        else if (value instanceof String)
        {
            retFlags = FLAG_TYPE_STRING;
            this.writeString((String) value, out);
        }
        else if (value instanceof NodeRef)
        {
            retFlags = FLAG_TYPE_NODEREF;
            this.writeNodeRef((NodeRef) value, out);
        }
        else if (value instanceof QName)
        {
            retFlags = FLAG_TYPE_QNAME;
            // values likely way too diverse and not guaranteed at all to be in alf_qname
            this.writeQName((QName) value, out);
        }
        else if (value instanceof Locale)
        {
            retFlags = FLAG_TYPE_LOCALE;
            // locales don't have a too wide range of values - possibly stored in alf_locale
            retFlags |= this.writeValueOrId((Locale) value, this::possibleLocaleLookup, (byte) 0, FLAG_TYPE_ID, FLAG_TYPE_UNSIGNED, out);
        }
        else if (value instanceof ChildAssociationRef)
        {
            retFlags = FLAG_TYPE_CHILDASSOCREF;
            final ChildAssociationRef childAssoc = (ChildAssociationRef) value;
            // child assoc type names reasonably expected to be in alf_qname
            this.writeChildAssociationRef(childAssoc, this::qnameLookup, out);
        }
        else if (value instanceof AssociationRef)
        {
            retFlags = FLAG_TYPE_ASSOCREF;
            final AssociationRef assoc = (AssociationRef) value;
            // assoc type names reasonably expected to be in alf_qname
            this.writeAssociationRef(assoc, this::qnameLookup, out);
        }
        else if (value instanceof Period)
        {
            retFlags = FLAG_TYPE_PERIOD;
            this.writeValueAsString(value, out);
        }
        else if (value instanceof VersionNumber)
        {
            retFlags = FLAG_TYPE_VERSION_NUMBER;
            this.writeValueAsString(value, out);
        }
        else if (value instanceof Path)
        {
            retFlags = FLAG_TYPE_PATH;
            this.writePath((Path) value, out);
        }
        else if (value instanceof MLText)
        {
            retFlags = FLAG_TYPE_MLTEXT;
            this.writeMlText((MLText) value, out);
        }
        else if (value instanceof ContentData)
        {
            retFlags = FLAG_TYPE_CONTENT;
            retFlags |= this.writeValueOrId((ContentData) value, this::contentLookup, this::writeContentData, (byte) 0, FLAG_TYPE_ID,
                    FLAG_TYPE_UNSIGNED, out);
        }
        else if (value instanceof List<?>)
        {
            retFlags = FLAG_TYPE_LIST;
            this.writeList((List<?>) value, rawWriter, out);
        }
        else if (value != null)
        {
            retFlags = FLAG_TYPE_OBJECT;
            rawWriter.writeObject(value);
        }

        return retFlags;
    }

    protected void writePath(final Path path, final BinaryOutputStream out)
    {
        final int size = path.size();
        this.writeUnsigned(size, out);

        for (int i = 0; i < size; i++)
        {
            final Element element = path.get(i);

            byte typeFlag;

            final int startPos = out.position();
            out.unsafeEnsure(1);
            out.position(startPos + 1);

            if (element instanceof SelfElement)
            {
                typeFlag = FLAG_PATH_ELEMENT_TYPE_SELF;
            }
            else if (element instanceof ParentElement)
            {
                typeFlag = FLAG_PATH_ELEMENT_TYPE_PARENT;
            }
            else if (element instanceof ChildAssocElement)
            {
                typeFlag = FLAG_PATH_ELEMENT_TYPE_CHILD;
                // child assoc type names reasonably expected to be in alf_qname
                this.writeChildAssociationRef(((ChildAssocElement) element).getRef(), this::qnameLookup, out);
            }
            else if (element instanceof DescendentOrSelfElement)
            {
                typeFlag = FLAG_PATH_ELEMENT_TYPE_DESCENDANT_OR_SELF;
            }
            else if (element instanceof AttributeElement)
            {
                typeFlag = FLAG_PATH_ELEMENT_TYPE_ATTRIBUTE;
                final AttributeElement attributeElement = (AttributeElement) element;
                this.writeQName(attributeElement.getQName(), out);
                typeFlag |= this.writeWithFlagIfUnsigned(attributeElement.position(), FLAG_TYPE_UNSIGNED, out);
            }
            else
            {
                typeFlag = FLAG_PATH_ELEMENT_TYPE_SIMPLE;
                this.writeString(element.getElementString(), out);
            }

            final int endPos = out.position();
            out.position(startPos);
            out.unsafeWriteByte(typeFlag);
            out.position(endPos);
        }
    }

    protected void writeMlText(final MLText mlText, final BinaryOutputStream out)
    {
        final int size = mlText.size();
        this.writeUnsigned(size, out);

        for (final Entry<Locale, String> entry : mlText.entrySet())
        {
            final int elementStartIdx = out.position();
            out.unsafeEnsure(1);
            out.position(elementStartIdx + 1);

            byte flags = this.writeValueOrId(entry.getKey(), this::localeLookup, (byte) 0, FLAG_MLTEXT_LOCALE_ID,
                    FLAG_MLTEXT_LOCALE_ID_UNSIGNED, out);
            String str = entry.getValue();
            if (str != null)
            {
                this.writeString(str, out);
            }
            else
            {
                flags |= FLAG_MLTEXT_STRING_NULL;
            }

            final int elementEndIdx = out.position();
            out.position(elementStartIdx);
            out.unsafeWriteByte(flags);
            out.position(elementEndIdx);
        }
    }

    protected void writeList(final List<?> list, final BinaryRawWriter rawWriter, final BinaryOutputStream out)
    {
        final int size = list.size();
        this.writeUnsigned(size, out);

        for (final Object element : list)
        {
            final int elementStartIdx = out.position();
            out.unsafeEnsure(1);
            out.position(elementStartIdx + 1);

            final byte typeFlags = this.writeElementValue(element, rawWriter, out);

            final int elementEndIdx = out.position();
            out.position(elementStartIdx);
            out.unsafeWriteByte(typeFlags);
            out.position(elementEndIdx);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final NodePropertiesCacheMap nodePropertiesCacheMap, final BinaryWriter writer)
    {
        // TODO granular serializers for ChildAssociationRef/AssociationRef
        if (this.useIdsWhenPossible)
        {
            final Map<Object, Serializable> contentProperties = new HashMap<>(10);
            final Map<Object, Serializable> regularProperties = new HashMap<>(10);

            nodePropertiesCacheMap.forEach((qn, v) -> {
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(qn);

                final Object effectiveQn = qnamePair != null ? qnamePair.getFirst() : qn;
                if (v instanceof ContentDataWithId)
                {
                    contentProperties.put(effectiveQn, ((ContentDataWithId) v).getId());
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
                        contentProperties.put(effectiveQn, ids);
                    }
                    else
                    {
                        regularProperties.put(effectiveQn, v);
                    }
                }
                else
                {
                    regularProperties.put(effectiveQn, v);
                }
            });

            writer.writeMap(REGULAR_VALUES, regularProperties);
            writer.writeMap(CONTENT_ID_VALUES, contentProperties);
        }
        else if (this.useIdsWhenReasonable)
        {
            final Map<Serializable, Serializable> mappedProperties = new HashMap<>();
            nodePropertiesCacheMap.forEach((qn, v) -> {
                final Pair<Long, QName> qnamePair = this.qnameDAO.getQName(qn);
                mappedProperties.put(qnamePair != null ? qnamePair.getFirst() : qn, v);
            });

            writer.writeMap(VALUES, mappedProperties);
        }
        else
        {
            // must be wrapped otherwise it would be written as self-referential handle
            // effectively preventing ANY values from being written
            writer.writeMap(VALUES, Collections.unmodifiableMap(nodePropertiesCacheMap));
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final NodePropertiesCacheMap nodePropertiesCacheMap, final BinaryRawReader rawReader)
    {
        final byte globalFlags = rawReader.readByte();

        if ((globalFlags & FLAG_GLOBAL_POSSIBLE_ID) != 0 && !this.useIdsWhenPossible)
        {
            throw new BinaryObjectException("Serializer is not configured to use IDs in place of ContentData values");
        }

        final boolean reasonableIdsPresent = (globalFlags & FLAG_GLOBAL_REASONABLE_ID) != 0;
        if (reasonableIdsPresent && !this.useIdsWhenReasonable)
        {
            throw new BinaryObjectException("Serializer is not configured to use IDs in place of QName keys or various value types");
        }

        final int size = this.readUnsignedInt(rawReader);

        for (int i = 0; i < size; i++)
        {
            final byte entryFlags = rawReader.readByte();

            final QName qname = this.doReadValueOrId(rawReader, this::qnameLookup, this::readQName,
                    (byte) (reasonableIdsPresent ? entryFlags | FLAG_TYPE_ID : entryFlags), (byte) 0, FLAG_TYPE_ID,
                    FLAG_ENTRY_QNAME_ID_UNSIGNED);

            final Serializable value = this.readElementValue(rawReader, entryFlags);
            nodePropertiesCacheMap.put(qname, value);
        }
    }

    protected Serializable readElementValue(final BinaryRawReader rawReader, final byte flags)
    {
        // since we have implicit NULL we can safely use 0x0 as nullFlag in calls to doReadValueOrId
        // that method does not have to handle null, which is already handled in our case-switch
        Serializable value;

        switch ((flags & MASK_CORE_TYPE_FLAGS))
        {
            case FLAG_TYPE_INT:
                value = Integer.valueOf(this.readInt(rawReader, (flags & FLAG_TYPE_UNSIGNED) == FLAG_TYPE_UNSIGNED));
                break;
            case FLAG_TYPE_LONG:
                value = Long.valueOf(this.readLong(rawReader, (flags & FLAG_TYPE_UNSIGNED) == FLAG_TYPE_UNSIGNED));
                break;
            case FLAG_TYPE_FLOAT:
                value = Float.valueOf(rawReader.readFloat());
                break;
            case FLAG_TYPE_DOUBLE:
                value = Double.valueOf(rawReader.readDouble());
                break;
            case FLAG_TYPE_DATE:
                value = new Date(this.readLong(rawReader, (flags & FLAG_TYPE_UNSIGNED) == FLAG_TYPE_UNSIGNED));
                break;
            case FLAG_TYPE_BOOLEAN:
                value = Boolean.valueOf(rawReader.readBoolean());
                break;
            case FLAG_TYPE_STRING:
                value = this.readString(rawReader);
                break;
            case FLAG_TYPE_NODEREF:
                value = this.readNodeRef(rawReader);
                break;
            case FLAG_TYPE_QNAME:
                value = this.doReadValueOrId(rawReader, this::qnameLookup, this::readQName, flags, (byte) 0, FLAG_TYPE_ID,
                        FLAG_TYPE_UNSIGNED);
                break;
            case FLAG_TYPE_LOCALE:
                value = this.readValue(rawReader, this::convertToLocale);
                break;
            case FLAG_TYPE_CHILDASSOCREF:
                value = this.readChildAssociationRef(rawReader, this::qnameLookup);
                break;
            case FLAG_TYPE_ASSOCREF:
                value = this.readAssociationRef(rawReader, this::qnameLookup);
                break;
            case FLAG_TYPE_PERIOD:
                value = new Period(this.readString(rawReader));
                break;
            case FLAG_TYPE_VERSION_NUMBER:
                value = new VersionNumber(this.readString(rawReader));
                break;
            case FLAG_TYPE_PATH:
                value = this.readPath(rawReader);
                break;
            case FLAG_TYPE_MLTEXT:
                value = this.readMlText(rawReader);
                break;
            case FLAG_TYPE_CONTENT:
                value = this.doReadValueOrId(rawReader, this::contentLookup, this::readContentData, flags, (byte) 0, FLAG_TYPE_ID,
                        FLAG_TYPE_UNSIGNED);
                break;
            case FLAG_TYPE_LIST:
                value = this.readList(rawReader);
                break;
            case FLAG_TYPE_OBJECT:
                value = rawReader.readObject();
                break;
            default:
                value = null;
        }

        return value;
    }

    protected Path readPath(final BinaryRawReader rawReader)
    {
        final int size = this.readUnsignedInt(rawReader);

        final Path path = new Path();
        for (int i = 0; i < size; i++)
        {
            final byte typeFlag = rawReader.readByte();

            Element el = null;
            switch (typeFlag & MASK_PATH_ELEMENT_TYPES)
            {
                case FLAG_PATH_ELEMENT_TYPE_PARENT:
                    el = new Path.ParentElement();
                    break;
                case FLAG_PATH_ELEMENT_TYPE_DESCENDANT_OR_SELF:
                    el = new Path.DescendentOrSelfElement();
                    break;
                case FLAG_PATH_ELEMENT_TYPE_SELF:
                    el = new Path.SelfElement();
                    break;
                case FLAG_PATH_ELEMENT_TYPE_ATTRIBUTE:
                    final QName attr = this.readQName(rawReader);
                    final int pos = this.readInt(rawReader, (typeFlag & FLAG_TYPE_UNSIGNED) == FLAG_TYPE_UNSIGNED);
                    el = new Path.AttributeElement(attr, pos);
                    break;
                case FLAG_PATH_ELEMENT_TYPE_CHILD:
                    final ChildAssociationRef childRef = this.readChildAssociationRef(rawReader, this::qnameLookup);
                    el = new Path.ChildAssocElement(childRef);
                    break;
                default:
                    final String elementString = this.readString(rawReader);
                    el = new SimplePathElement(elementString);
            }

            if (el != null)
            {
                path.append(el);
            }
        }

        return path;
    }

    protected MLText readMlText(final BinaryRawReader rawReader)
    {
        final int size = this.readUnsignedInt(rawReader);
        final MLText mlText = new MLText();

        for (int i = 0; i < size; i++)
        {
            final byte flags = rawReader.readByte();

            final Locale locale = this.readValueOrId(rawReader, this::localeLookup, this::convertToLocale, flags, (byte) 0,
                    FLAG_MLTEXT_LOCALE_ID, FLAG_MLTEXT_LOCALE_ID_UNSIGNED);
            final String text;
            if ((flags & FLAG_MLTEXT_STRING_NULL) == 0)
            {
                text = this.readString(rawReader);
            }
            else
            {
                text = null;
            }

            mlText.addValue(locale, text);
        }

        return mlText;
    }

    protected ArrayList<Serializable> readList(final BinaryRawReader rawReader)
    {
        final int size = this.readUnsignedInt(rawReader);

        final ArrayList<Serializable> list = new ArrayList<>(size);

        for (int i = 0; i < size; i++)
        {
            final byte flags = rawReader.readByte();
            final Serializable element = this.readElementValue(rawReader, flags);
            list.add(element);
        }

        return list;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final NodePropertiesCacheMap nodePropertiesCacheMap, final BinaryReader reader)
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
                nodePropertiesCacheMap.put(resolveQName.apply(regularEntry), regularEntry.getValue());
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
                    nodePropertiesCacheMap.put(qn, contentDataPair.getSecond());
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
                    nodePropertiesCacheMap.put(qn, cds);
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
                nodePropertiesCacheMap.put(qn, entry.getValue());
            });
        }
    }

    protected Pair<Long, QName> qnameLookup(final QName qname)
    {
        Pair<Long, QName> result = null;
        if (this.useIdsWhenReasonable)
        {
            result = this.doLookup(LOOKUP_BUCKET_QNAME, qname, this.qnameDAO::getQName);
        }
        return result;
    }

    protected Pair<Long, QName> qnameLookup(final Long id)
    {
        Pair<Long, QName> result = null;
        if (this.useIdsWhenReasonable)
        {
            result = this.doLookup(LOOKUP_BUCKET_QNAME, id, this.qnameDAO::getQName);
        }
        return result;
    }

    protected Pair<Long, Locale> possibleLocaleLookup(final Locale locale)
    {
        Pair<Long, Locale> result = null;
        if (this.useIdsWhenPossible)
        {
            result = this.doLookup(LOOKUP_BUCKET_LOCALE, locale, this.localeDAO::getLocalePair);
        }
        return result;
    }

    protected Pair<Long, ContentData> contentLookup(final ContentData contentData)
    {
        if (this.useIdsWhenPossible && contentData instanceof ContentDataWithId)
        {
            return new Pair<>(((ContentDataWithId) contentData).getId(), contentData);
        }
        return null;
    }

    protected Pair<Long, ContentData> contentLookup(final Long id)
    {
        // no need to use doLookup because content data is not typically reused in different nodes/properties
        return this.useIdsWhenPossible ? this.contentDataDAO.getContentData(id) : null;
    }
}
