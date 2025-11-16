/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.web.scripts;

import com.thedeanda.lorem.Lorem;
import com.thedeanda.lorem.LoremIpsum;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.BlogIntegrationModel;
import org.alfresco.model.ContentModel;
import org.alfresco.model.ImapModel;
import org.alfresco.model.QuickShareModel;
import org.alfresco.opencmis.mapping.CMISMapping;
import org.alfresco.repo.action.ActionModel;
import org.alfresco.repo.batch.BatchProcessWorkProvider;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.batch.BatchProcessor.BatchProcessWorker;
import org.alfresco.repo.calendar.CalendarModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.download.DownloadModel;
import org.alfresco.repo.module.ModuleComponentHelper;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.remotecredentials.RemoteCredentialsModel;
import org.alfresco.repo.rule.RuleModel;
import org.alfresco.repo.search.impl.solr.facet.SolrFacetModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.site.SiteModel;
import org.alfresco.repo.transfer.TransferModel;
import org.alfresco.repo.version.Version2Model;
import org.alfresco.repo.version.VersionModel;
import org.alfresco.repo.virtual.VirtualContentModel;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.rule.RuleService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.Pair;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.apache.commons.logging.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * @author Axel Faust
 */
public class GenerateNodes extends AbstractWebScript implements InitializingBean
{

    public static final QName INT_A = QName.createQName("http://www.aldica.org/model/mem-bm/1.0", "intA");

    public static final QName LONG_B = QName.createQName("http://www.aldica.org/model/mem-bm/1.0", "longB");

    public static final QName FLOAT_C = QName.createQName("http://www.aldica.org/model/mem-bm/1.0", "floatC");

    public static final QName DOUBLE_D = QName.createQName("http://www.aldica.org/model/mem-bm/1.0", "doubleD");

    public static final QName DATE_E = QName.createQName("http://www.aldica.org/model/mem-bm/1.0", "dateE");

    public static final QName DATE_F = QName.createQName("http://www.aldica.org/model/mem-bm/1.0", "dateF");

    public static final QName NODE_G = QName.createQName("http://www.aldica.org/model/mem-bm/1.0", "nodeG");

    public static final QName QNAME_H = QName.createQName("http://www.aldica.org/model/mem-bm/1.0", "qnameH");

    public static final QName LOV_STRING = QName.createQName("http://www.aldica.org/model/mem-bm/1.0", "lovString");

    private static final String[] NAMESPACES = { NamespaceService.ALFRESCO_URI, NamespaceService.SYSTEM_MODEL_1_0_URI,
            "http://www.alfresco.org/system/registry/1.0", ModuleComponentHelper.URI_MODULES_1_0, ContentModel.USER_MODEL_URI,
            NamespaceService.DICTIONARY_MODEL_1_0_URI, NamespaceService.CONTENT_MODEL_1_0_URI, NamespaceService.APP_MODEL_1_0_URI,
            NamespaceService.AUDIO_MODEL_1_0_URI, NamespaceService.EXIF_MODEL_1_0_URI, NamespaceService.WEBDAV_MODEL_1_0_URI,
            NamespaceService.DATALIST_MODEL_1_0_URI, NamespaceService.BPM_MODEL_1_0_URI, NamespaceService.WORKFLOW_MODEL_1_0_URI,
            NamespaceService.FORUMS_MODEL_1_0_URI, NamespaceService.LINKS_MODEL_1_0_URI, NamespaceService.RENDITION_MODEL_1_0_URI,
            NamespaceService.REPOSITORY_VIEW_1_0_URI, NamespaceService.SECURITY_MODEL_1_0_URI, NamespaceService.EMAILSERVER_MODEL_URI,
            SiteModel.SITE_MODEL_URL, SiteModel.SITE_CUSTOM_PROPERTY_URL, VersionModel.NAMESPACE_URI, Version2Model.NAMESPACE_URI,
            TransferModel.TRANSFER_MODEL_1_0_URI, ActionModel.ACTION_MODEL_URI, RuleModel.RULE_MODEL_URI,
            DownloadModel.DOWNLOAD_MODEL_1_0_URI, ImapModel.IMAP_MODEL_1_0_URI, CalendarModel.CALENDAR_MODEL_URL,
            RemoteCredentialsModel.REMOTE_CREDENTIALS_MODEL_URL, BlogIntegrationModel.MODEL_URL,
            VirtualContentModel.VIRTUAL_CONTENT_MODEL_1_0_URI, CMISMapping.CMIS_MODEL_URI, CMISMapping.CMIS_EXT_URI,
            QuickShareModel.QSHARE_MODEL_1_0_URI, SolrFacetModel.SOLR_FACET_MODEL_URL, SolrFacetModel.SOLR_FACET_CUSTOM_PROPERTY_URL };

    private static final String[] AUTHORITY_NAMES = { "mjackson", "abeecher", "mmustermann", UUID.randomUUID().toString(),
            "GROUP_site_test_SiteConsumer", "GROUP_site_test_SiteManager", "GROUP_site_test_SiteContributor",
            "GROUP_site_test_SiteCollaboroator", "GROUP_DMS_User", "GROUP_" + UUID.randomUUID().toString() };

    private static final String[] PERMISSION_NAMES = { PermissionService.CONSUMER, PermissionService.CONTRIBUTOR, PermissionService.EDITOR,
            PermissionService.COORDINATOR, "Collaborator", SiteModel.SITE_COLLABORATOR, SiteModel.SITE_CONSUMER, SiteModel.SITE_CONTRIBUTOR,
            SiteModel.SITE_MANAGER };

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateNodes.class);

    private static final Random RN_JESUS = new SecureRandom();

    private static final String[] LOVS = new String[] { "Value ABC", "Value DEF", "Value GHI", "Value JK", "Value LMN", "Value OPQ",
            "Value RST", "Value UVW", "Value XYZ" };

    private static final int MIN_DAY = (int) LocalDate.of(2010, 1, 1).toEpochDay();

    private static final int MAX_DAY = (int) LocalDate.now(ZoneOffset.UTC).toEpochDay();

    private static final long MIN_TIME = LocalDateTime.of(2010, 1, 1, 0, 0).toEpochSecond(ZoneOffset.UTC);

    private static final long MAX_TIME = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC);

    private static final Lorem lorem = LoremIpsum.getInstance();

    protected TransactionService transactionService;

    protected NodeService nodeService;

    protected PermissionService permissionService;

    protected PersonService personService;

    protected ContentService contentService;

    protected RuleService ruleService;

    protected BehaviourFilter behaviourFilter;

    protected String guaranteedAccessUserName;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "transactionService", this.transactionService);
        PropertyCheck.mandatory(this, "nodeService", this.nodeService);
        PropertyCheck.mandatory(this, "permissionService", this.permissionService);
        PropertyCheck.mandatory(this, "personService", this.personService);
        PropertyCheck.mandatory(this, "ruleService", this.ruleService);
        PropertyCheck.mandatory(this, "behaviourFilter", this.behaviourFilter);
        PropertyCheck.mandatory(this, "guaranteedAccessUserName", this.guaranteedAccessUserName);
    }

    /**
     * @param transactionService
     *     the transactionService to set
     */
    public void setTransactionService(final TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    /**
     * @param nodeService
     *     the nodeService to set
     */
    public void setNodeService(final NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    /**
     * @param permissionService
     *     the permissionService to set
     */
    public void setPermissionService(final PermissionService permissionService)
    {
        this.permissionService = permissionService;
    }

    /**
     * @param personService
     *     the personService to set
     */
    public void setPersonService(PersonService personService)
    {
        this.personService = personService;
    }

    /**
     * @param contentService
     *     the contentService to set
     */
    public void setContentService(final ContentService contentService)
    {
        this.contentService = contentService;
    }

    /**
     * @param ruleService
     *     the ruleService to set
     */
    public void setRuleService(final RuleService ruleService)
    {
        this.ruleService = ruleService;
    }

    /**
     * @param behaviourFilter
     *     the behaviourFilter to set
     */
    public void setBehaviourFilter(final BehaviourFilter behaviourFilter)
    {
        this.behaviourFilter = behaviourFilter;
    }

    /**
     * @param guaranteedAccessUserName
     *     the guaranteedAccessUserName to set
     */
    public void setGuaranteedAccessUserName(String guaranteedAccessUserName)
    {
        this.guaranteedAccessUserName = guaranteedAccessUserName;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void execute(final WebScriptRequest req, final WebScriptResponse res) throws IOException
    {
        final int threads = Integer.parseInt(req.getParameter("threads"));
        final int count = Integer.parseInt(req.getParameter("countNodes"));
        final int maxChildrenPerFolder = Integer.parseInt(req.getParameter("maxChildrenPerFolder"));
        final int wordsPerNode = req.getParameter("wordsPerNode") != null ? Integer.parseInt(req.getParameter("wordsPerNode")) : 0;

        LOGGER.info("Web script called to generate {} nodes in {} threads with {} max children per folder", count, threads,
                maxChildrenPerFolder);

        AuthenticationUtil.runAsSystem(() -> {

            final NodeRef rootNode = this.nodeService.getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
            final NodeRef companyHome = this.nodeService.getChildAssocs(rootNode, ContentModel.ASSOC_CHILDREN,
                    QName.createQName("http://www.alfresco.org/model/application/1.0", "company_home")).get(0).getChildRef();

            if (!this.personService.personExists(this.guaranteedAccessUserName))
            {
                this.personService.createPerson(new HashMap<>(Map.of(ContentModel.PROP_USERNAME, this.guaranteedAccessUserName,
                        ContentModel.PROP_FIRSTNAME, "Test", ContentModel.PROP_LASTNAME, "User")));
            }

            final int requiredTargetFolderCount = (int) Math.ceil(1.0f * count / maxChildrenPerFolder);
            LOGGER.info("{} nodes require {} target folders for sub-division", count, requiredTargetFolderCount);
            final List<NodeRef> targetFolders = this.generateTargetFolders(companyHome, requiredTargetFolderCount, maxChildrenPerFolder,
                    threads, wordsPerNode);

            final String nameSuffix = " of " + count;
            this.generateNodes(targetFolders, count, threads, i -> i + nameSuffix, this::generateRandomContentTypeAndProperties, t -> {
                // NO-OP
            }, wordsPerNode);

            return null;
        });

        res.setStatus(Status.STATUS_NO_CONTENT);
    }

    protected List<NodeRef> generateTargetFolders(final NodeRef rootNode, final int folderCount, final int maxChildrenPerFolder,
            final int threads, final int wordsPerNode)
    {
        final List<NodeRef> targetFolders = new ArrayList<>(folderCount);
        final List<NodeRef> parentFolders;
        boolean subDivide = folderCount > maxChildrenPerFolder;
        if (subDivide)
        {
            final int requiredParentFolderCount = (int) Math.ceil((1.0d * folderCount) / maxChildrenPerFolder);
            LOGGER.info("{} folders is more than the allowed limit of children - need to generate {} folders for further sub-divison",
                    folderCount, requiredParentFolderCount);
            parentFolders = this.generateTargetFolders(rootNode, requiredParentFolderCount, maxChildrenPerFolder, threads, wordsPerNode);
            if (parentFolders.isEmpty())
            {
                throw new AlfrescoRuntimeException("No folders were generated in recursive step");
            }
        }
        else
        {
            LOGGER.info("Generating {} top-level sub-division folders below root node {}", folderCount, rootNode);
            parentFolders = Collections.singletonList(rootNode);
        }

        final String nameSuffix = "of" + folderCount;
        final Pair<QName, Map<QName, Serializable>> typeAndProperties = new Pair<>(ContentModel.TYPE_FOLDER, Collections.emptyMap());

        this.generateNodes(parentFolders, folderCount, threads, i -> i + nameSuffix, () -> typeAndProperties, folders -> {
            if (subDivide)
            {
                AuthenticationUtil.runAsSystem(() -> {
                    for (NodeRef folder : folders)
                    {
                        boolean inherit = RN_JESUS.nextBoolean();
                        this.permissionService.setInheritParentPermissions(folder, inherit);

                        int assignments = 1 + RN_JESUS.nextInt(4);
                        for (int i = 0; i < assignments; i++)
                        {
                            this.permissionService.setPermission(folder, AUTHORITY_NAMES[RN_JESUS.nextInt(AUTHORITY_NAMES.length)],
                                    PERMISSION_NAMES[RN_JESUS.nextInt(PERMISSION_NAMES.length)], RN_JESUS.nextBoolean());
                        }
                        if (!inherit)
                        {
                            this.permissionService.setPermission(folder, this.guaranteedAccessUserName, PermissionService.CONSUMER, true);
                        }
                    }
                    return null;
                });
            }
            synchronized (targetFolders)
            {
                targetFolders.addAll(folders);
            }
        }, wordsPerNode);

        return targetFolders;
    }

    protected void generateNodes(final List<NodeRef> parentFolders, final int nodeCount, final int threads,
            final Function<Integer, String> nodeNameProvider,
            final Supplier<Pair<QName, Map<QName, Serializable>>> typeAndPropertiesProvider,
            final Consumer<List<NodeRef>> nodeBatchProcessor, final int wordsPerNode)
    {
        LOGGER.info("Starting actual generation step for {} nodes in {} parent folders with {} threads", nodeCount, parentFolders.size(),
                threads);
        final int batchSize = Math.min(50, Math.max(5, nodeCount / (parentFolders.size() * threads)));
        final int effectiveThreadCount = Math.min(threads, parentFolders.size());

        final BatchProcessWorkProvider<String> provider = new BatchProcessWorkProvider<>()
        {

            private int totalProvided = 0;

            /**
             * {@inheritDoc}
             */
            @Override
            public int getTotalEstimatedWorkSize()
            {
                return nodeCount;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Collection<String> getNextWork()
            {
                final Collection<String> nextWork = new ArrayList<>(batchSize);
                for (int idx = 0; idx < batchSize && this.totalProvided + idx < nodeCount; idx++)
                {
                    nextWork.add(nodeNameProvider.apply(Integer.valueOf(this.totalProvided + idx + 1)));
                }
                this.totalProvided += nextWork.size();
                return nextWork;
            }

        };
        final BatchProcessor<String> bp = new BatchProcessor<>("NodeGenerator", this.transactionService.getRetryingTransactionHelper(),
                provider, effectiveThreadCount, batchSize, null, LogFactory.getLog(GenerateNodes.class), 1000);

        final List<NodeRef> eligibleParents = new LinkedList<>(parentFolders);

        bp.process(new BatchProcessWorker<String>()
        {

            private final ThreadLocal<List<NodeRef>> createdNodes = new ThreadLocal<>();

            private final ThreadLocal<NodeRef> parent = new ThreadLocal<>();

            /**
             * {@inheritDoc}
             */
            @Override
            public String getIdentifier(final String entry)
            {
                return entry;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void beforeProcess() throws Throwable
            {
                AuthenticationUtil.pushAuthentication();
                AuthenticationUtil.setRunAsUserSystem();

                synchronized (eligibleParents)
                {
                    this.parent.set(eligibleParents.remove(0));
                }

                GenerateNodes.this.ruleService.disableRules();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void process(final String entry) throws Throwable
            {
                final Object flag = TransactionSupportUtil.getResource(GenerateNodes.class.getName());
                if (!Boolean.TRUE.equals(flag))
                {
                    this.createdNodes.set(new ArrayList<>(batchSize));
                    TransactionSupportUtil.bindResource(GenerateNodes.class.getName(), Boolean.TRUE);
                    GenerateNodes.this.behaviourFilter.disableBehaviour();
                }

                final Pair<QName, Map<QName, Serializable>> typeAndProperties = typeAndPropertiesProvider.get();

                final Map<QName, Serializable> properties = new HashMap<>();

                properties.putAll(typeAndProperties.getSecond());
                properties.put(ContentModel.PROP_NAME, entry);

                final NodeRef childRef = GenerateNodes.this.nodeService
                        .createNode(this.parent.get(), ContentModel.ASSOC_CONTAINS,
                                QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, entry), typeAndProperties.getFirst(), properties)
                        .getChildRef();

                if (wordsPerNode > 0)
                {
                    // Generate file content
                    if (typeAndProperties.getFirst().equals(ContentModel.TYPE_CONTENT))
                    {
                        final ContentWriter writer = GenerateNodes.this.contentService.getWriter(childRef, ContentModel.PROP_CONTENT, true);
                        writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
                        writer.setEncoding(StandardCharsets.UTF_8.name());
                        writer.putContent(generateFileContent(wordsPerNode));
                    }
                }

                this.createdNodes.get().add(childRef);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void afterProcess() throws Throwable
            {
                GenerateNodes.this.ruleService.enableRules();

                synchronized (eligibleParents)
                {
                    eligibleParents.add(this.parent.get());
                }
                this.parent.remove();

                AuthenticationUtil.popAuthentication();

                nodeBatchProcessor.accept(this.createdNodes.get());
            }

        }, true);

        LOGGER.info("Completed actual generation step");
    }

    protected Pair<QName, Map<QName, Serializable>> generateRandomContentTypeAndProperties()
    {
        final Map<QName, Serializable> properties = new HashMap<>();

        final Date created = generateDateTime();
        properties.put(ContentModel.PROP_CREATED, created);
        properties.put(ContentModel.PROP_MODIFIED, created);

        properties.put(INT_A, RN_JESUS.nextInt());
        properties.put(LONG_B, RN_JESUS.nextLong());
        properties.put(FLOAT_C, RN_JESUS.nextFloat());
        properties.put(DOUBLE_D, RN_JESUS.nextDouble());
        properties.put(DATE_E, generateDate());
        properties.put(DATE_F, generateDateTime());
        properties.put(NODE_G, new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString()));
        properties.put(QNAME_H, QName.createQName(NAMESPACES[RN_JESUS.nextInt(NAMESPACES.length)], lorem.getWords(2)));
        properties.put(LOV_STRING, LOVS[RN_JESUS.nextInt(LOVS.length)]);

        return new Pair<>(ContentModel.TYPE_CONTENT, properties);
    }

    protected static Date generateDateTime()
    {
        final long epochSeconds = MIN_TIME + (long) (RN_JESUS.nextDouble() * (MAX_TIME - MIN_TIME));
        final Date d = Date.from(LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC).toInstant(ZoneOffset.UTC));
        return d;
    }

    protected static Date generateDate()
    {
        final int epochDay = MIN_DAY + RN_JESUS.nextInt(MAX_DAY - MIN_DAY);
        final Date d = Date.from(LocalDate.ofEpochDay(epochDay).atStartOfDay().atOffset(ZoneOffset.UTC).toInstant());
        return d;
    }

    protected static String generateFileContent(final int length)
    {
        return lorem.getWords(0, length);
    }
}
