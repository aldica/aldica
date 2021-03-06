/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.plugin;

import java.util.Collection;
import java.util.Map;

import org.apache.ignite.plugin.security.SecurityPermission;
import org.apache.ignite.plugin.security.SecurityPermissionSet;

/**
 * @author Axel Faust
 */
public class NoopSecurityPermissionSet implements SecurityPermissionSet
{

    private static final long serialVersionUID = 709458146635250751L;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean defaultAllowAll()
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Collection<SecurityPermission>> taskPermissions()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Collection<SecurityPermission>> cachePermissions()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Collection<SecurityPermission>> servicePermissions()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<SecurityPermission> systemPermissions()
    {
        return null;
    }
}
