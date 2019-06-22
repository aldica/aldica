/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.binary;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import org.alfresco.util.PropertyCheck;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * Instances of this class use globally configured application properties to provide collections of serialisation relevant fields for
 * specific value classes.
 *
 * @author Axel Faust
 */
public class ConfigurableSerializationRelevantFieldsProvider implements Function<Class<?>, Collection<String>>, InitializingBean
{

    protected String basePropertyKey;

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
        PropertyCheck.mandatory(this, "propertyPrefix", this.basePropertyKey);
        PropertyCheck.mandatory(this, "propertiesSource", this.propertiesSource);
        PropertyCheck.mandatory(this, "propertiesSource", this.propertiesSource);
        PropertyCheck.mandatory(this, "placeholderPrefix", this.placeholderPrefix);
        PropertyCheck.mandatory(this, "placeholderSuffix", this.placeholderSuffix);
        PropertyCheck.mandatory(this, "valueSeparator", this.valueSeparator);

        this.placeholderHelper = new PropertyPlaceholderHelper(this.placeholderPrefix, this.placeholderSuffix, this.valueSeparator, true);
    }

    /**
     * @param basePropertyKey
     *            the basePropertyKey to set
     */
    public void setBasePropertyKey(final String basePropertyKey)
    {
        this.basePropertyKey = basePropertyKey;
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
    public Collection<String> apply(final Class<?> t)
    {
        final String className = t.getName();
        final String propertyName = this.basePropertyKey + '.' + className;

        String property = this.propertiesSource.getProperty(propertyName);
        if (property != null && !property.trim().isEmpty())
        {
            property = this.placeholderHelper.replacePlaceholders(property, this.propertiesSource);
        }

        final Set<String> fieldNames = property != null && !property.trim().isEmpty() ? new HashSet<>(Arrays.asList(property.split(",")))
                : Collections.emptySet();
        return fieldNames;
    }

}
