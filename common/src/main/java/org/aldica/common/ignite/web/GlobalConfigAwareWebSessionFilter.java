/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.web;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.ignite.cache.websession.WebSessionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.context.ApplicationContext;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.web.context.WebApplicationContext;

/**
 * Slightly adapted variant of the default Ignite web session filter for distributed web session handling - this class adds management
 * functionality to take configuration in the Spring context / global properties of an Alfresco web application into account before enabling
 * the base functionality. This allows users to disable web session replication via a simple property in {@code alfresco-global.properties}
 * or {@code share-global.properties}, as well as configure the cache to be used without having to provide a custom web fragment.
 *
 * @author Axel Faust
 */
public class GlobalConfigAwareWebSessionFilter extends WebSessionFilter
{

    private static final String PROP_WEB_SESSION_CACHE_ENABLED = "aldica.webSessionCache.enabled";

    private static final String PROP_WEB_SESSION_CACHE_INSTANCE_NAME = "aldica.webSessionCache.instanceName";

    private static final String PROP_WEB_SESSION_CACHE_CACHE_NAME = "aldica.webSessionCache.cacheName";

    private static final String PROP_WEB_SESSION_CACHE_RETRIES_ON_FAIL = "aldica.webSessionCache.retriesOnFailure";

    private static final String PROP_WEB_SESSION_CACHE_RETRIES_TIMEOUT = "aldica.webSessionCache.retriesTimeout";

    private static final String PROP_WEB_SESSION_CACHE_KEEP_BINARY = "aldica.webSessionCache.keepBinary";

    private static final Map<String, String> IGNITE_PARAMETER_TO_PROPERTY;
    static
    {
        final Map<String, String> mapping = new HashMap<>();
        mapping.put(WEB_SES_NAME_PARAM, PROP_WEB_SESSION_CACHE_INSTANCE_NAME);
        mapping.put(WEB_SES_CACHE_NAME_PARAM, PROP_WEB_SESSION_CACHE_CACHE_NAME);
        mapping.put(PROP_WEB_SESSION_CACHE_RETRIES_ON_FAIL, PROP_WEB_SESSION_CACHE_RETRIES_ON_FAIL);
        mapping.put(PROP_WEB_SESSION_CACHE_RETRIES_TIMEOUT, PROP_WEB_SESSION_CACHE_RETRIES_TIMEOUT);
        mapping.put(PROP_WEB_SESSION_CACHE_KEEP_BINARY, PROP_WEB_SESSION_CACHE_KEEP_BINARY);

        IGNITE_PARAMETER_TO_PROPERTY = Collections.unmodifiableMap(mapping);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigAwareWebSessionFilter.class);

    protected boolean enabled = false;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void init(final FilterConfig cfg) throws ServletException
    {
        final Object contextObj = cfg.getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
        if (contextObj instanceof ApplicationContext)
        {
            final ApplicationContext applicationContext = (ApplicationContext) contextObj;
            try
            {
                final Properties globalProperties = applicationContext.getBean("global-properties", Properties.class);

                // in case some property configurations refer to other properties via placeholders
                final PropertyPlaceholderHelper placeholderHelper = new PropertyPlaceholderHelper(
                        PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_PREFIX, PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_SUFFIX,
                        PlaceholderConfigurerSupport.DEFAULT_VALUE_SEPARATOR, true);

                String enabledProp = globalProperties.getProperty(PROP_WEB_SESSION_CACHE_ENABLED);
                if (enabledProp != null)
                {
                    enabledProp = placeholderHelper.replacePlaceholders(enabledProp, globalProperties);
                }
                this.enabled = Boolean.parseBoolean(enabledProp);

                if (this.enabled)
                {
                    super.init(new FilterConfig()
                    {

                        /**
                         *
                         * {@inheritDoc}
                         */
                        @Override
                        public String getFilterName()
                        {
                            return cfg.getFilterName();
                        }

                        @Override
                        public ServletContext getServletContext()
                        {
                            return cfg.getServletContext();
                        }

                        @Override
                        public String getInitParameter(final String name)
                        {
                            String parameterValue = cfg.getInitParameter(name);

                            // any parameter explicitly set will override value from global properties
                            if (parameterValue == null)
                            {
                                final String propertyName = IGNITE_PARAMETER_TO_PROPERTY.get(name);
                                if (propertyName != null)
                                {
                                    final String propertyValue = globalProperties.getProperty(propertyName);
                                    if (propertyValue != null)
                                    {
                                        parameterValue = placeholderHelper.replacePlaceholders(propertyValue, globalProperties);
                                    }
                                }
                            }

                            return parameterValue;
                        }

                        @SuppressWarnings("unchecked")
                        @Override
                        public Enumeration<String> getInitParameterNames()
                        {
                            return cfg.getInitParameterNames();
                        }

                    });
                }
            }
            catch (final NoSuchBeanDefinitionException nsbde)
            {
                LOGGER.warn("No global-properties instance found - cannot initialize web session filter", nsbde);
            }
        }
        else
        {
            LOGGER.warn("Application context has not been initialized - cannot initialize web session filter");
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void doFilter(final ServletRequest req, final ServletResponse res, final FilterChain chain) throws IOException, ServletException
    {
        if (this.enabled)
        {
            LOGGER.debug("Filter is enabled - delegating to default Ignite web session filter logic");
            super.doFilter(req, res, chain);
        }
        else
        {
            LOGGER.debug("Filter is disabled - letting chain continue filtering");
            chain.doFilter(req, res);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void destroy()
    {
        if (this.enabled)
        {
            super.destroy();
        }
    }
}
