/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.spring;

import java.util.List;

import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * Instances of this bean definition registry post processor enhance definitions of {@link ProxyFactoryBean proxy factory beans} and similar
 * bean factories to avoid implicit initialisation when {@link DefaultListableBeanFactory#getBeanNamesForType(Class) looking up bean names
 * based on type} by enhancing bean definition attributes to include the {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE object type} as
 * {@link BeanDefinition#getAttribute(String) an attribute}.
 *
 * @author Axel Faust
 */
public class BeanFactoryPredictedTypeEnhancer implements BeanDefinitionRegistryPostProcessor
{

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry) throws BeansException
    {
        final String[] beanDefinitionNames = registry.getBeanDefinitionNames();
        for (final String beanDefinitionName : beanDefinitionNames)
        {
            final BeanDefinition beanDefinition = registry.getBeanDefinition(beanDefinitionName);
            final String beanClassName = beanDefinition.getBeanClassName();

            Object objectTypeAttribute = beanDefinition.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
            if (objectTypeAttribute == null && beanClassName != null)
            {
                if (beanClassName.startsWith("org.springframework") && beanClassName.endsWith("ProxyFactoryBean"))
                {
                    final PropertyValue proxyInterfaces = beanDefinition.getPropertyValues().getPropertyValue("proxyInterfaces");
                    if (proxyInterfaces != null)
                    {
                        objectTypeAttribute = this.deriveObjectTypeAttribute(proxyInterfaces);
                    }
                }
                else if (beanClassName.startsWith("org.alfresco") && beanClassName.endsWith("ProxyFactory"))
                {
                    final PropertyValue interfaces = beanDefinition.getPropertyValues().getPropertyValue("interfaces");
                    if (interfaces != null)
                    {
                        objectTypeAttribute = this.deriveObjectTypeAttribute(interfaces);
                    }
                }

                if (objectTypeAttribute != null)
                {
                    beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, objectTypeAttribute);
                }
            }
        }
    }

    private Object deriveObjectTypeAttribute(final PropertyValue interfaces)
    {
        Object objectTypeAttribute = null;

        final Object value = interfaces.getValue();
        Object element = null;
        if (value instanceof List<?> && ((List<?>) value).size() == 1)
        {
            element = ((List<?>) value).get(0);
        }
        else if (value instanceof String || value instanceof TypedStringValue)
        {
            element = value;
        }

        if (element instanceof String || element instanceof TypedStringValue)
        {
            final String clsName = element instanceof String ? (String) element : ((TypedStringValue) element).getValue();
            try
            {
                final Class<?> cls = Class.forName(clsName);
                objectTypeAttribute = cls;
            }
            catch (final ClassNotFoundException ignore)
            {
                // best-effort
            }
        }
        return objectTypeAttribute;
    }
}
