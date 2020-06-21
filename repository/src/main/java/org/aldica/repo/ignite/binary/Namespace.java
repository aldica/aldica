/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.model.BlogIntegrationModel;
import org.alfresco.model.ContentModel;
import org.alfresco.model.ImapModel;
import org.alfresco.model.QuickShareModel;
import org.alfresco.opencmis.mapping.CMISMapping;
import org.alfresco.repo.action.ActionModel;
import org.alfresco.repo.calendar.CalendarModel;
import org.alfresco.repo.download.DownloadModel;
import org.alfresco.repo.module.ModuleComponentHelper;
import org.alfresco.repo.remotecredentials.RemoteCredentialsModel;
import org.alfresco.repo.rule.RuleModel;
import org.alfresco.repo.search.impl.solr.facet.SolrFacetModel;
import org.alfresco.repo.site.SiteModel;
import org.alfresco.repo.transfer.TransferModel;
import org.alfresco.repo.version.Version2Model;
import org.alfresco.repo.version.VersionModel;
import org.alfresco.repo.virtual.VirtualContentModel;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;

/**
 * The values of this enum represent the well-known namespace URIs used in standard Alfresco, as well as a placeholder for custom URIs. This
 * enumeration is meant to be used to optimise the serial form of {@link QName qualified name} instances by replacing the multi-character
 * URI with a single enum ordinal for all well-known namespaces.
 *
 * @author Axel Faust
 */
public enum Namespace
{

    ALFRESCO(NamespaceService.ALFRESCO_URI),
    SYSTEM_MODEL(NamespaceService.SYSTEM_MODEL_1_0_URI),
    REGISTRY_MODEL("http://www.alfresco.org/system/registry/1.0"),
    MODULES_MODEL(ModuleComponentHelper.URI_MODULES_1_0),
    USER_MODEL(ContentModel.USER_MODEL_URI),
    DICTIONARY_MODEL(NamespaceService.DICTIONARY_MODEL_1_0_URI),
    CONTENT_MODEL(NamespaceService.CONTENT_MODEL_1_0_URI),
    APPLICATION_MODEL(NamespaceService.APP_MODEL_1_0_URI),
    AUDIO_MODEL(NamespaceService.AUDIO_MODEL_1_0_URI),
    EXIF_MODEL(NamespaceService.EXIF_MODEL_1_0_URI),
    WEBDAV_MODEL(NamespaceService.WEBDAV_MODEL_1_0_URI),
    DATALIST_MODEL(NamespaceService.DATALIST_MODEL_1_0_URI),
    BPM_MODEL(NamespaceService.BPM_MODEL_1_0_URI),
    WORKFLOW_MODEL(NamespaceService.WORKFLOW_MODEL_1_0_URI),
    FORUMS_MODEL(NamespaceService.FORUMS_MODEL_1_0_URI),
    LINKS_MODEL(NamespaceService.LINKS_MODEL_1_0_URI),
    RENDITION_MODEL(NamespaceService.RENDITION_MODEL_1_0_URI),
    REPOSITORY_VIEW(NamespaceService.REPOSITORY_VIEW_1_0_URI),
    SECURITY_MODEL(NamespaceService.SECURITY_MODEL_1_0_URI),
    EMAILSERVER_MODEL(NamespaceService.EMAILSERVER_MODEL_URI),
    SITE_MODEL(SiteModel.SITE_MODEL_URL),
    SITE_CUSTOM_PROPERTY(SiteModel.SITE_CUSTOM_PROPERTY_URL),
    VERSION_MODEL(VersionModel.NAMESPACE_URI),
    VERSION2_MODEL(Version2Model.NAMESPACE_URI),
    TRANSFER_MODEL(TransferModel.TRANSFER_MODEL_1_0_URI),
    ACTION_MODEL(ActionModel.ACTION_MODEL_URI),
    RULE_MODEL(RuleModel.RULE_MODEL_URI),
    DOWNLOAD_MODEL(DownloadModel.DOWNLOAD_MODEL_1_0_URI),
    IMAP_MODEL(ImapModel.IMAP_MODEL_1_0_URI),
    CALENDAR_MODEL(CalendarModel.CALENDAR_MODEL_URL),
    REMOTE_CREDENTIALS_MODEL(RemoteCredentialsModel.REMOTE_CREDENTIALS_MODEL_URL),
    BLOG_INTEGRATION_MODEL(BlogIntegrationModel.MODEL_URL),
    VIRTUAL_CONTENT_MODEL(VirtualContentModel.VIRTUAL_CONTENT_MODEL_1_0_URI),
    CMIS_MODEL(CMISMapping.CMIS_MODEL_URI),
    CMIS_EXT_MODEL(CMISMapping.CMIS_EXT_URI),
    QUICKSHARE_MODEL(QuickShareModel.QSHARE_MODEL_1_0_URI),
    SOLR_FACET_MODEL(SolrFacetModel.SOLR_FACET_MODEL_URL),
    SOLR_FACET_CUSTOM_PROPERTY(SolrFacetModel.SOLR_FACET_CUSTOM_PROPERTY_URL),
    CUSTOM(null);

    private static final Map<String, Namespace> LOOKUP = new HashMap<>();
    static
    {
        for (final Namespace value : Namespace.values())
        {
            final String name = value.getUri();
            if (name != null)
            {
                LOOKUP.put(name, value);
            }
        }
    }

    private final String uri;

    private Namespace(final String uri)
    {
        this.uri = uri;
    }

    /**
     * @return the uri
     */
    public String getUri()
    {
        return this.uri;
    }

    /**
     * Retrieves the enumeration literal corresponding to the provided cache region name.
     *
     * @param uri
     *            the namespace URI for which to retrieve the literal
     * @return the literal matching the namespace URI - will never be {@code null} and fall back to {@link #CUSTOM} for any namespace URI
     *         not matching the well known predefined Alfresco namespace
     */
    public static Namespace getLiteral(final String uri)
    {
        final Namespace literal = LOOKUP.getOrDefault(uri, CUSTOM);
        return literal;
    }
}
