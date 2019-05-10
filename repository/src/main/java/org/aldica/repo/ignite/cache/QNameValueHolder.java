/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import org.alfresco.service.namespace.QName;

/**
 * @author Axel Faust
 */
public class QNameValueHolder implements Serializable
{

    private static final long serialVersionUID = -8776358055598257427L;

    protected final long namespaceId;

    // kept for simplification of on-heap cache scenarios
    protected transient QName actualValue;

    // technically final but due to readObject + intern() requirement we keep it without
    protected String localName;

    public QNameValueHolder(final long namespaceId, final QName actualValue)
    {
        this.namespaceId = namespaceId;
        this.actualValue = actualValue;
        this.localName = actualValue.getLocalName();
    }

    /**
     * @return the namespaceId
     */
    public long getNamespaceId()
    {
        return this.namespaceId;
    }

    /**
     * @return the localName
     */
    public String getLocalName()
    {
        return this.localName;
    }

    /**
     * @return the actualValue
     */
    public QName getActualValue()
    {
        return this.actualValue;
    }

    /**
     * @param actualValue
     *            the actualValue to set
     */
    public void setActualValue(final QName actualValue)
    {
        this.actualValue = actualValue;
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();

        this.localName = this.localName.intern();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (this.namespaceId ^ (this.namespaceId >>> 32));
        result = prime * result + this.localName.hashCode();
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
        final QNameValueHolder other = (QNameValueHolder) obj;
        if (this.namespaceId != other.namespaceId)
        {
            return false;
        }
        if (!this.localName.equals(other.localName))
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
        builder.append("QNameValueHolder [namespaceId=");
        builder.append(this.namespaceId);
        builder.append(", ");
        builder.append("localName=");
        builder.append(this.localName);
        builder.append("]");
        return builder.toString();
    }

}