/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.policy;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.aldica.common.ignite.lifecycle.IgniteInstanceLifecycleAware;
import org.aldica.repo.ignite.cache.AsynchronouslyRefreshedCacheEventHandler;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.dictionary.CompiledModelsCache;
import org.alfresco.repo.dictionary.DictionaryDAO;
import org.alfresco.repo.dictionary.DictionaryDAOImpl;
import org.alfresco.repo.dictionary.DictionaryModelType;
import org.alfresco.repo.node.NodeServicePolicies.OnUpdatePropertiesPolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.tenant.TenantService;
import org.alfresco.repo.tenant.TenantUtil;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.PropertyCheck;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * The sole purpose of this policy / behaviour is to ensure that any activation change in Repository-managed models is properly reflected on
 * other servers within the same data grid. By default, the Alfresco implementation in {@link DictionaryModelType},
 * {@link DictionaryDAOImpl} and {@link CompiledModelsCache} prevent the {@link AsynchronouslyRefreshedCacheEventHandler} from receiving and
 * transmitting any request to refresh the compiled models on other servers. This policy fills that gap and forces refresh of the
 * dictionary.
 *
 * @author Axel Faust
 */
public class DictionaryModelActivationChange extends TransactionListenerAdapter
        implements InitializingBean, IgniteInstanceLifecycleAware, OnUpdatePropertiesPolicy
{

    private static final Logger LOGGER = LoggerFactory.getLogger(DictionaryModelActivationChange.class);

    private static final String TXN_KEY_PENDING_ACTIVATION = DictionaryModelActivationChange.class.getName() + "-pendingActivation";

    private static final String TXN_KEY_PENDING_DEACTIVATION = DictionaryModelActivationChange.class.getName() + "-pendingDeactivation";

    // copied from org.alfresco.repo.transaction.TransactionSupportUtil (not accessible)
    private static final int COMMIT_ORDER_CACHE = 4;

    protected DictionaryDAO dictionaryDAO;

    protected PolicyComponent policyComponent;

    protected TransactionService transactionService;

    protected TenantService tenantService;

    protected String instanceName;

    protected boolean instanceActive;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "dictionaryDAO", this.dictionaryDAO);
        PropertyCheck.mandatory(this, "policyComponent", this.policyComponent);
        PropertyCheck.mandatory(this, "transactionService", this.transactionService);
        PropertyCheck.mandatory(this, "tenantService", this.tenantService);
        PropertyCheck.mandatory(this, "instanceName", this.instanceName);

        this.policyComponent.bindClassBehaviour(OnUpdatePropertiesPolicy.QNAME, ContentModel.TYPE_DICTIONARY_MODEL,
                new JavaBehaviour(this, "onUpdateProperties", NotificationFrequency.EVERY_EVENT));

        LOGGER.debug("Bound dictionary model activation policy");
    }

    /**
     * @param dictionaryDAO
     *            the dictionaryDAO to set
     */
    public void setDictionaryDAO(final DictionaryDAO dictionaryDAO)
    {
        this.dictionaryDAO = dictionaryDAO;
    }

    /**
     * @param policyComponent
     *            the policyComponent to set
     */
    public void setPolicyComponent(final PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
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
     * @param tenantService
     *            the tenantService to set
     */
    public void setTenantService(final TenantService tenantService)
    {
        this.tenantService = tenantService;
    }

    /**
     * @param instanceName
     *            the instanceName to set
     */
    public void setInstanceName(final String instanceName)
    {
        this.instanceName = instanceName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceStartup(final String instanceName)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceStartup(final String instanceName)
    {
        if (EqualsHelper.nullSafeEquals(this.instanceName, instanceName))
        {
            Ignition.ignite(instanceName).message().localListen(DictionaryModelActivationChange.class.getName(), (UUID, tenant) -> {
                this.onRemoteDictionaryChange(String.valueOf(tenant));
                return true;
            });
            LOGGER.debug("Registered listener for remote dictionary model activation");
            this.instanceActive = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeInstanceShutdown(final String instanceName)
    {
        // NO-OP

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterInstanceShutdown(final String instanceName)
    {
        if (EqualsHelper.nullSafeEquals(this.instanceName, instanceName))
        {
            this.instanceActive = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdateProperties(final NodeRef nodeRef, final Map<QName, Serializable> before, final Map<QName, Serializable> after)
    {
        if (StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(nodeRef.getStoreRef()))
        {
            final Set<NodeRef> pendingActivation = TransactionalResourceHelper.getSet(TXN_KEY_PENDING_ACTIVATION);
            final Set<NodeRef> pendingDeactivation = TransactionalResourceHelper.getSet(TXN_KEY_PENDING_DEACTIVATION);

            final Serializable activeBefore = before.get(ContentModel.PROP_MODEL_ACTIVE);
            final Serializable activeAfter = after.get(ContentModel.PROP_MODEL_ACTIVE);

            if (Boolean.TRUE.equals(activeAfter) && !Boolean.TRUE.equals(activeBefore))
            {
                if (pendingActivation.isEmpty() && pendingDeactivation.isEmpty())
                {
                    TransactionSupportUtil.bindListener(this, COMMIT_ORDER_CACHE);
                }
                LOGGER.debug("Dictionary model {} flipped to active", nodeRef);
                pendingActivation.add(nodeRef);
            }
            else if (!Boolean.TRUE.equals(activeAfter) && Boolean.TRUE.equals(activeBefore))
            {
                if (pendingActivation.isEmpty() && pendingDeactivation.isEmpty())
                {
                    TransactionSupportUtil.bindListener(this, COMMIT_ORDER_CACHE);
                }
                LOGGER.debug("Dictionary model {} flipped to inactive", nodeRef);
                pendingDeactivation.add(nodeRef);
            }
            // else: before = null && after = false|null => irrelevant
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCommit()
    {
        if (this.instanceActive)
        {
            final Ignite ignite = Ignition.ignite(this.instanceName);
            final ClusterGroup remotes = ignite.cluster().forRemotes().forServers();
            if (!remotes.nodes().isEmpty())
            {
                final Set<NodeRef> pendingActivation = TransactionalResourceHelper.getSet(TXN_KEY_PENDING_ACTIVATION);
                final Set<NodeRef> pendingDeactivation = TransactionalResourceHelper.getSet(TXN_KEY_PENDING_DEACTIVATION);

                final Set<String> affectedTenants = new HashSet<>();

                final Function<? super NodeRef, ? extends String> nodeTenantMapper = node -> this.tenantService
                        .getDomain(node.getStoreRef().getIdentifier());
                affectedTenants.addAll(pendingActivation.stream().map(nodeTenantMapper).collect(Collectors.toSet()));
                affectedTenants.addAll(pendingDeactivation.stream().map(nodeTenantMapper).collect(Collectors.toSet()));

                LOGGER.debug("Sending message about dictionary model (de)activations in tenants {} to grid servers {}", affectedTenants,
                        remotes.nodes());

                affectedTenants.forEach(tenant -> ignite.message(remotes).send(DictionaryModelActivationChange.class.getName(), tenant));
            }
            else
            {
                LOGGER.debug("Not handling dictionary model (de)activations as there are no remote nodes in the grid");
            }
        }
        else
        {
            LOGGER.debug("Not handling dictionary model (de)activations as grid instance {} is not active", this.instanceName);
        }
    }

    /**
     * Reacts to a remote tenant dictionary invalidation message by reinitialising the dictionary of the local server instance.
     *
     * @param tenant
     *            the tenant to process
     */
    protected void onRemoteDictionaryChange(final String tenant)
    {
        LOGGER.debug("Received dictionary model invalidation message for tenant {}", tenant);

        this.transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
            TenantUtil.runAsSystemTenant(() -> {
                this.dictionaryDAO.init();

                LOGGER.debug("Reinitialised dictionary for tenant {}", tenant);
                return null;
            }, tenant);

            return null;
        }, true, true);
    }
}
