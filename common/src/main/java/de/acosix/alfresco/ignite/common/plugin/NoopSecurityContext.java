/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.common.plugin;

import java.io.Serializable;

import org.apache.ignite.internal.processors.security.SecurityContext;
import org.apache.ignite.plugin.security.SecurityPermission;
import org.apache.ignite.plugin.security.SecuritySubject;

/**
 * @author Axel Faust
 */
public class NoopSecurityContext implements SecurityContext, Serializable
{

    private static final long serialVersionUID = 1794411284528886140L;

    protected final SecuritySubject subject;

    public NoopSecurityContext(final SecuritySubject subject)
    {
        this.subject = subject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecuritySubject subject()
    {
        return this.subject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean taskOperationAllowed(final String taskClsName, final SecurityPermission perm)
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean cacheOperationAllowed(final String cacheName, final SecurityPermission perm)
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean systemOperationAllowed(final SecurityPermission perm)
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean serviceOperationAllowed(final String srvcName, final SecurityPermission perm)
    {
        return true;
    }

}
