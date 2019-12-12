/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.web.scripts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

import org.alfresco.repo.batch.BatchProcessWorkProvider;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.batch.BatchProcessor.BatchProcessWorker;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * @author Axel Faust
 */
public class CacheNodes extends AbstractWebScript implements InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheNodes.class);

    protected TransactionService transactionService;

    protected NodeService nodeService;

    protected SearchService searchService;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "transactionService", this.transactionService);
        PropertyCheck.mandatory(this, "nodeService", this.nodeService);
        PropertyCheck.mandatory(this, "searchService", this.searchService);
    }

    /**
     * @param transactionService
     *            the transactionService to set
     */
    public void setTransactionService(final TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    /**
     * @param nodeService
     *            the nodeService to set
     */
    public void setNodeService(final NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    /**
     * @param searchService
     *            the searchService to set
     */
    public void setSearchService(final SearchService searchService)
    {
        this.searchService = searchService;
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

        final String nameSuffix = " of " + count;
        this.loadNodes(count, threads, i -> i + nameSuffix);
    }

    protected void loadNodes(final int nodeCount, final int threads, final Function<Integer, String> nodeNameProvider)
    {
        final int batchSize = 25;

        final BatchProcessWorkProvider<String> provider = new BatchProcessWorkProvider<String>()
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
        final BatchProcessor<String> bp = new BatchProcessor<>("NodeCacher", this.transactionService.getRetryingTransactionHelper(),
                provider, threads, batchSize, null, LogFactory.getLog(CacheNodes.class), 1000);

        bp.process(new BatchProcessWorker<String>()
        {

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
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void process(final String entry) throws Throwable
            {
                final String query = "TYPE:\"cm:content\" AND =cm:name:\"" + entry + "\"";
                final ResultSet resultSet = CacheNodes.this.searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
                        SearchService.LANGUAGE_FTS_ALFRESCO, query);
                try
                {
                    for (final NodeRef node : resultSet.getNodeRefs())
                    {
                        LOGGER.trace("{}", CacheNodes.this.nodeService.getProperties(node));
                        LOGGER.trace("{}", CacheNodes.this.nodeService.getAspects(node));
                    }
                }
                finally
                {
                    resultSet.close();
                }
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void afterProcess() throws Throwable
            {
                AuthenticationUtil.popAuthentication();
            }

        }, true);
    }
}
