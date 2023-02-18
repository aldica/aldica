/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.plugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import org.apache.ignite.plugin.PluginConfiguration;
import org.apache.ignite.plugin.security.SecurityCredentials;

/**
 * @author Axel Faust
 */
public class SimpleSecurityPluginConfiguration implements PluginConfiguration
{

    protected SecurityCredentials credentials;

    protected Collection<SecurityCredentials> allowedNodeCredentials;

    protected Collection<SecurityCredentials> allowedClientCredentials;

    protected String tierAttributeValue;

    protected Collection<String> allowedNodeTierAttributeValues;

    /**
     * @return the credentials
     */
    public SecurityCredentials getCredentials()
    {
        return this.credentials != null ? asDecoupledCredentials(this.credentials) : null;
    }

    /**
     * @param credentials
     *     the credentials to set
     */
    public void setCredentials(final SecurityCredentials credentials)
    {
        this.credentials = credentials;
    }

    /**
     * @return the allowedNodeCredentials
     */
    public Collection<SecurityCredentials> getAllowedNodeCredentials()
    {
        return asDecoupledCredentialsCollection(this.allowedNodeCredentials);
    }

    /**
     * @param allowedNodeCredentials
     *     the allowedNodeCredentials to set
     */
    public void setAllowedNodeCredentials(final Collection<SecurityCredentials> allowedNodeCredentials)
    {
        this.allowedNodeCredentials = asDecoupledCredentialsCollection(allowedNodeCredentials);
    }

    /**
     * @return the allowedClientCredentials
     */
    public Collection<SecurityCredentials> getAllowedClientCredentials()
    {
        return asDecoupledCredentialsCollection(this.allowedClientCredentials);
    }

    /**
     * @return the tierAttributeValue
     */
    public String getTierAttributeValue()
    {
        return this.tierAttributeValue;
    }

    /**
     * @param tierAttributeValue
     *     the tierAttributeValue to set
     */
    public void setTierAttributeValue(final String tierAttributeValue)
    {
        this.tierAttributeValue = tierAttributeValue;
    }

    /**
     * @return the allowedNodeTierAttributeValues
     */
    public Collection<String> getAllowedNodeTierAttributeValues()
    {
        return this.allowedNodeTierAttributeValues != null ? new HashSet<>(this.allowedNodeTierAttributeValues) : null;
    }

    /**
     * @param allowedNodeTierAttributeValues
     *     the allowedNodeTierAttributeValues to set
     */
    public void setAllowedNodeTierAttributeValues(final Collection<String> allowedNodeTierAttributeValues)
    {
        this.allowedNodeTierAttributeValues = allowedNodeTierAttributeValues;
    }

    /**
     * @param allowedClientCredentials
     *     the allowedClientCredentials to set
     */
    public void setAllowedClientCredentials(final Collection<SecurityCredentials> allowedClientCredentials)
    {
        this.allowedClientCredentials = asDecoupledCredentialsCollection(allowedClientCredentials);
    }

    protected static Collection<SecurityCredentials> asDecoupledCredentialsCollection(final Collection<SecurityCredentials> credentials)
    {
        final Collection<SecurityCredentials> result;
        if (credentials != null)
        {
            result = new HashSet<>(credentials.size());
            credentials.stream().filter(Objects::nonNull).map(SimpleSecurityPluginConfiguration::asDecoupledCredentials)
                    .forEach(result::add);
        }
        else
        {
            result = null;
        }
        return result;
    }

    protected static SecurityCredentials asDecoupledCredentials(final SecurityCredentials credentials)
    {
        final SecurityCredentials cred2 = new SecurityCredentials();

        cred2.setLogin(credentials.getLogin());
        cred2.setPassword(credentials.getPassword());
        cred2.setUserObject(credentials.getUserObject());

        return cred2;
    }
}
