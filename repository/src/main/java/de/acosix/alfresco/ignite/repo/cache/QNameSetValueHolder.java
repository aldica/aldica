/*
 * Copyright 2016 - 2019 Acosix GmbH
 */
package de.acosix.alfresco.ignite.repo.cache;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.alfresco.service.namespace.QName;

/**
 * @author Axel Faust
 */
public class QNameSetValueHolder implements Serializable
{

    private static final long serialVersionUID = 1L;

    protected final long[] qnameIds;

    protected transient Set<QName> qnames;

    public QNameSetValueHolder(final long[] qnameIds, final Set<QName> qnames)
    {
        this.qnameIds = new long[qnameIds.length];
        System.arraycopy(qnameIds, 0, this.qnameIds, 0, qnameIds.length);
        this.qnames = new HashSet<>(qnames);
    }

    protected QNameSetValueHolder()
    {
        this.qnameIds = new long[0];
    }

    /**
     * @return the qnameIds
     */
    public long[] getQnameIds()
    {
        final long[] qnameIds = new long[this.qnameIds.length];
        System.arraycopy(this.qnameIds, 0, qnameIds, 0, this.qnameIds.length);
        return qnameIds;
    }

    /**
     * @return the qnames
     */
    public Set<QName> getQnames()
    {
        return this.qnames != null ? Collections.unmodifiableSet(this.qnames) : null;
    }

    /**
     * @param qnames
     *            the qnames to set
     */
    public void setQnames(final Set<QName> qnames)
    {
        this.qnames = qnames;
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        this.qnames = null;
    }
}
