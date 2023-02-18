/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.plugin;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.IgniteNodeAttributes;
import org.apache.ignite.internal.processors.security.GridSecurityProcessor;
import org.apache.ignite.internal.processors.security.SecurityContext;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.plugin.PluginContext;
import org.apache.ignite.plugin.security.AuthenticationContext;
import org.apache.ignite.plugin.security.SecurityCredentials;
import org.apache.ignite.plugin.security.SecurityException;
import org.apache.ignite.plugin.security.SecurityPermission;
import org.apache.ignite.plugin.security.SecuritySubject;
import org.apache.ignite.plugin.security.SecuritySubjectType;
import org.apache.ignite.spi.IgniteNodeValidationResult;
import org.apache.ignite.spi.discovery.DiscoveryDataBag;
import org.apache.ignite.spi.discovery.DiscoveryDataBag.GridDiscoveryData;
import org.apache.ignite.spi.discovery.DiscoveryDataBag.JoiningNodeDiscoveryData;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Axel Faust
 */
public class SimpleSecurityProcessor implements GridSecurityProcessor
{

    public static final String ATTR_SECURITY_TIER = IgniteNodeAttributes.ATTR_PREFIX + ".security.tier";

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSecurityProcessor.class);

    protected final PluginContext ctx;

    protected final SimpleSecurityPluginConfiguration configuration;

    protected final Map<UUID, SecuritySubject> authenticatedSubjects = new HashMap<>();

    /**
     * Initialises a new instance of this class with an explicit plugin configuration.
     *
     * @param ctx
     *     the plugin context
     * @param configuration
     *     the configuration of the plugin
     */
    public SimpleSecurityProcessor(final PluginContext ctx, final SimpleSecurityPluginConfiguration configuration)
    {
        if (configuration == null)
        {
            throw new IllegalStateException("No configuration for SimplePassphraseSecurityPlugin has been defined");
        }
        this.ctx = ctx;
        this.configuration = configuration;

        final Collection<String> allowedNodeTierAttributeValues = configuration.getAllowedNodeTierAttributeValues();

        final Collection<SecurityCredentials> allowedNodeCredentials = this.configuration.getAllowedNodeCredentials();
        final Collection<SecurityCredentials> allowedClientCredentials = this.configuration.getAllowedClientCredentials();

        if (allowedNodeCredentials == null || allowedNodeCredentials.isEmpty())
        {
            LOGGER.info("No allowed node credentials have been configured - no nodes will be allowed to connect");
        }

        if (allowedClientCredentials == null || allowedClientCredentials.isEmpty())
        {
            LOGGER.info("No allowed client credentials have been configured - no clients will be allowed to connect");
        }

        if (allowedNodeTierAttributeValues == null)
        {
            LOGGER.info("No allowed values for the node-tier attribute have been configured - any node is allowed to connect");
        }
        else if (allowedNodeTierAttributeValues.isEmpty())
        {
            LOGGER.warn(
                    "An empty list of allowed values for the node-tier attribute have been configured - no node will be allowed to connect");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecurityContext authenticateNode(final ClusterNode node, final SecurityCredentials cred) throws IgniteCheckedException
    {
        final AuthenticationContext ctxt = new AuthenticationContext();
        ctxt.credentials(cred);
        ctxt.nodeAttributes(node.attributes());
        ctxt.subjectType(node.isClient() ? SecuritySubjectType.REMOTE_CLIENT : SecuritySubjectType.REMOTE_NODE);
        ctxt.subjectId(node.id());
        ctxt.address(new InetSocketAddress(node.addresses().iterator().next(),
                node instanceof TcpDiscoveryNode ? ((TcpDiscoveryNode) node).discoveryPort() : 0));

        final SecurityContext securityContext = this.authenticate(ctxt);
        return securityContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isGlobalNodeAuthentication()
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecurityContext authenticate(final AuthenticationContext ctx) throws IgniteCheckedException
    {
        if (ctx.subjectType() == null)
        {
            throw new SecurityException("Unable to authenticate without subject type");
        }

        final SecurityContext securityContext;

        switch (ctx.subjectType())
        {
            case REMOTE_CLIENT:
                securityContext = this.validateCredentials(ctx, true);
                break;
            case REMOTE_NODE:
                boolean failedPrecondition = false;
                final Collection<String> nodeTierAttributeValues = this.configuration.getAllowedNodeTierAttributeValues();
                if (nodeTierAttributeValues != null)
                {
                    final Object tierValueO = ctx.nodeAttributes().get(ATTR_SECURITY_TIER);
                    if (!(tierValueO instanceof String))
                    {
                        LOGGER.info("Rejecting node {} ({}) due to incompatible node-tier attribute value {}", ctx.subjectId(),
                                ctx.address(), tierValueO);
                        failedPrecondition = true;
                    }
                    else if (!nodeTierAttributeValues.contains(tierValueO))
                    {
                        LOGGER.info("Rejecting node {} ({}) due to unallowed node-tier attribute value {}", ctx.subjectId(), ctx.address(),
                                tierValueO);
                        failedPrecondition = true;
                    }
                }

                securityContext = failedPrecondition ? null : this.validateCredentials(ctx, false);
                break;
            default:
                throw new SecurityException("Unsupported / unexpected subject type");
        }

        return securityContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<SecuritySubject> authenticatedSubjects() throws IgniteCheckedException
    {
        final ArrayList<SecuritySubject> authenticatedSubjects = new ArrayList<>(this.authenticatedSubjects.values());
        return authenticatedSubjects;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecuritySubject authenticatedSubject(final UUID subjId) throws IgniteCheckedException
    {
        return this.authenticatedSubjects.get(subjId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws IgniteCheckedException
    {
        final IgniteConfiguration igniteConfiguration = this.ctx.igniteConfiguration();

        // need to decouple since it may be an unmodifiable / empty map (see
        // IgniteNamedInstance#initializeConfiguration(IgniteConfiguration) )
        @SuppressWarnings("unchecked")
        Map<String, Object> userAttributes = (Map<String, Object>) igniteConfiguration.getUserAttributes();
        userAttributes = userAttributes != null ? new HashMap<>(userAttributes) : new HashMap<>();
        igniteConfiguration.setUserAttributes(userAttributes);

        final SecurityCredentials credentials = this.configuration.getCredentials();
        if (credentials != null)
        {
            userAttributes.put(IgniteNodeAttributes.ATTR_SECURITY_CREDENTIALS, credentials);
        }

        final String tierAttributeValue = this.configuration.getTierAttributeValue();
        if (tierAttributeValue != null)
        {
            userAttributes.put(ATTR_SECURITY_TIER, tierAttributeValue);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(final boolean cancel) throws IgniteCheckedException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onKernalStart(final boolean active) throws IgniteCheckedException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onKernalStop(final boolean cancel)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collectJoiningNodeData(final DiscoveryDataBag dataBag)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collectGridNodeData(final DiscoveryDataBag dataBag)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onGridDataReceived(final GridDiscoveryData data)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onJoiningNodeDataReceived(final JoiningNodeDiscoveryData data)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printMemoryStats()
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IgniteNodeValidationResult validateNode(final ClusterNode node)
    {
        // NO-OP
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IgniteNodeValidationResult validateNode(final ClusterNode node, final JoiningNodeDiscoveryData discoData)
    {
        // NO-OP
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DiscoveryDataExchangeType discoveryDataType()
    {
        return DiscoveryDataExchangeType.PLUGIN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDisconnected(final IgniteFuture<?> reconnectFut) throws IgniteCheckedException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IgniteInternalFuture<?> onReconnected(final boolean clusterRestarted) throws IgniteCheckedException
    {
        // NO-OP
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void authorize(final String name, final SecurityPermission perm, final SecurityContext securityCtx) throws SecurityException
    {
        // NO-OP - we don't provide any kind of ACL checking
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSessionExpired(final UUID subjId)
    {
        this.authenticatedSubjects.remove(subjId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enabled()
    {
        return true;
    }

    protected SecurityContext validateCredentials(final AuthenticationContext ctx, final boolean asClient)
    {
        final SecurityContext securityContext;
        final Collection<SecurityCredentials> allowedCredentials = asClient ? this.configuration.getAllowedClientCredentials()
                : this.configuration.getAllowedNodeCredentials();
        final SecurityCredentials providedCredentials = ctx.credentials();

        if (allowedCredentials != null && !allowedCredentials.isEmpty())
        {
            if (providedCredentials != null && allowedCredentials.contains(providedCredentials))
            {
                LOGGER.debug("Accepting {} ({}) as provided credentials match", ctx.subjectId(), ctx.address());
                final SecuritySubject securitySubject = new SimpleSecuritySubject(ctx.subjectId(), ctx.subjectType(),
                        providedCredentials.getLogin(), ctx.address(), new NoopSecurityPermissionSet());
                this.authenticatedSubjects.put(ctx.subjectId(), securitySubject);
                securityContext = new NoopSecurityContext(securitySubject);
            }
            else
            {
                LOGGER.info("Rejecting {} ({}) as provided credentials do not match", ctx.subjectId(), ctx.address());
                securityContext = null;
            }
        }
        else
        {
            LOGGER.info("Rejecting {} ({}) as no allowed credentials have been configured for type of subject", ctx.subjectId(),
                    ctx.address());
            securityContext = null;
        }
        return securityContext;
    }

}
