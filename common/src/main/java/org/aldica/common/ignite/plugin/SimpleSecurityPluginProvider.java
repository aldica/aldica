/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.plugin;

import java.io.Serializable;
import java.util.UUID;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.processors.security.GridSecurityProcessor;
import org.apache.ignite.plugin.CachePluginContext;
import org.apache.ignite.plugin.CachePluginProvider;
import org.apache.ignite.plugin.ExtensionRegistry;
import org.apache.ignite.plugin.IgnitePlugin;
import org.apache.ignite.plugin.PluginContext;
import org.apache.ignite.plugin.PluginProvider;
import org.apache.ignite.plugin.PluginValidationException;

/**
 * This plugin provider is the entry point for a simple security model to validate nodes joining the data grid against a configured
 * collection of allowed credentials.
 *
 * @author Axel Faust
 */
public class SimpleSecurityPluginProvider implements PluginProvider<SimpleSecurityPluginConfiguration>
{

    protected SimpleSecurityPluginConfiguration configuration;

    /**
     * @param configuration
     *            the configuration to set
     */
    public void setConfiguration(final SimpleSecurityPluginConfiguration configuration)
    {
        this.configuration = configuration;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String name()
    {
        return "SimpleSecurityPlugin (org.aldica:aldica-common-ignite)";
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String version()
    {
        return "1.0.0.0";
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String copyright()
    {
        return "Copyright 2019 Acosix GmbH, Copyright 2019 MAGENTA ApS";
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public <T extends IgnitePlugin> T plugin()
    {
        // forced upon us by API
        @SuppressWarnings("unchecked")
        final T plugin = (T) new SimpleSecurityPlugin();
        return plugin;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void initExtensions(final PluginContext ctx, final ExtensionRegistry registry)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T createComponent(final PluginContext ctx, final Class<T> cls)
    {
        T component = null;
        if (cls.isAssignableFrom(GridSecurityProcessor.class) && this.configuration != null)
        {
            component = cls.cast(new SimpleSecurityProcessor(this.configuration));
        }
        return component;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(final PluginContext ctx) throws IgniteCheckedException
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
    public void onIgniteStart() throws IgniteCheckedException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onIgniteStop(final boolean cancel)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable provideDiscoveryData(final UUID nodeId)
    {
        // NO-OP
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveDiscoveryData(final UUID nodeId, final Serializable data)
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateNewNode(final ClusterNode node) throws PluginValidationException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("rawtypes") // forced by interface
    public CachePluginProvider<?> createCacheProvider(final CachePluginContext ctx)
    {
        // NO-OP
        return null;
    }

}
