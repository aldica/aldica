/*
 * Copyright 2016 - 2019 Acosix GmbH
 */
package de.acosix.alfresco.ignite.common.plugin;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.apache.ignite.plugin.security.SecurityPermissionSet;
import org.apache.ignite.plugin.security.SecuritySubject;
import org.apache.ignite.plugin.security.SecuritySubjectType;

/**
 * @author Axel Faust
 */
public class SimpleSecuritySubject implements SecuritySubject
{

    private static final long serialVersionUID = 8577031703972371596L;

    protected final UUID id;

    protected final SecuritySubjectType type;

    protected final Object login;

    protected final InetSocketAddress adddres;

    protected final SecurityPermissionSet permissions;

    public SimpleSecuritySubject(final UUID id, final SecuritySubjectType type, final Object login, final InetSocketAddress adddres,
            final SecurityPermissionSet permissions)
    {
        this.id = id;
        this.type = type;
        this.login = login;
        this.adddres = adddres;
        this.permissions = permissions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID id()
    {
        return this.id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecuritySubjectType type()
    {
        return this.type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object login()
    {
        return this.login;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress address()
    {
        return this.adddres;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecurityPermissionSet permissions()
    {
        return this.permissions;
    }

}
