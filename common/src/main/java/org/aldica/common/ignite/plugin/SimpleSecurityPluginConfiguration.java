/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.plugin;

import java.util.Collection;
import java.util.HashSet;

import org.apache.ignite.plugin.PluginConfiguration;
import org.apache.ignite.plugin.security.SecurityCredentials;

/**
 * @author Axel Faust
 */
public class SimpleSecurityPluginConfiguration implements PluginConfiguration
{

    protected boolean enabled;

    protected Collection<SecurityCredentials> allowedNodeCredentials;

    protected Collection<SecurityCredentials> allowedClientCredentials;

    protected String nodeTierAttributeKey;

    protected Collection<String> allowedNodeTierAttributeValues;

    /**
     * @return the enabled
     */
    public boolean isEnabled()
    {
        return this.enabled;
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
     * @return the allowedNodeCredentials
     */
    public Collection<SecurityCredentials> getAllowedNodeCredentials()
    {
        return asDecoupledCredentialsCollection(this.allowedNodeCredentials);
    }

    /**
     * @param allowedNodeCredentials
     *            the allowedNodeCredentials to set
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
     * @return the nodeTierAttributeKey
     */
    public String getNodeTierAttributeKey()
    {
        return this.nodeTierAttributeKey;
    }

    /**
     * @param nodeTierAttributeKey
     *            the nodeTierAttributeKey to set
     */
    public void setNodeTierAttributeKey(final String nodeTierAttributeKey)
    {
        this.nodeTierAttributeKey = nodeTierAttributeKey;
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
     *            the allowedNodeTierAttributeValues to set
     */
    public void setAllowedNodeTierAttributeValues(final Collection<String> allowedNodeTierAttributeValues)
    {
        this.allowedNodeTierAttributeValues = allowedNodeTierAttributeValues;
    }

    /**
     * @param allowedClientCredentials
     *            the allowedClientCredentials to set
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
            credentials.forEach(cred -> {
                if (cred != null)
                {
                    // data is mutable => need to copy
                    final SecurityCredentials cred2 = new SecurityCredentials();

                    cred2.setLogin(cred.getLogin());
                    cred2.setPassword(cred.getPassword());
                    cred2.setUserObject(cred.getUserObject());

                    result.add(cred2);
                }
            });
        }
        else
        {
            result = null;
        }
        return result;
    }

}
