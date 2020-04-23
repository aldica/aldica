/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.spring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

import org.alfresco.util.PropertyCheck;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * Instances of this class dynamically emit {@link BinaryTypeConfiguration} bean definitions based on global configuration properties to
 * allow for easy extensibility of Ignite serialisation / binary handling configuration without requiring complex XML configuration.
 *
 * @author Axel Faust
 */
public class BinaryTypeConfigurationBeanDefinitionEmitter implements BeanDefinitionRegistryPostProcessor, InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryTypeConfigurationBeanDefinitionEmitter.class);

    private static final String TYPE_CONFIGURATIONS_PROPERTY_NAME = "typeConfigurations";

    protected boolean enabled;

    protected String enabledPropertyKey;

    protected String propertyPrefix;

    protected String binaryConfigurationBeanDefinitionName;

    protected String binaryTypeConfigurationBeanDefinitionNamePrefix;

    protected String instanceNameProperty;

    protected Properties propertiesSource;

    protected String placeholderPrefix = PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_PREFIX;

    protected String placeholderSuffix = PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_SUFFIX;

    protected String valueSeparator = PlaceholderConfigurerSupport.DEFAULT_VALUE_SEPARATOR;

    protected PropertyPlaceholderHelper placeholderHelper;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "propertyPrefix", this.propertyPrefix);
        PropertyCheck.mandatory(this, "binaryConfigurationBeanDefinitionName", this.binaryConfigurationBeanDefinitionName);
        PropertyCheck.mandatory(this, "binaryTypeDefinitionBeanDefinitionNamePrefix", this.binaryTypeConfigurationBeanDefinitionNamePrefix);
        PropertyCheck.mandatory(this, "instanceNameProperty", this.instanceNameProperty);
        PropertyCheck.mandatory(this, "propertiesSource", this.propertiesSource);
        PropertyCheck.mandatory(this, "placeholderPrefix", this.placeholderPrefix);
        PropertyCheck.mandatory(this, "placeholderSuffix", this.placeholderSuffix);
        PropertyCheck.mandatory(this, "valueSeparator", this.valueSeparator);

        this.placeholderHelper = new PropertyPlaceholderHelper(this.placeholderPrefix, this.placeholderSuffix, this.valueSeparator, true);
    }

    /**
     * @param enabled
     *            the enabled to set
     */
    public void setEnabled(final boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * @param enabledPropertyKey
     *            the enabledPropertyKey to set
     */
    public void setEnabledPropertyKey(final String enabledPropertyKey)
    {
        this.enabledPropertyKey = enabledPropertyKey;
    }

    /**
     * @param propertyPrefix
     *            the propertyPrefix to set
     */
    public void setPropertyPrefix(final String propertyPrefix)
    {
        this.propertyPrefix = propertyPrefix;
    }

    /**
     * @param binaryConfigurationBeanDefinitionName
     *            the binaryConfigurationBeanDefinitionName to set
     */
    public void setBinaryConfigurationBeanDefinitionName(final String binaryConfigurationBeanDefinitionName)
    {
        this.binaryConfigurationBeanDefinitionName = binaryConfigurationBeanDefinitionName;
    }

    /**
     * @param binaryTypeConfigurationBeanDefinitionNamePrefix
     *            the binaryTypeConfigurationBeanDefinitionNamePrefix to set
     */
    public void setBinaryTypeConfigurationBeanDefinitionNamePrefix(final String binaryTypeConfigurationBeanDefinitionNamePrefix)
    {
        this.binaryTypeConfigurationBeanDefinitionNamePrefix = binaryTypeConfigurationBeanDefinitionNamePrefix;
    }

    /**
     * @param instanceNameProperty
     *            the instanceNameProperty to set
     */
    public void setInstanceNameProperty(final String instanceNameProperty)
    {
        this.instanceNameProperty = instanceNameProperty;
    }

    /**
     * @param propertiesSource
     *            the propertiesSource to set
     */
    public void setPropertiesSource(final Properties propertiesSource)
    {
        this.propertiesSource = propertiesSource;
    }

    /**
     * @param placeholderPrefix
     *            the placeholderPrefix to set
     */
    public void setPlaceholderPrefix(final String placeholderPrefix)
    {
        this.placeholderPrefix = placeholderPrefix;
    }

    /**
     * @param placeholderSuffix
     *            the placeholderSuffix to set
     */
    public void setPlaceholderSuffix(final String placeholderSuffix)
    {
        this.placeholderSuffix = placeholderSuffix;
    }

    /**
     * @param valueSeparator
     *            the valueSeparator to set
     */
    public void setValueSeparator(final String valueSeparator)
    {
        this.valueSeparator = valueSeparator;
    }

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
        boolean enabled = this.enabled;
        if (this.enabledPropertyKey != null && !this.enabledPropertyKey.isEmpty())
        {
            final String property = this.propertiesSource.getProperty(this.enabledPropertyKey);
            enabled = (property != null ? Boolean.valueOf(property) : Boolean.FALSE);
        }

        if (enabled)
        {
            if (registry.containsBeanDefinition(this.binaryConfigurationBeanDefinitionName))
            {
                final List<Object> values = this.lookupOrInitTypeConfigurationsPropertyValue(registry);
                this.emitAndTypeConfigurations(registry, values);
            }
            else
            {
                LOGGER.warn("Bean registry does not contain a bean by name {} - unable to emit data region bean definitions",
                        this.binaryConfigurationBeanDefinitionName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected List<Object> lookupOrInitTypeConfigurationsPropertyValue(final BeanDefinitionRegistry registry)
    {
        List<Object> values;
        final BeanDefinition binaryConfigurationBeanDefinition = registry.getBeanDefinition(this.binaryConfigurationBeanDefinitionName);
        final MutablePropertyValues binaryConfigurationBeanProperties = binaryConfigurationBeanDefinition.getPropertyValues();
        PropertyValue typeConfigurationsValue = binaryConfigurationBeanProperties.getPropertyValue(TYPE_CONFIGURATIONS_PROPERTY_NAME);
        if (typeConfigurationsValue == null)
        {
            LOGGER.debug("No binary type configurations on {} have been configured statically - initialising new property",
                    this.binaryConfigurationBeanDefinitionName);
            values = new ManagedList<>();
            typeConfigurationsValue = new PropertyValue(TYPE_CONFIGURATIONS_PROPERTY_NAME, values);
            binaryConfigurationBeanProperties.addPropertyValue(typeConfigurationsValue);
        }
        else
        {
            final Object value = typeConfigurationsValue.getValue();
            if (value instanceof List<?>)
            {
                LOGGER.debug("A list of binary type configurations has been configured statically on {} - going to add to the list",
                        this.binaryConfigurationBeanDefinitionName);
                values = (List<Object>) value;
            }
            else
            {
                LOGGER.warn(
                        "THe property {} on {} has been configured with an unexpected / incompatible value - overriding with new property",
                        TYPE_CONFIGURATIONS_PROPERTY_NAME, this.binaryConfigurationBeanDefinitionName);
                values = new ManagedList<>();
                typeConfigurationsValue = new PropertyValue(TYPE_CONFIGURATIONS_PROPERTY_NAME, values);
                binaryConfigurationBeanProperties.addPropertyValue(typeConfigurationsValue);
            }
        }
        return values;
    }

    protected void emitAndTypeConfigurations(final BeanDefinitionRegistry registry, final List<Object> binaryTypeConfigurations)
    {
        final String instanceName = this.placeholderHelper.replacePlaceholders(this.propertiesSource.getProperty(this.instanceNameProperty),
                this.propertiesSource);

        final Map<String, Boolean> cachedEnabled = new HashMap<>();
        final Predicate<String> isEnabled = typeName -> Boolean.TRUE.equals(cachedEnabled.computeIfAbsent(typeName, typeNameI -> {
            final String key = this.propertyPrefix + typeName + ".enabled";
            String value = this.propertiesSource.getProperty(key, "true");
            value = this.placeholderHelper.replacePlaceholders(value, this.propertiesSource);
            final Boolean enabled = Boolean.valueOf(value);
            return enabled;
        }));

        this.propertiesSource.stringPropertyNames().forEach(propertyName -> {
            if (propertyName.startsWith(this.propertyPrefix))
            {
                final String effPropertyName = propertyName.substring(this.propertyPrefix.length());
                if (effPropertyName.matches("^([^\\.]+(\\.[^\\\\.]+)*)\\.(serializer|idMapper|nameMapper)$"))
                {
                    final int sepIdx = effPropertyName.lastIndexOf('.');
                    final String typeName = effPropertyName.substring(0, sepIdx);
                    if (isEnabled.test(typeName))
                    {
                        final String typeConfigurationPropertyName = effPropertyName.substring(sepIdx + 1);

                        final BeanDefinition binaryTypeConfigurationBeanDefinition = this
                                .lookupOrCreateBinaryTypeConfigurationBeanDefinition(registry, binaryTypeConfigurations, instanceName,
                                        typeName);

                        final String beanName = this.placeholderHelper.replacePlaceholders(this.propertiesSource.getProperty(propertyName),
                                this.propertiesSource);
                        LOGGER.debug(
                                "Setting binary type configuration property {} to reference bean {} for type {} on instance {}",
                                typeConfigurationPropertyName, beanName, typeName, instanceName);

                        binaryTypeConfigurationBeanDefinition.getPropertyValues().add(typeConfigurationPropertyName,
                                new RuntimeBeanReference(beanName));
                    }
                }
            }
        });
    }

    protected BeanDefinition lookupOrCreateBinaryTypeConfigurationBeanDefinition(final BeanDefinitionRegistry registry,
            final List<Object> typeConfigurations, final String instanceName, final String typeName)
    {
        final String expectedBeanDefinitionName = this.binaryTypeConfigurationBeanDefinitionNamePrefix + typeName;
        BeanDefinition binaryTypeConfigurationBeanDefinition;
        if (registry.containsBeanDefinition(expectedBeanDefinitionName))
        {
            binaryTypeConfigurationBeanDefinition = registry.getBeanDefinition(expectedBeanDefinitionName);
        }
        else
        {
            LOGGER.info("Emitting bean definition for binary type configuration affecting type {} in instance {}", typeName, instanceName);
            binaryTypeConfigurationBeanDefinition = new GenericBeanDefinition();
            registry.registerBeanDefinition(expectedBeanDefinitionName, binaryTypeConfigurationBeanDefinition);

            // set static defaults
            binaryTypeConfigurationBeanDefinition.setBeanClassName(BinaryTypeConfiguration.class.getName());
            binaryTypeConfigurationBeanDefinition.getPropertyValues().add("typeName", typeName);
            typeConfigurations.add(new RuntimeBeanReference(expectedBeanDefinitionName));
        }

        return binaryTypeConfigurationBeanDefinition;
    }
}
