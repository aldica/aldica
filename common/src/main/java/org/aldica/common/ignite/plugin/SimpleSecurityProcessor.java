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

import org.alfresco.util.ParameterCheck;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.security.GridSecurityProcessor;
import org.apache.ignite.internal.processors.security.SecurityContext;
import org.apache.ignite.lang.IgniteFuture;
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

/**
 * @author Axel Faust
 */
public class SimpleSecurityProcessor implements GridSecurityProcessor
{

    protected final SimpleSecurityPluginConfiguration configuration;

    protected final Map<UUID, SecuritySubject> authenticatedSubjects = new HashMap<>();

    public SimpleSecurityProcessor(final SimpleSecurityPluginConfiguration configuration)
    {
        ParameterCheck.mandatory("configuration", configuration);
        this.configuration = configuration;
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
                final Collection<SecurityCredentials> allowedClientCredentials = this.configuration.getAllowedClientCredentials();
                if (allowedClientCredentials == null)
                {
                    throw new SecurityException("Client access is not allowed");
                }

                if (allowedClientCredentials.contains(ctx.credentials()))
                {
                    final SecuritySubject securitySubject = new SimpleSecuritySubject(ctx.subjectId(), ctx.subjectType(),
                            ctx.credentials().getLogin(), ctx.address(), new NoopSecurityPermissionSet());
                    this.authenticatedSubjects.put(ctx.subjectId(), securitySubject);
                    securityContext = new NoopSecurityContext(securitySubject);
                }
                else
                {
                    securityContext = null;
                }
                break;
            case REMOTE_NODE:
                final Collection<SecurityCredentials> allowedNodeCredentials = this.configuration.getAllowedNodeCredentials();
                if (allowedNodeCredentials == null)
                {
                    throw new SecurityException("Node access is not allowed");
                }

                if (allowedNodeCredentials.contains(ctx.credentials()))
                {
                    final SecuritySubject securitySubject = new SimpleSecuritySubject(ctx.subjectId(), ctx.subjectType(),
                            ctx.credentials().getLogin(), ctx.address(), new NoopSecurityPermissionSet());
                    this.authenticatedSubjects.put(ctx.subjectId(), securitySubject);
                    securityContext = new NoopSecurityContext(securitySubject);
                }
                else
                {
                    securityContext = null;
                }
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
        // NO-OP
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

}
