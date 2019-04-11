/*
 * Copyright 2016 - 2019 Acosix GmbH
 */
package de.acosix.alfresco.ignite.common.plugin;

import java.io.Serializable;
import java.util.UUID;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.processors.security.GridSecurityProcessor;
import org.apache.ignite.plugin.CachePluginContext;
import org.apache.ignite.plugin.CachePluginProvider;
import org.apache.ignite.plugin.ExtensionRegistry;
import org.apache.ignite.plugin.IgnitePlugin;
import org.apache.ignite.plugin.PluginConfiguration;
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

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String name()
    {
        return "SimpleSecurityPlugin (acosix-ignite)";
    }

    @Override
    public String version()
    {
        return "1.0.0.0";
    }

    @Override
    public String copyright()
    {
        return "Copyright 2016 - 2019 Acosix GmbH";
    }

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

        final PluginConfiguration[] pluginConfigurations = ctx.igniteConfiguration().getPluginConfigurations();
        if (cls.isAssignableFrom(GridSecurityProcessor.class) && pluginConfigurations != null)
        {
            boolean containsConfig = false;
            for (final PluginConfiguration config : pluginConfigurations)
            {
                containsConfig = containsConfig || config instanceof SimpleSecurityPluginConfiguration;
            }

            if (containsConfig)
            {
                component = cls.cast(new SimpleSecurityProcessor(ctx));
            }
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
    public CachePluginProvider<?> createCacheProvider(final CachePluginContext ctx)
    {
        // NO-OP
        return null;
    }

}
