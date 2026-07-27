/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.repo.domain.permissions.AclEntity;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.repo.security.permissions.ACEType;
import org.alfresco.repo.security.permissions.ACLType;
import org.alfresco.repo.security.permissions.PermissionReference;
import org.alfresco.repo.security.permissions.SimpleAccessControlEntry;
import org.alfresco.repo.security.permissions.SimpleAccessControlListProperties;
import org.alfresco.repo.security.permissions.impl.SimplePermissionReference;
import org.alfresco.repo.site.SiteModel;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
import org.apache.ignite.internal.util.GridUnsafe;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Instances of this class provide for the (de-)serialisation of specific value objects. In this class, patterns related to the raw
 * serialisation of ACL-related value types have been aggregated which may be re-used across different top-level types of serialised
 * objects.
 *
 * @author Axel Faust
 */
public abstract class AbstractAclBinarySerializer<T> extends AbstractExtendedBinarySerializer<T> implements ApplicationContextAware
{

    private static final short FLAG_ACL_ENTITY_ID_UNSIGNED = 0x01;

    private static final short FLAG_ACL_ENTITY_VERSION_UNSIGNED = 0x02;

    private static final short FLAG_ACL_ENTITY_IS_LATEST = 0x04;

    private static final short FLAG_ACL_ENTITY_ACL_VERSION_UNSIGNED = 0x08;

    private static final short FLAG_ACL_ENTITY_INHERITS = 0x10;

    private static final short FLAG_ACL_ENTITY_INHERITS_FROM_NULL = 0x20;

    private static final short FLAG_ACL_ENTITY_INHERITS_FROM_UNSIGNED = 0x40;

    // type is derived from ACLType enum (always unsigned)

    private static final short FLAG_ACL_ENTITY_INHERITED_ACL_NULL = 0x80;

    private static final short FLAG_ACL_ENTITY_INHERITED_ACL_UNSIGNED = 0x0100;

    private static final short FLAG_ACL_ENTITY_IS_VERSIONED = 0x0200;

    private static final short FLAG_ACL_ENTITY_REQUIRES_VERSION = 0x0400;

    private static final short FLAG_ACL_ENTITY_ACL_CHANGE_SET_UNSIGNED = 0x0800;

    private static final byte FLAG_ACL_PROPERTIES_ID_UNSIGNED = 0x01;

    private static final byte FLAG_ACL_PROPERTIES_IS_LATEST = 0x02;

    private static final byte FLAG_ACL_PROPERTIES_ACL_VERSION_UNSIGNED = 0x04;

    private static final byte FLAG_ACL_PROPERTIES_INHERITS = 0x08;

    private static final byte FLAG_ACL_PROPERTIES_IS_VERSIONED = 0x10;

    private static final byte FLAG_ACL_PROPERTIES_ACL_CHANGE_SET_UNSIGNED = 0x20;

    private static final byte MASK_ACL_ENTRY_TYPE = 0x03;

    private static final byte FLAG_ACL_ENTRY_ALLOWED = 0x04;

    private static final byte FLAG_ACL_ENTRY_POSITION_UNSIGNED = 0x08;

    private static final byte FLAG_ACL_ENTRY_PERMISSION_QNAME_ID = 0x10;

    private static final byte FLAG_ACL_ENTRY_PERMISSION_QNAME_ID_UNSIGNED = 0x20;

    private static final byte FLAG_ACL_ENTRY_PERMISSION_NAME_ID = 0x40;

    private static final byte FLAG_PERMISSION_QNAME_ID = 0x01;

    private static final byte FLAG_PERMISSION_QNAME_ID_UNSIGNED = 0x02;

    private static final byte FLAG_PERMISSION_NAME_ID = 0x04;

    private static final long PERMISSION_QNAME_FIELD_OFFSET = getFieldOffset(SimplePermissionReference.class, "qName", QName.class);

    private static final long PERMISSION_NAME_FIELD_OFFSET = getFieldOffset(SimplePermissionReference.class, "name", String.class);

    // most common exposed permission (group) names - high-level first (frequently used), then some low-level ones
    private static final String[] PERMISSION_NAMES = { PermissionService.FULL_CONTROL, PermissionService.COORDINATOR,
            PermissionService.CONSUMER, PermissionService.CONTRIBUTOR, PermissionService.EDITOR, "Collaborator", SiteModel.SITE_CONSUMER,
            SiteModel.SITE_CONTRIBUTOR, SiteModel.SITE_COLLABORATOR, SiteModel.SITE_MANAGER, PermissionService.READ,
            PermissionService.WRITE, PermissionService.DELETE, PermissionService.ADD_CHILDREN, PermissionService.READ_PROPERTIES,
            PermissionService.READ_CHILDREN, PermissionService.WRITE_PROPERTIES, PermissionService.DELETE_NODE,
            PermissionService.DELETE_CHILDREN, PermissionService.CREATE_CHILDREN, PermissionService.LINK_CHILDREN,
            PermissionService.READ_ASSOCIATIONS, PermissionService.CREATE_ASSOCIATIONS, PermissionService.DELETE_ASSOCIATIONS,
            PermissionService.READ_PERMISSIONS, PermissionService.CHANGE_PERMISSIONS,
            // exposed AGS permissions
            "Administrator", "Filing" };

    private static final Map<String, Byte> PERMISSION_IDS;

    static
    {
        final Map<String, Byte> ids = new HashMap<>();
        for (int idx = 0; idx < PERMISSION_NAMES.length && idx <= 255; idx++)
        {
            final byte id = (byte) (idx > 127 ? ((-1 * idx) + 127) : idx);
            ids.put(PERMISSION_NAMES[idx], id);
        }
        PERMISSION_IDS = Collections.unmodifiableMap(ids);
    }

    protected ApplicationContext applicationContext;

    protected QNameDAO qnameDAO;

    protected AbstractAclBinarySerializer(final Class<T> handledType)
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
    protected void ensureDAOsAvailable() throws BinaryObjectException
    {
        if (this.useIdsWhenReasonable && this.qnameDAO == null)
        {
            try
            {
                this.qnameDAO = this.applicationContext.getBean("qnameDAO", QNameDAO.class);
            }
            catch (final BeansException be)
            {
                throw new BinaryObjectException("Cannot (de-)serialise node properties in current configuration without access to QNameDAO",
                        be);
            }
        }
    }

    protected void writeAclEntity(final AclEntity aclEntity, final BinaryOutputStream out)
    {
        short flags = 0;

        final int startPos = out.position();
        out.unsafeEnsure(2);
        out.position(startPos + 2);

        flags |= this.writeWithFlagIfUnsigned(aclEntity.getId(), FLAG_ACL_ENTITY_ID_UNSIGNED, out);
        flags |= this.writeWithFlagIfUnsigned(aclEntity.getVersion(), FLAG_ACL_ENTITY_VERSION_UNSIGNED, out);

        this.writeString(aclEntity.getAclId(), out);

        if (aclEntity.isLatest())
        {
            flags |= FLAG_ACL_ENTITY_IS_LATEST;
        }

        flags |= this.writeWithFlagIfUnsigned(aclEntity.getAclVersion(), FLAG_ACL_ENTITY_ACL_VERSION_UNSIGNED, out);

        if (Boolean.TRUE.equals(aclEntity.getInherits()))
        {
            flags |= FLAG_ACL_ENTITY_INHERITS;
        }

        final Long inheritsFrom = aclEntity.getInheritsFrom();
        if (inheritsFrom != null)
        {
            flags |= this.writeWithFlagIfUnsigned(inheritsFrom, FLAG_ACL_ENTITY_INHERITS_FROM_UNSIGNED, out);
        }
        else
        {
            flags |= FLAG_ACL_ENTITY_INHERITS_FROM_NULL;
        }

        this.writeUnsigned(aclEntity.getType(), out);

        Long inheritedAcl = aclEntity.getInheritedAcl();
        if (inheritedAcl != null)
        {
            flags |= this.writeWithFlagIfUnsigned(inheritedAcl, FLAG_ACL_ENTITY_INHERITED_ACL_UNSIGNED, out);
        }
        else
        {
            flags |= FLAG_ACL_ENTITY_INHERITED_ACL_NULL;
        }

        if (aclEntity.isVersioned())
        {
            flags |= FLAG_ACL_ENTITY_IS_VERSIONED;
        }
        if (Boolean.TRUE.equals(aclEntity.getRequiresVersion()))
        {
            flags |= FLAG_ACL_ENTITY_REQUIRES_VERSION;
        }

        flags |= this.writeWithFlagIfUnsigned(aclEntity.getAclChangeSetId(), FLAG_ACL_ENTITY_ACL_CHANGE_SET_UNSIGNED, out);

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteShort(flags);
        out.position(endPos);
    }

    protected void writeAclProperties(final SimpleAccessControlListProperties aclProperties, final BinaryOutputStream out)
    {
        byte flags = 0;

        final int startPos = out.position();
        out.unsafeEnsure(1);
        out.position(startPos + 1);

        flags |= this.writeWithFlagIfUnsigned(aclProperties.getId(), FLAG_ACL_PROPERTIES_ID_UNSIGNED, out);

        this.writeString(aclProperties.getAclId(), out);

        if (Boolean.TRUE.equals(aclProperties.isLatest()))
        {
            flags |= FLAG_ACL_PROPERTIES_IS_LATEST;
        }

        flags |= this.writeWithFlagIfUnsigned(aclProperties.getAclVersion(), FLAG_ACL_PROPERTIES_ACL_VERSION_UNSIGNED, out);

        if (Boolean.TRUE.equals(aclProperties.getInherits()))
        {
            flags |= FLAG_ACL_PROPERTIES_INHERITS;
        }

        final ACLType aclType = aclProperties.getAclType();
        this.writeUnsigned(aclType.getId(), out);

        if (Boolean.TRUE.equals(aclProperties.isVersioned()))
        {
            flags |= FLAG_ACL_PROPERTIES_IS_VERSIONED;
        }

        flags |= this.writeWithFlagIfUnsigned(aclProperties.getAclChangeSetId(), FLAG_ACL_PROPERTIES_ACL_CHANGE_SET_UNSIGNED, out);

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteByte(flags);
        out.position(endPos);
    }

    protected void writeAce(final SimpleAccessControlEntry entry, final BinaryOutputStream out)
    {

        final int startPos = out.position();
        out.unsafeEnsure(1);
        out.position(startPos + 1);

        final ACEType aceType = entry.getAceType();
        byte flags = (byte) aceType.getId();

        final AccessStatus accessStatus = entry.getAccessStatus();
        flags |= accessStatus == AccessStatus.ALLOWED ? FLAG_ACL_ENTRY_ALLOWED : 0;

        this.writeString(entry.getAuthority(), out);

        final PermissionReference permission = entry.getPermission();
        final QName qName = permission.getQName();
        final String name = permission.getName();

        flags |= this.writeValueOrId(qName, this::qnameLookup, this::writeQName, (byte) 0, FLAG_ACL_ENTRY_PERMISSION_QNAME_ID,
                FLAG_ACL_ENTRY_PERMISSION_QNAME_ID_UNSIGNED, out);
        final Byte id = resolvePermissionId(name);
        if (id != null)
        {
            flags |= FLAG_ACL_ENTRY_PERMISSION_NAME_ID;
            out.unsafeEnsure(1);
            out.writeByte(id.byteValue());
        }
        else
        {
            this.writeString(name, out);
        }

        flags |= this.writeWithFlagIfUnsigned(entry.getPosition(), FLAG_ACL_ENTRY_POSITION_UNSIGNED, out);

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteByte(flags);
        out.position(endPos);
    }

    protected void writePermissionReference(final SimplePermissionReference permission, final BinaryOutputStream out)
    {
        byte flags = 0;

        final int startPos = out.position();
        out.unsafeEnsure(1);
        out.position(startPos + 1);

        final QName qName = permission.getQName();
        final String name = permission.getName();

        flags |= this.writeValueOrId(qName, this::qnameLookup, this::writeQName, (byte) 0, FLAG_PERMISSION_QNAME_ID,
                FLAG_PERMISSION_QNAME_ID_UNSIGNED, out);
        final Byte id = resolvePermissionId(name);
        if (id != null)
        {
            flags |= FLAG_PERMISSION_NAME_ID;
            out.unsafeEnsure(1);
            out.writeByte(id.byteValue());
        }
        else
        {
            this.writeString(name, out);
        }

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteByte(flags);
        out.position(endPos);
    }

    protected void readAclEntity(final AclEntity aclEntity, final BinaryRawReader rawReader)
    {
        final short flags = rawReader.readShort();

        final long id = this.readLong(rawReader, (flags & FLAG_ACL_ENTITY_ID_UNSIGNED) == FLAG_ACL_ENTITY_ID_UNSIGNED);
        final long version = this.readLong(rawReader, (flags & FLAG_ACL_ENTITY_VERSION_UNSIGNED) == FLAG_ACL_ENTITY_VERSION_UNSIGNED);
        final String aclId = this.readString(rawReader);
        final boolean isLatest = (flags & FLAG_ACL_ENTITY_IS_LATEST) == FLAG_ACL_ENTITY_IS_LATEST;
        final long aclVersion = this.readLong(rawReader,
                (flags & FLAG_ACL_ENTITY_ACL_VERSION_UNSIGNED) == FLAG_ACL_ENTITY_ACL_VERSION_UNSIGNED);
        final boolean inherits = (flags & FLAG_ACL_ENTITY_INHERITS) == FLAG_ACL_ENTITY_INHERITS;

        aclEntity.setId(id);
        aclEntity.setVersion(version);
        aclEntity.setAclId(aclId);
        aclEntity.setLatest(isLatest);
        aclEntity.setAclVersion(aclVersion);
        aclEntity.setInherits(inherits);

        if ((flags & FLAG_ACL_ENTITY_INHERITS_FROM_NULL) == 0)
        {
            final long inheritsFrom = this.readLong(rawReader,
                    (flags & FLAG_ACL_ENTITY_INHERITS_FROM_UNSIGNED) == FLAG_ACL_ENTITY_INHERITS_FROM_UNSIGNED);
            aclEntity.setInheritsFrom(inheritsFrom);
        }

        final int type = this.readInt(rawReader, true);
        if ((flags & FLAG_ACL_ENTITY_INHERITED_ACL_NULL) == 0)
        {
            final long inheritedAcl = this.readLong(rawReader,
                    (flags & FLAG_ACL_ENTITY_INHERITED_ACL_UNSIGNED) == FLAG_ACL_ENTITY_INHERITED_ACL_UNSIGNED);
            aclEntity.setInheritedAcl(inheritedAcl);
        }
        final boolean isVersioned = (flags & FLAG_ACL_ENTITY_IS_VERSIONED) == FLAG_ACL_ENTITY_IS_VERSIONED;
        final boolean requriresVersion = (flags & FLAG_ACL_ENTITY_REQUIRES_VERSION) == FLAG_ACL_ENTITY_REQUIRES_VERSION;
        final long aclChangeSet = this.readLong(rawReader,
                (flags & FLAG_ACL_ENTITY_ACL_CHANGE_SET_UNSIGNED) == FLAG_ACL_ENTITY_ACL_CHANGE_SET_UNSIGNED);

        aclEntity.setType(type);
        aclEntity.setVersioned(isVersioned);
        aclEntity.setRequiresVersion(requriresVersion);
        aclEntity.setAclChangeSetId(aclChangeSet);
    }

    protected void readAclProperties(final SimpleAccessControlListProperties aclProperties, final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        final long id = this.readLong(rawReader, (flags & FLAG_ACL_PROPERTIES_ID_UNSIGNED) == FLAG_ACL_PROPERTIES_ID_UNSIGNED);
        final String aclId = this.readString(rawReader);
        final boolean isLatest = (flags & FLAG_ACL_PROPERTIES_IS_LATEST) == FLAG_ACL_PROPERTIES_IS_LATEST;
        final long aclVersion = this.readLong(rawReader,
                (flags & FLAG_ACL_PROPERTIES_ACL_VERSION_UNSIGNED) == FLAG_ACL_PROPERTIES_ACL_VERSION_UNSIGNED);
        final boolean inherits = (flags & FLAG_ACL_PROPERTIES_INHERITS) == FLAG_ACL_PROPERTIES_INHERITS;

        aclProperties.setId(id);
        aclProperties.setAclId(aclId);
        aclProperties.setLatest(isLatest);
        aclProperties.setAclVersion(aclVersion);
        aclProperties.setInherits(inherits);

        final int type = this.readInt(rawReader, true);
        final boolean isVersioned = (flags & FLAG_ACL_PROPERTIES_IS_VERSIONED) == FLAG_ACL_PROPERTIES_IS_VERSIONED;
        final long aclChangeSet = this.readLong(rawReader,
                (flags & FLAG_ACL_PROPERTIES_ACL_CHANGE_SET_UNSIGNED) == FLAG_ACL_PROPERTIES_ACL_CHANGE_SET_UNSIGNED);

        aclProperties.setAclType(ACLType.getACLTypeFromId(type));
        aclProperties.setVersioned(isVersioned);
        aclProperties.setAclChangeSetId(aclChangeSet);
    }

    protected void readAce(final SimpleAccessControlEntry entry, final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        final AccessStatus accessStatus = (flags & FLAG_ACL_ENTRY_ALLOWED) == FLAG_ACL_ENTRY_ALLOWED ? AccessStatus.ALLOWED
                : AccessStatus.DENIED;
        entry.setAccessStatus(accessStatus);

        final int aceTypeId = flags & MASK_ACL_ENTRY_TYPE;
        final ACEType aceType = ACEType.getACETypeFromId(aceTypeId);
        entry.setAceType(aceType);

        final String authority = this.readString(rawReader);
        entry.setAuthority(authority);

        final QName qName = this.doReadValueOrId(rawReader, this::qnameLookup, this::readQName, flags, (byte) 0,
                FLAG_ACL_ENTRY_PERMISSION_QNAME_ID, FLAG_ACL_ENTRY_PERMISSION_QNAME_ID_UNSIGNED);
        final String name;

        if ((flags & FLAG_ACL_ENTRY_PERMISSION_NAME_ID) == FLAG_ACL_ENTRY_PERMISSION_NAME_ID)
        {
            name = resolvePermissionName(rawReader.readByte());
        }
        else
        {
            name = this.readString(rawReader);
        }

        final SimplePermissionReference permission = SimplePermissionReference.getPermissionReference(qName, name);
        entry.setPermission(permission);

        final int position = this.readInt(rawReader, (flags & FLAG_ACL_ENTRY_POSITION_UNSIGNED) == FLAG_ACL_ENTRY_POSITION_UNSIGNED);
        entry.setPosition(position);
    }

    protected void readPermissionReference(final SimplePermissionReference permission, final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        final QName qName = this.doReadValueOrId(rawReader, this::qnameLookup, this::readQName, flags, (byte) 0, FLAG_PERMISSION_QNAME_ID,
                FLAG_PERMISSION_QNAME_ID_UNSIGNED);
        final String name;

        if ((flags & FLAG_PERMISSION_NAME_ID) == FLAG_PERMISSION_NAME_ID)
        {
            name = resolvePermissionName(rawReader.readByte());
        }
        else
        {
            name = this.readString(rawReader);
        }

        this.setPermissionReferenceFields(permission, qName, name);
    }

    protected void setPermissionReferenceFields(final SimplePermissionReference permission, final QName qName, final String name)
    {
        GridUnsafe.putObjectField(permission, PERMISSION_QNAME_FIELD_OFFSET, qName);
        GridUnsafe.putObjectField(permission, PERMISSION_NAME_FIELD_OFFSET, name);
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

    protected static Byte resolvePermissionId(final String name)
    {
        return PERMISSION_IDS.get(name);
    }

    protected static String resolvePermissionName(final byte permissionId)
    {
        int id = permissionId;
        if (id < 0)
        {
            id = 256 - id;
        }
        return PERMISSION_NAMES[id];
    }
}
