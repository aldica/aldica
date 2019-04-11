/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.common.discovery;

import java.util.HashMap;
import java.util.Map;

import org.apache.ignite.internal.IgniteNodeAttributes;
import org.apache.ignite.plugin.security.SecurityCredentials;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;

/**
 * @author Axel Faust
 */
public class CredentialsAwareTcpDiscoverySpi extends TcpDiscoverySpi
{

    protected SecurityCredentials credentials;

    /**
     * @return the credentials
     */
    public SecurityCredentials getCredentials()
    {
        return this.credentials;
    }

    /**
     * @param credentials
     *            the credentials to set
     */
    public void setCredentials(final SecurityCredentials credentials)
    {
        this.credentials = credentials;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getNodeAttributes()
    {
        Map<String, Object> nodeAttributes = super.getNodeAttributes();
        if (nodeAttributes == null || nodeAttributes.isEmpty())
        {
            nodeAttributes = new HashMap<>();
        }

        if (this.credentials != null)
        {
            nodeAttributes.put(IgniteNodeAttributes.ATTR_SECURITY_CREDENTIALS, this.credentials);
        }

        return nodeAttributes;
    }
}
