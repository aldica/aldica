/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * This factory bean creates a Micrometer {@link MeterRegistry} for use in exporting Ignite metrics.
 *
 * @author Axel Faust
 */
public class MeterRegistryFactoryBean implements FactoryBean<MeterRegistry>, ApplicationContextAware
{

    private static final Logger LOGGER = LoggerFactory.getLogger(MeterRegistryFactoryBean.class);

    private ApplicationContext applicationContext;

    private String meterRegistryBeanNames;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException
    {
        this.applicationContext = applicationContext;
    }

    /**
     * @param meterRegistryBeanNames
     *     the meterRegistryBeanNames to set
     */
    public void setMeterRegistryBeanNames(final String meterRegistryBeanNames)
    {
        this.meterRegistryBeanNames = meterRegistryBeanNames;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MeterRegistry getObject() throws Exception
    {
        final List<MeterRegistry> meterRegistries = new LinkedList<>();

        if (this.meterRegistryBeanNames != null && !this.meterRegistryBeanNames.isEmpty())
        {
            final String[] names = this.meterRegistryBeanNames.split(",");
            for (final String name : names)
            {
                LOGGER.debug("Trying to look up meter registry bean {}", name);
                if (this.applicationContext.containsBeanDefinition(name))
                {
                    try
                    {
                        final MeterRegistry bean = this.applicationContext.getBean(name, MeterRegistry.class);
                        meterRegistries.add(bean);
                    }
                    catch (final BeansException be)
                    {
                        LOGGER.error("Failed to look up meter registry named {}", name, be);
                    }
                }
                else
                {
                    LOGGER.warn("Application context does not contain a meter registry bean named {}", name);
                }
            }
        }

        if (this.applicationContext.containsBeanDefinition("metricsController"))
        {
            LOGGER.debug("Trying to obtain Alfresco Enterprise Prometheus meter registry from metricsController bean");
            try
            {
                final Object bean = this.applicationContext.getBean("metricsController");
                final MeterRegistry meterRegistry = this.getMeterRegistryFromMetricsController(bean);
                if (meterRegistry != null)
                {
                    meterRegistries.add(meterRegistry);
                }
            }
            catch (final BeansException be)
            {
                LOGGER.warn("Failed to obtain Alfresco Enterprise metricsController", be);
            }
        }

        final MeterRegistry registry;
        if (meterRegistries.isEmpty())
        {
            LOGGER.debug("Creating simple meter registry");
            registry = new SimpleMeterRegistry();
        }
        else if (meterRegistries.size() == 1)
        {
            registry = meterRegistries.get(0);
            LOGGER.debug("Returning single meter registry {}", registry);
        }
        else
        {
            LOGGER.debug("Creating composite meter registry for {} meter registries", meterRegistries.size());
            registry = new CompositeMeterRegistry(Clock.SYSTEM, meterRegistries);
        }

        return registry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> getObjectType()
    {
        return MeterRegistry.class;
    }

    private MeterRegistry getMeterRegistryFromMetricsController(final Object metricsController)
    {
        try
        {
            final Method method = metricsController.getClass().getMethod("getRegistry");
            final Object o = method.invoke(metricsController);
            if (o instanceof MeterRegistry)
            {
                return (MeterRegistry) o;
            }
            LOGGER.warn("Alfresco Enterprise metricsController.getRegistry() did not yield a MeterRegistry");
            return null;
        }
        catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e)
        {
            LOGGER.warn("Unexpected structure of Alfresco Enterprise metricsController bean", e);
            return null;
        }
    }
}
