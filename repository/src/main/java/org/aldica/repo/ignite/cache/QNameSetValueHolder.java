/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.alfresco.service.namespace.QName;

/**
 * @author Axel Faust
 */
public class QNameSetValueHolder implements Serializable
{

    private static final long serialVersionUID = -1821572340401679055L;

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

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(this.qnameIds);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (this.getClass() != obj.getClass())
        {
            return false;
        }
        final QNameSetValueHolder other = (QNameSetValueHolder) obj;
        if (!Arrays.equals(this.qnameIds, other.qnameIds))
        {
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("QNameSetValueHolder [");
        builder.append("qnameIds=");
        builder.append(Arrays.toString(this.qnameIds));
        builder.append("]");
        return builder.toString();
    }

}
