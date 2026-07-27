/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.function.Function;

import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
import org.apache.ignite.internal.util.GridUnsafe;

/**
 * Instances of this class provide for the (de-)serialisation of specific value objects. In this class, patterns related to the raw
 * serialisation of common Alfresco value types have been aggregated which may be re-used across different top-level types of serialised
 * objects.
 *
 * @author Axel Faust
 */
public abstract class AbstractExtendedBinarySerializer<T> extends AbstractBinarySerializer<T>
{

    protected static final int LOOKUP_BUCKET_QNAME = 0;

    protected static final int LOOKUP_BUCKET_ENCODING = 1;

    protected static final int LOOKUP_BUCKET_LOCALE = 2;

    protected static final int LOOKUP_BUCKET_MIMETYPE = 3;

    protected static final byte MASK_STORE_REF_PROTO = 0x03;

    protected static final byte FLAG_STORE_REF_PROTO_WORKSPACE = 0x00;

    protected static final byte FLAG_STORE_REF_PROTO_ARCHIVE = 0x01;

    protected static final byte FLAG_STORE_REF_PROTO_USER = 0x02;

    protected static final byte FLAG_STORE_REF_PROTO_SYSTEM = 0x03;

    protected static final byte MASK_STORE_REF_ID = 0x0c;

    protected static final byte FLAG_STORE_REF_ID_SPACESSTORE = 0x00;

    // we do not handle lightWeightVersionStore - it's unused in any modern ACS instance
    protected static final byte FLAG_STORE_REF_ID_VERSION2STORE = 0x04;

    protected static final byte FLAG_STORE_REF_ID_ALFRESCOUSERSTORE = 0x08;

    protected static final byte FLAG_STORE_REF_ID_SYSTEM = 0x0c;

    protected static final byte MASK_STORE_REF_CUSTOM_FLAGS = 0x30;

    protected static final byte FLAG_STORE_REF_PROTO_CUSTOM = 0x10;

    protected static final byte FLAG_STORE_REF_ID_CUSTOM = 0x20;

    protected static final byte MASK_QNAME_DEFAULT_NAMESPACES = 0x7f;

    protected static final byte FLAG_QNAME_CUSTOM_NAMESPACE = -128;

    protected static final byte FLAG_CHILD_REF_PARENT_IS_ROOT = 0x01;

    protected static final byte FLAG_CHILD_REF_TYPE_QNAME_ID_UNSIGNED = 0x02;

    protected static final byte FLAG_CHILD_REF_TYPE_QNAME_ID = 0x04;

    protected static final byte FLAG_CHILD_REF_PRIMARY = 0x08;

    protected static final byte FLAG_CHILD_REF_TYPE_SIBLING = 0x10;

    protected static final byte FLAG_ASSOC_REF_TYPE_QNAME_ID_UNSIGNED = 0x01;

    protected static final byte FLAG_ASSOC_REF_TYPE_QNAME_ID = 0x02;

    protected static final byte FLAG_ASSOC_REF_ID_UNSIGNED = 0x04;

    protected static final byte FLAG_ASSOC_REF_ID = 0x08;

    protected static final String[] STORE_REF_PROTOCOLS = { StoreRef.PROTOCOL_WORKSPACE, StoreRef.PROTOCOL_ARCHIVE, "user", "system" };

    protected static final String[] STORE_REF_IDS = { "SpacesStore", "version2Store", "alfrescoUserStore", "system" };

    protected static final StoreRef[] STORE_REF_CONSTANTS;

    static
    {
        STORE_REF_CONSTANTS = new StoreRef[(MASK_STORE_REF_PROTO | MASK_STORE_REF_ID) + 1];

        final byte[] protoFlags = { FLAG_STORE_REF_PROTO_WORKSPACE, FLAG_STORE_REF_PROTO_ARCHIVE, FLAG_STORE_REF_PROTO_USER,
                FLAG_STORE_REF_PROTO_SYSTEM };
        final byte[] idFlags = { FLAG_STORE_REF_ID_SPACESSTORE, FLAG_STORE_REF_ID_VERSION2STORE, FLAG_STORE_REF_ID_ALFRESCOUSERSTORE,
                FLAG_STORE_REF_ID_SYSTEM };

        for (final byte protoFlag : protoFlags)
        {
            for (final byte idFlag : idFlags)
            {
                STORE_REF_CONSTANTS[protoFlag | idFlag] = new StoreRef(STORE_REF_PROTOCOLS[protoFlag], STORE_REF_IDS[idFlag >> 2]);
            }
        }

        // Alfresco defined constants
        STORE_REF_CONSTANTS[FLAG_STORE_REF_PROTO_WORKSPACE | FLAG_STORE_REF_ID_SPACESSTORE] = StoreRef.STORE_REF_WORKSPACE_SPACESSTORE;
        STORE_REF_CONSTANTS[FLAG_STORE_REF_PROTO_ARCHIVE | FLAG_STORE_REF_ID_SPACESSTORE] = StoreRef.STORE_REF_ARCHIVE_SPACESSTORE;
    }

    private static final long STORE_REF_PROTOCOL_FIELD_OFFSET = getFieldOffset(StoreRef.class, "protocol", String.class);

    private static final long STORE_REF_IDENTIFIER_FIELD_OFFSET = getFieldOffset(StoreRef.class, "identifier", String.class);

    private static final long NODE_REF_STORE_REF_FIELD_OFFSET = getFieldOffset(NodeRef.class, "storeRef", StoreRef.class);

    private static final long NODE_REF_ID_FIELD_OFFSET = getFieldOffset(NodeRef.class, "id", String.class);

    private static final long QNAME_NAMESPACE_URI_FIELD_OFFSET = getFieldOffset(QName.class, "namespaceURI", String.class);

    private static final long QNAME_LOCAL_NAME_FIELD_OFFSET = getFieldOffset(QName.class, "localName", String.class);

    protected AbstractExtendedBinarySerializer(final Class<T> handledType)
    {
        super(handledType);
    }

    protected void writeStoreRef(final StoreRef storeRef, final BinaryOutputStream out)
    {
        final String protocol = storeRef.getProtocol();
        final String identifier = storeRef.getIdentifier();

        final byte flags = this.determineStoreFlags(protocol, identifier);

        out.unsafeEnsure(1);
        out.unsafeWriteByte(flags);

        if ((flags & FLAG_STORE_REF_PROTO_CUSTOM) != 0)
        {
            this.writeString(protocol, out);
        }

        if ((flags & FLAG_STORE_REF_ID_CUSTOM) != 0)
        {
            this.writeString(identifier, out);
        }
    }

    protected void writeNodeRef(final NodeRef nodeRef, final BinaryOutputStream out)
    {
        final StoreRef storeRef = nodeRef.getStoreRef();
        final String protocol = storeRef.getProtocol();
        final String identifier = storeRef.getIdentifier();

        final byte flags = this.determineStoreFlags(protocol, identifier);

        out.unsafeEnsure(1);
        out.unsafeWriteByte(flags);

        if ((flags & FLAG_STORE_REF_PROTO_CUSTOM) != 0)
        {
            this.writeString(protocol, out);
        }

        if ((flags & FLAG_STORE_REF_ID_CUSTOM) != 0)
        {
            this.writeString(identifier, out);
        }

        this.writeString(nodeRef.getId(), out);
    }

    protected void writeQName(final QName qname, final BinaryOutputStream out)
    {
        final String namespaceURI = qname.getNamespaceURI();
        final String localName = qname.getLocalName();

        byte flags = 0;

        final Namespace literal = Namespace.getLiteral(namespaceURI);

        if (literal != null)
        {
            flags |= (byte) literal.ordinal();
        }
        else
        {
            flags |= FLAG_QNAME_CUSTOM_NAMESPACE;
        }

        out.unsafeEnsure(1);
        out.unsafeWriteByte(flags);

        if (literal == null)
        {
            this.writeString(namespaceURI, out);
        }
        this.writeString(localName, out);
    }

    protected void writeChildAssociationRef(final ChildAssociationRef childRef, final Function<QName, Pair<Long, QName>> qnameIdLookup,
            final BinaryOutputStream out)
    {
        out.unsafeEnsure(1);
        final NodeRef parentRef = childRef.getParentRef();
        if (parentRef == null)
        {
            out.unsafeWriteByte(FLAG_CHILD_REF_PARENT_IS_ROOT);
        }
        else
        {
            byte flags = 0;
            final int startPos = out.position();
            out.position(startPos + 1);

            final QName typeQName = childRef.getTypeQName();
            flags |= this.writeValueOrId(typeQName, qnameIdLookup, this::writeQName, (byte) 0, FLAG_CHILD_REF_TYPE_QNAME_ID,
                    FLAG_CHILD_REF_TYPE_QNAME_ID_UNSIGNED, out);
            this.writeNodeRef(parentRef, out);
            this.writeQName(childRef.getQName(), out);

            if (childRef.isPrimary())
            {
                flags |= FLAG_CHILD_REF_PRIMARY;
            }

            final int nthSibling = childRef.getNthSibling();
            if (nthSibling >= 0)
            {
                flags |= FLAG_CHILD_REF_TYPE_SIBLING;
                this.writeUnsigned(nthSibling, out);
            }

            final int nearEndPos = out.position();
            out.position(startPos);
            out.unsafeWriteByte(flags);
            out.position(nearEndPos);
        }

        this.writeNodeRef(childRef.getChildRef(), out);
    }

    protected void writeAssociationRef(final AssociationRef assocRef, final Function<QName, Pair<Long, QName>> qnameIdLookup,
            final BinaryOutputStream out)
    {
        byte flags = 0;
        final int startPos = out.position();
        out.unsafeEnsure(1);
        out.position(startPos + 1);

        final Long id = assocRef.getId();
        if (id != null)
        {
            flags |= FLAG_ASSOC_REF_ID;
            flags |= this.writeWithFlagIfUnsigned(id.longValue(), FLAG_ASSOC_REF_ID_UNSIGNED, out);
        }

        final QName typeQName = assocRef.getTypeQName();
        flags |= this.writeValueOrId(typeQName, qnameIdLookup, this::writeQName, (byte) 0, FLAG_ASSOC_REF_TYPE_QNAME_ID,
                FLAG_ASSOC_REF_TYPE_QNAME_ID_UNSIGNED, out);

        this.writeNodeRef(assocRef.getSourceRef(), out);
        this.writeNodeRef(assocRef.getTargetRef(), out);

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteByte(flags);
        out.position(endPos);
    }

    protected StoreRef readStoreRef(final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        if ((flags & MASK_STORE_REF_CUSTOM_FLAGS) == 0)
        {
            return STORE_REF_CONSTANTS[flags & (MASK_STORE_REF_PROTO | MASK_STORE_REF_ID)];
        }

        String protocol;
        String identifier;

        if ((flags & FLAG_STORE_REF_PROTO_CUSTOM) != 0)
        {
            protocol = this.readString(rawReader);
        }
        else
        {
            protocol = STORE_REF_PROTOCOLS[flags & MASK_STORE_REF_PROTO];
        }

        if ((flags & FLAG_STORE_REF_ID_CUSTOM) != 0)
        {
            identifier = this.readString(rawReader);
        }
        else
        {
            identifier = STORE_REF_IDS[(flags & MASK_STORE_REF_ID) >> 2];
        }

        return new StoreRef(protocol, identifier);
    }

    protected void readStoreRef(final StoreRef storeRef, final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        String protocol;
        String identifier;

        if ((flags & FLAG_STORE_REF_PROTO_CUSTOM) != 0)
        {
            protocol = this.readString(rawReader);
        }
        else
        {
            protocol = STORE_REF_PROTOCOLS[flags & MASK_STORE_REF_PROTO];
        }

        if ((flags & FLAG_STORE_REF_ID_CUSTOM) != 0)
        {
            identifier = this.readString(rawReader);
        }
        else
        {
            identifier = STORE_REF_IDS[(flags & MASK_STORE_REF_ID) >> 2];
        }

        GridUnsafe.putObjectField(storeRef, STORE_REF_PROTOCOL_FIELD_OFFSET, protocol);
        GridUnsafe.putObjectField(storeRef, STORE_REF_IDENTIFIER_FIELD_OFFSET, identifier);
    }

    protected NodeRef readNodeRef(final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        StoreRef storeRef;
        String id;

        if ((flags & MASK_STORE_REF_CUSTOM_FLAGS) == 0)
        {
            storeRef = STORE_REF_CONSTANTS[flags & (MASK_STORE_REF_PROTO | MASK_STORE_REF_ID)];
        }
        else
        {
            String protocol;
            String identifier;

            if ((flags & FLAG_STORE_REF_PROTO_CUSTOM) != 0)
            {
                protocol = this.readString(rawReader);
            }
            else
            {
                protocol = STORE_REF_PROTOCOLS[flags & MASK_STORE_REF_PROTO];
            }

            if ((flags & FLAG_STORE_REF_ID_CUSTOM) != 0)
            {
                identifier = this.readString(rawReader);
            }
            else
            {
                identifier = STORE_REF_IDS[(flags & MASK_STORE_REF_ID) >> 2];
            }
            storeRef = new StoreRef(protocol, identifier);
        }

        id = this.readString(rawReader);

        return new NodeRef(storeRef, id);
    }

    protected void readNodeRef(final NodeRef nodeRef, final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        StoreRef storeRef;
        String id;

        if ((flags & MASK_STORE_REF_CUSTOM_FLAGS) == 0)
        {
            storeRef = STORE_REF_CONSTANTS[flags & (MASK_STORE_REF_PROTO | MASK_STORE_REF_ID)];
        }
        else
        {
            String protocol;
            String identifier;

            if ((flags & FLAG_STORE_REF_PROTO_CUSTOM) != 0)
            {
                protocol = this.readString(rawReader);
            }
            else
            {
                protocol = STORE_REF_PROTOCOLS[flags & MASK_STORE_REF_PROTO];
            }

            if ((flags & FLAG_STORE_REF_ID_CUSTOM) != 0)
            {
                identifier = this.readString(rawReader);
            }
            else
            {
                identifier = STORE_REF_IDS[(flags & MASK_STORE_REF_ID) >> 2];
            }
            storeRef = new StoreRef(protocol, identifier);
        }

        id = this.readString(rawReader);

        GridUnsafe.putObjectField(nodeRef, NODE_REF_STORE_REF_FIELD_OFFSET, storeRef);
        GridUnsafe.putObjectField(nodeRef, NODE_REF_ID_FIELD_OFFSET, id);
    }

    protected QName readQName(final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        String namespaceUri;
        String localName;

        if ((flags & FLAG_QNAME_CUSTOM_NAMESPACE) == FLAG_QNAME_CUSTOM_NAMESPACE)
        {
            namespaceUri = this.readString(rawReader);
        }
        else
        {
            final Namespace namespace = Namespace.values()[flags & MASK_QNAME_DEFAULT_NAMESPACES];
            namespaceUri = namespace.getUri();
        }

        localName = this.readString(rawReader);

        return QName.createQName(namespaceUri, localName);
    }

    protected void readQName(final QName qname, final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        String namespaceUri;
        String localName;

        if ((flags & FLAG_QNAME_CUSTOM_NAMESPACE) == FLAG_QNAME_CUSTOM_NAMESPACE)
        {
            namespaceUri = this.readString(rawReader);
        }
        else
        {
            final Namespace namespace = Namespace.values()[flags & MASK_QNAME_DEFAULT_NAMESPACES];
            namespaceUri = namespace.getUri();
        }

        localName = this.readString(rawReader);

        GridUnsafe.putObjectField(qname, QNAME_NAMESPACE_URI_FIELD_OFFSET, namespaceUri);
        GridUnsafe.putObjectField(qname, QNAME_LOCAL_NAME_FIELD_OFFSET, localName);
    }

    protected ChildAssociationRef readChildAssociationRef(final BinaryRawReader reader,
            final Function<Long, Pair<Long, QName>> qnameIdLookup)
    {
        final byte flags = reader.readByte();

        NodeRef parentRef = null;
        QName typeQName = null;
        NodeRef childRef;
        QName qname = null;
        boolean primary = false;
        int nthSibling = -1;

        if ((flags & FLAG_CHILD_REF_PARENT_IS_ROOT) == 0)
        {
            typeQName = this.doReadValueOrId(reader, qnameIdLookup, this::readQName, flags, FLAG_CHILD_REF_PARENT_IS_ROOT,
                    FLAG_CHILD_REF_TYPE_QNAME_ID, FLAG_CHILD_REF_TYPE_QNAME_ID_UNSIGNED);
            parentRef = this.readNodeRef(reader);
            qname = this.readQName(reader);
            primary = (flags & FLAG_CHILD_REF_PRIMARY) == FLAG_CHILD_REF_PRIMARY;

            if ((flags & FLAG_CHILD_REF_TYPE_SIBLING) == FLAG_CHILD_REF_TYPE_SIBLING)
            {
                nthSibling = this.readUnsignedInt(reader);
            }
        }

        childRef = this.readNodeRef(reader);

        return new ChildAssociationRef(typeQName, parentRef, qname, childRef, primary, nthSibling);
    }

    protected AssociationRef readAssociationRef(final BinaryRawReader reader, final Function<Long, Pair<Long, QName>> qnameIdLookup)
    {
        final byte flags = reader.readByte();

        Long id = null;
        NodeRef sourceRef;
        QName typeQName;
        NodeRef targetRef;

        if ((flags & FLAG_ASSOC_REF_ID) == FLAG_ASSOC_REF_ID)
        {
            id = Long.valueOf(this.readLong(reader, (flags & FLAG_ASSOC_REF_ID_UNSIGNED) == FLAG_ASSOC_REF_ID_UNSIGNED));
        }

        typeQName = this.doReadValueOrId(reader, qnameIdLookup, this::readQName, flags, (byte) -128, FLAG_ASSOC_REF_TYPE_QNAME_ID,
                FLAG_ASSOC_REF_TYPE_QNAME_ID_UNSIGNED);
        sourceRef = this.readNodeRef(reader);
        targetRef = this.readNodeRef(reader);

        return new AssociationRef(id, sourceRef, typeQName, targetRef);
    }

    protected byte determineStoreFlags(final String protocol, final String identifier)
    {
        byte flags = 0;
        switch (protocol)
        {
            case StoreRef.PROTOCOL_WORKSPACE:
                flags |= FLAG_STORE_REF_PROTO_WORKSPACE;
                break;
            case StoreRef.PROTOCOL_ARCHIVE:
                flags |= FLAG_STORE_REF_PROTO_ARCHIVE;
                break;
            case "user":
                flags |= FLAG_STORE_REF_PROTO_USER;
                break;
            case "system":
                flags |= FLAG_STORE_REF_PROTO_SYSTEM;
                break;
            default:
                flags |= FLAG_STORE_REF_PROTO_CUSTOM;
        }

        switch (identifier)
        {
            case "SpacesStore":
                flags |= FLAG_STORE_REF_ID_SPACESSTORE;
                break;
            case "version2Store":
                flags |= FLAG_STORE_REF_ID_VERSION2STORE;
                break;
            case "alfrescoUserStore":
                flags |= FLAG_STORE_REF_ID_ALFRESCOUSERSTORE;
                break;
            case "system":
                flags |= FLAG_STORE_REF_ID_SYSTEM;
                break;
            default:
                flags |= FLAG_STORE_REF_ID_CUSTOM;
        }
        return flags;
    }
}
