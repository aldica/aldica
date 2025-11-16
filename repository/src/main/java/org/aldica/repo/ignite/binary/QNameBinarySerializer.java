/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import org.alfresco.service.namespace.QName;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.util.GridUnsafe;

/**
 * Instances of this class handle (de-)serialisations of {@link QName qualified name} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise handling of well-known {@link QName#getNamespaceURI() namespace URIs} as part of the qualifed
 * name.
 *
 * @author Axel Faust
 */
public class QNameBinarySerializer extends AbstractExtendedBinarySerializer<QName>
{

    private static final String NAMESPACE_TYPE = "namespaceType";

    private static final String NAMESPACE_URI = "namespaceURI";

    private static final String LOCAL_NAME = "localName";

    private static final long QNAME_NAMESPACE_URI_FIELD_OFFSET = getFieldOffset(QName.class, "namespaceURI", String.class);

    private static final long QNAME_LOCAL_NAME_FIELD_OFFSET = getFieldOffset(QName.class, "localName", String.class);

    public QNameBinarySerializer()
    {
        super(QName.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final QName qname, final BinaryWriterExImpl rawWriter)
    {
        this.writeQName(qname, rawWriter.out());
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final QName qname, final BinaryWriter writer)
    {
        final String namespaceURI = qname.getNamespaceURI();
        final String localName = qname.getLocalName();
        final Namespace literal = Namespace.getLiteral(namespaceURI);

        if (literal != null)
        {
            writer.writeByte(NAMESPACE_TYPE, (byte) literal.ordinal());
        }
        else
        {
            writer.writeString(NAMESPACE_URI, namespaceURI);
        }
        writer.writeString(LOCAL_NAME, localName);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final QName qname, final BinaryRawReader rawReader) throws BinaryObjectException
    {
        this.readQName(qname, rawReader);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final QName qname, final BinaryReader reader) throws BinaryObjectException
    {
        String namespaceUri;
        String localName;

        namespaceUri = reader.readString(NAMESPACE_URI);
        if (namespaceUri == null)
        {
            final Namespace namespace = Namespace.values()[reader.readByte(NAMESPACE_TYPE)];
            namespaceUri = namespace.getUri();
        }
        localName = reader.readString(LOCAL_NAME);

        GridUnsafe.putObjectField(qname, QNAME_NAMESPACE_URI_FIELD_OFFSET, namespaceUri);
        GridUnsafe.putObjectField(qname, QNAME_LOCAL_NAME_FIELD_OFFSET, localName);
    }
}
