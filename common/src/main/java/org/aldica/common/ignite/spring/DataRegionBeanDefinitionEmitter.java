/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.spring;

import java.util.List;
import java.util.Properties;

import org.alfresco.util.PropertyCheck;
import org.apache.ignite.configuration.DataPageEvictionMode;
import org.apache.ignite.configuration.DataRegionConfiguration;
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
 *
 * @author Axel Faust
 */
public class DataRegionBeanDefinitionEmitter implements BeanDefinitionRegistryPostProcessor, InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(DataRegionBeanDefinitionEmitter.class);

    private static final String DATA_REGION_CONFIGURATIONS_PROPERTY_NAME = "dataRegionConfigurations";

    protected boolean enabled;

    protected String enabledPropertyKey;

    protected String propertyPrefix;

    protected String storageBeanDefinitionName;

    protected String dataRegionBeanDefinitionNamePrefix;

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
        PropertyCheck.mandatory(this, "storageBeanDefinitionName", this.storageBeanDefinitionName);
        PropertyCheck.mandatory(this, "dataRegionBeanDefinitionNamePrefix", this.dataRegionBeanDefinitionNamePrefix);
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
     * @param storageBeanDefinitionName
     *            the storageBeanDefinitionName to set
     */
    public void setStorageBeanDefinitionName(final String storageBeanDefinitionName)
    {
        this.storageBeanDefinitionName = storageBeanDefinitionName;
    }

    /**
     * @param dataRegionBeanDefinitionNamePrefix
     *            the dataRegionBeanDefinitionNamePrefix to set
     */
    public void setDataRegionBeanDefinitionNamePrefix(final String dataRegionBeanDefinitionNamePrefix)
    {
        this.dataRegionBeanDefinitionNamePrefix = dataRegionBeanDefinitionNamePrefix;
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
            if (registry.containsBeanDefinition(this.storageBeanDefinitionName))
            {
                final List<Object> values = this.lookupOrInitDataRegionsPropertyValue(registry);
                this.emitAndLinkDataRegions(registry, values);
            }
            else
            {
                LOGGER.warn("Bean registry does not contain a bean by name {} - unable to emit data region bean definitions",
                        this.storageBeanDefinitionName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected List<Object> lookupOrInitDataRegionsPropertyValue(final BeanDefinitionRegistry registry)
    {
        List<Object> values;
        final BeanDefinition storageBeanDefinition = registry.getBeanDefinition(this.storageBeanDefinitionName);
        final MutablePropertyValues storageBeanProperties = storageBeanDefinition.getPropertyValues();
        PropertyValue regionConfigurationsValue = storageBeanProperties.getPropertyValue(DATA_REGION_CONFIGURATIONS_PROPERTY_NAME);
        if (regionConfigurationsValue == null)
        {
            LOGGER.debug("No data regions on {} have been configured statically - initialising new property",
                    this.storageBeanDefinitionName);
            values = new ManagedList<>();
            regionConfigurationsValue = new PropertyValue(DATA_REGION_CONFIGURATIONS_PROPERTY_NAME, values);
            storageBeanProperties.addPropertyValue(regionConfigurationsValue);
        }
        else
        {
            final Object value = regionConfigurationsValue.getValue();
            if (value instanceof List<?>)
            {
                LOGGER.debug("A list of data regions has been configured statically on {} - going to add to the list",
                        this.storageBeanDefinitionName);
                values = (List<Object>) value;
            }
            else
            {
                LOGGER.warn(
                        "THe property {} on {} has been configured with an unexpected / incompatible value - overriding with new property",
                        DATA_REGION_CONFIGURATIONS_PROPERTY_NAME, this.storageBeanDefinitionName);
                values = new ManagedList<>();
                regionConfigurationsValue = new PropertyValue(DATA_REGION_CONFIGURATIONS_PROPERTY_NAME, values);
                storageBeanProperties.addPropertyValue(regionConfigurationsValue);
            }
        }
        return values;
    }

    protected void emitAndLinkDataRegions(final BeanDefinitionRegistry registry, final List<Object> storageDataRegions)
    {
        final String instanceName = this.placeholderHelper.replacePlaceholders(this.propertiesSource.getProperty(this.instanceNameProperty),
                this.propertiesSource);

        this.propertiesSource.stringPropertyNames().forEach(propertyName -> {
            if (propertyName.startsWith(this.propertyPrefix))
            {
                final String effPropertyName = propertyName.substring(this.propertyPrefix.length());
                if (effPropertyName.matches("^[a-zA-Z0-9]+\\.(initialSize|maxSize|swapPath)$"))
                {
                    final int sepIdx = effPropertyName.indexOf('.');
                    final String dataRegionName = effPropertyName.substring(0, sepIdx);
                    final String dataRegionPropertyName = effPropertyName.substring(sepIdx + 1);

                    final BeanDefinition dataRegionBeanDefinition = this.lookupOrCreateDataRegionBeanDefinition(registry,
                            storageDataRegions, instanceName, dataRegionName);

                    final String configValue = this.placeholderHelper.replacePlaceholders(this.propertiesSource.getProperty(propertyName),
                            this.propertiesSource);
                    LOGGER.debug("Setting data region property {} to {} on {} for instance {}", dataRegionPropertyName, configValue,
                            dataRegionName, instanceName);

                    dataRegionBeanDefinition.getPropertyValues().add(dataRegionPropertyName, configValue);
                }
            }
        });
    }

    protected BeanDefinition lookupOrCreateDataRegionBeanDefinition(final BeanDefinitionRegistry registry,
            final List<Object> storageDataRegions, final String instanceName, final String dataRegionName)
    {
        final String expectedBeanDefinitionName = this.dataRegionBeanDefinitionNamePrefix + dataRegionName;
        BeanDefinition regionBeanDefinition;
        if (registry.containsBeanDefinition(expectedBeanDefinitionName))
        {
            regionBeanDefinition = registry.getBeanDefinition(expectedBeanDefinitionName);
        }
        else
        {
            LOGGER.info("Emitting bean definition for data region {} in instance {}", dataRegionName, instanceName);
            regionBeanDefinition = new GenericBeanDefinition();
            registry.registerBeanDefinition(expectedBeanDefinitionName, regionBeanDefinition);

            // set static defaults
            regionBeanDefinition.setBeanClassName(DataRegionConfiguration.class.getName());
            final MutablePropertyValues propertyValues = regionBeanDefinition.getPropertyValues();
            final String effectiveDataRegionName = instanceName + ".region." + dataRegionName;
            propertyValues.add("name", effectiveDataRegionName);
            propertyValues.add("metricsEnabled", Boolean.TRUE);
            propertyValues.add("pageEvictionMode", DataPageEvictionMode.RANDOM_2_LRU);

            storageDataRegions.add(new RuntimeBeanReference(expectedBeanDefinitionName));
        }

        return regionBeanDefinition;
    }
}
