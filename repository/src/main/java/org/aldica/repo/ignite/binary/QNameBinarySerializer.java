/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.lang.reflect.Field;

import org.alfresco.service.namespace.QName;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link QName qualified name} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise handling of well-known {@link QName#getNamespaceURI() namespace URIs} as part of the qualifed
 * name.
 *
 * @author Axel Faust
 */
public class QNameBinarySerializer implements BinarySerializer
{

    private static final String NAMESPACE_TYPE = "namespaceType";

    private static final String NAMESPACE_URI = "namespaceURI";

    private static final String LOCAL_NAME = "localName";

    private static final Field NAMESPACE_URI_FIELD;

    private static final Field LOCAL_NAME_FIELD;

    static
    {
        try
        {
            NAMESPACE_URI_FIELD = QName.class.getDeclaredField("namespaceURI");
            LOCAL_NAME_FIELD = QName.class.getDeclaredField("localName");

            NAMESPACE_URI_FIELD.setAccessible(true);
            LOCAL_NAME_FIELD.setAccessible(true);
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
        if (!cls.equals(QName.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final QName qname = (QName) obj;

        final String namespaceURI = qname.getNamespaceURI();
        final String localName = qname.getLocalName();

        final Namespace namespace = Namespace.getLiteral(namespaceURI);

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();
            rawWriter.writeByte((byte) namespace.ordinal());
            if (namespace == Namespace.CUSTOM)
            {
                rawWriter.writeString(namespaceURI);
            }
            rawWriter.writeString(localName);
        }
        else
        {
            writer.writeByte(NAMESPACE_TYPE, (byte) namespace.ordinal());
            if (namespace == Namespace.CUSTOM)
            {
                writer.writeString(NAMESPACE_URI, namespaceURI);
            }
            writer.writeString(LOCAL_NAME, localName);
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
        if (!cls.equals(QName.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        String namespaceUri;
        String localName;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();
            final byte namespaceType = rawReader.readByte();
            final Namespace namespace = Namespace.values()[namespaceType];
            if (namespace == Namespace.CUSTOM)
            {
                namespaceUri = rawReader.readString();
            }
            else
            {
                namespaceUri = namespace.getUri();
            }
            localName = rawReader.readString();
        }
        else
        {
            final byte namespaceType = reader.readByte(NAMESPACE_TYPE);
            final Namespace namespace = Namespace.values()[namespaceType];
            if (namespace == Namespace.CUSTOM)
            {
                namespaceUri = reader.readString(NAMESPACE_URI);
            }
            else
            {
                namespaceUri = namespace.getUri();
            }
            localName = reader.readString(LOCAL_NAME);
        }

        try
        {
            NAMESPACE_URI_FIELD.set(obj, namespaceUri);
            LOCAL_NAME_FIELD.set(obj, localName);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

}
