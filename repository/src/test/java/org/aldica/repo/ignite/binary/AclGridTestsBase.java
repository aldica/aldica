/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.security.SecureRandom;
import java.util.UUID;

import org.aldica.common.ignite.GridTestsBase;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.domain.permissions.AclEntity;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.repo.domain.qname.ibatis.QNameDAOImpl;
import org.alfresco.repo.security.permissions.ACEType;
import org.alfresco.repo.security.permissions.ACLType;
import org.alfresco.repo.security.permissions.SimpleAccessControlEntry;
import org.alfresco.repo.security.permissions.SimpleAccessControlListProperties;
import org.alfresco.repo.security.permissions.impl.SimplePermissionReference;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author Axel Faust
 */
public abstract class AclGridTestsBase extends GridTestsBase
{

    private static final QName CUSTOM_ASPECT = QName.createQName("myCustomModel", "myCustomAspect");

    @SuppressWarnings("deprecation")
    private static final QName[] QNAMES = { ContentModel.TYPE_CMOBJECT, ContentModel.ASPECT_ANULLABLE, ContentModel.ASPECT_ARCHIVE_LOCKABLE,
            ContentModel.ASPECT_ARCHIVE_ROOT, ContentModel.ASPECT_ARCHIVED, ContentModel.ASPECT_ARCHIVED_ASSOCS,
            ContentModel.ASPECT_ATTACHABLE, ContentModel.ASPECT_AUDITABLE, ContentModel.ASPECT_AUTHOR, ContentModel.ASPECT_CASCADE_UPDATE,
            ContentModel.ASPECT_CHECKED_OUT, ContentModel.ASPECT_CLASSIFIABLE, ContentModel.ASPECT_CMIS_CREATED_CHECKEDOUT,
            ContentModel.ASPECT_CMIS_UPDATE_CONTEXT, ContentModel.ASPECT_COPIEDFROM, ContentModel.ASPECT_COUNTABLE,
            ContentModel.ASPECT_DUBLINCORE, ContentModel.ASPECT_EMAILED, ContentModel.ASPECT_FAILED_THUMBNAIL_SOURCE,
            ContentModel.ASPECT_FIVESTAR_RATING_SCHEME_ROLLUPS, ContentModel.ASPECT_GEN_CLASSIFIABLE, ContentModel.ASPECT_GEOGRAPHIC,
            ContentModel.ASPECT_GEOGRAPHIC, ContentModel.ASPECT_HIDDEN, ContentModel.ASPECT_INCOMPLETE, ContentModel.ASPECT_INDEX_CONTROL,
            ContentModel.ASPECT_LIKES_RATING_SCHEME_ROLLUPS, ContentModel.ASPECT_LOCALIZED, ContentModel.ASPECT_LOCKABLE,
            ContentModel.ASPECT_MULTILINGUAL_DOCUMENT, ContentModel.ASPECT_MULTILINGUAL_EMPTY_TRANSLATION, ContentModel.ASPECT_NO_CONTENT,
            ContentModel.ASPECT_OWNABLE, ContentModel.ASPECT_PENDING_FIX_ACL, ContentModel.ASPECT_PERSON_DISABLED,
            ContentModel.ASPECT_PREFERENCES, ContentModel.ASPECT_RATEABLE, ContentModel.ASPECT_REFERENCEABLE,
            ContentModel.ASPECT_REFERENCES_NODE, ContentModel.ASPECT_REFERENCING, ContentModel.ASPECT_ROOT, ContentModel.ASPECT_SOFT_DELETE,
            ContentModel.ASPECT_STORE_SELECTOR, ContentModel.ASPECT_SYNDICATION, ContentModel.ASPECT_TAGGABLE, ContentModel.ASPECT_TAGSCOPE,
            ContentModel.ASPECT_TEMPLATABLE, ContentModel.ASPECT_TEMPORARY, ContentModel.ASPECT_THUMBNAIL_MODIFICATION,
            ContentModel.ASPECT_THUMBNAILED, ContentModel.ASPECT_TITLED, ContentModel.ASPECT_UNDELETABLE, ContentModel.ASPECT_UNMOVABLE,
            ContentModel.ASPECT_VERSIONABLE, ContentModel.ASPECT_WEBDAV_NO_CONTENT, ContentModel.ASPECT_WEBDAV_OBJECT,
            ContentModel.ASPECT_WEBSCRIPTABLE, ContentModel.ASPECT_WORKING_COPY, CUSTOM_ASPECT };

    private static final String[] PERMISSION_NAMES = { PermissionService.CONSUMER, PermissionService.EDITOR, PermissionService.CONTRIBUTOR,
            "Collaborator", PermissionService.COORDINATOR, "CustomPermission" };

    protected static GenericApplicationContext createApplicationContext()
    {
        final GenericApplicationContext appContext = new GenericApplicationContext();

        final QNameDAO qnameDAO = EasyMock.partialMockBuilder(QNameDAOImpl.class).addMockedMethod("getQName", Long.class)
                .addMockedMethod("getQName", QName.class).createMock();
        appContext.getBeanFactory().registerSingleton("qnameDAO", qnameDAO);
        appContext.refresh();

        for (int idx = 0; idx < QNAMES.length; idx++)
        {
            final Long exposedId = Long.valueOf(Integer.MAX_VALUE - idx);
            EasyMock.expect(qnameDAO.getQName(exposedId)).andStubReturn(new Pair<>(exposedId, QNAMES[idx]));
            EasyMock.expect(qnameDAO.getQName(QNAMES[idx])).andStubReturn(new Pair<>(exposedId, QNAMES[idx]));
        }
        EasyMock.expect(qnameDAO.getQName(EasyMock.anyObject(QName.class))).andStubReturn(null);

        EasyMock.replay(qnameDAO);

        return appContext;
    }

    protected void assertAclEntity(AclEntity control, AclEntity comparison)
    {
        // only shallow equals() in AclEntity
        // we do a deep check ourselves
        Assert.assertEquals(control.getId(), comparison.getId());
        Assert.assertEquals(control.getVersion(), comparison.getVersion());
        Assert.assertEquals(control.getAclId(), comparison.getAclId());
        Assert.assertEquals(control.isLatest(), comparison.isLatest());
        Assert.assertEquals(control.getAclVersion(), comparison.getAclVersion());
        Assert.assertEquals(control.getInherits(), comparison.getInherits());
        Assert.assertEquals(control.getInheritsFrom(), comparison.getInheritsFrom());
        Assert.assertEquals(control.getAclType(), comparison.getAclType());
        Assert.assertEquals(control.getInheritedAcl(), comparison.getInheritedAcl());
        Assert.assertEquals(control.isVersioned(), comparison.isVersioned());
        Assert.assertEquals(control.getRequiresVersion(), comparison.getRequiresVersion());
        Assert.assertEquals(control.getAclChangeSetId(), comparison.getAclChangeSetId());
    }

    protected AclEntity randomAclEntity(final SecureRandom rnJesus)
    {
        final AclEntity acl = new AclEntity();

        final long id = rnJesus.nextLong();
        acl.setId(id);
        acl.setVersion(Long.valueOf(rnJesus.nextInt(100)));
        acl.setAclId(UUID.randomUUID().toString());
        acl.setLatest(rnJesus.nextBoolean());
        acl.setAclVersion(Long.valueOf(rnJesus.nextInt(100)));
        final boolean inherits = rnJesus.nextBoolean();
        acl.setInherits(inherits);
        if (inherits)
        {
            acl.setInheritsFrom(id - 1);
        }
        acl.setType(rnJesus.nextInt(6));
        acl.setInheritedAcl(id + 1);
        acl.setVersioned(rnJesus.nextBoolean());
        acl.setRequiresVersion(rnJesus.nextBoolean());
        acl.setAclChangeSetId(rnJesus.nextLong());

        return acl;
    }

    protected void assertAclProperties(SimpleAccessControlListProperties control, SimpleAccessControlListProperties comparison)
    {
        // no equals() in SimpleAccessControlListProperties
        // we do a deep check ourselves
        Assert.assertEquals(control.getId(), comparison.getId());
        Assert.assertEquals(control.getAclId(), comparison.getAclId());
        Assert.assertEquals(control.isLatest(), comparison.isLatest());
        Assert.assertEquals(control.getAclVersion(), comparison.getAclVersion());
        Assert.assertEquals(control.getInherits(), comparison.getInherits());
        Assert.assertEquals(control.getAclType(), comparison.getAclType());
        Assert.assertEquals(control.isVersioned(), comparison.isVersioned());
        Assert.assertEquals(control.getAclChangeSetId(), comparison.getAclChangeSetId());
    }

    protected SimpleAccessControlListProperties randomAclProperties(final SecureRandom rnJesus)
    {
        final SimpleAccessControlListProperties acl = new SimpleAccessControlListProperties();

        final long id = rnJesus.nextLong();
        acl.setId(id);
        acl.setAclId(UUID.randomUUID().toString());
        acl.setLatest(rnJesus.nextBoolean());
        acl.setAclVersion(Long.valueOf(rnJesus.nextInt(100)));
        final boolean inherits = rnJesus.nextBoolean();
        acl.setInherits(inherits);
        acl.setAclType(ACLType.getACLTypeFromId(rnJesus.nextInt(6)));
        acl.setVersioned(rnJesus.nextBoolean());
        acl.setAclChangeSetId(rnJesus.nextLong());

        return acl;
    }

    protected SimplePermissionReference randomPermission(final SecureRandom rnJesus)
    {
        final QName qName = QNAMES[rnJesus.nextInt(QNAMES.length)];
        final String name = PERMISSION_NAMES[rnJesus.nextInt(PERMISSION_NAMES.length)];
        return SimplePermissionReference.getPermissionReference(qName, name);
    }

    protected void assertAce(SimpleAccessControlEntry control, SimpleAccessControlEntry comparison)
    {
        // no equals() in SimpleAccessControlEntry
        // we do a deep check ourselves
        Assert.assertEquals(control.getAceType(), comparison.getAceType());
        Assert.assertEquals(control.getAccessStatus(), comparison.getAccessStatus());
        Assert.assertEquals(control.getAuthority(), comparison.getAuthority());
        Assert.assertEquals(control.getPermission(), comparison.getPermission());
        Assert.assertEquals(control.getPosition(), comparison.getPosition());
    }

    protected SimpleAccessControlEntry randomAce(final SecureRandom rnJesus)
    {
        return this.randomAce(rnJesus, rnJesus.nextInt(10));
    }

    protected SimpleAccessControlEntry randomAce(final SecureRandom rnJesus, final int position)
    {
        final SimpleAccessControlEntry ace = new SimpleAccessControlEntry();
        ace.setAceType(ACEType.getACETypeFromId(rnJesus.nextInt(3)));
        ace.setAccessStatus(rnJesus.nextBoolean() ? AccessStatus.ALLOWED : AccessStatus.DENIED);
        ace.setAuthority(UUID.randomUUID().toString());
        ace.setPermission(this.randomPermission(rnJesus));
        ace.setPosition(position);

        return ace;
    }
}
