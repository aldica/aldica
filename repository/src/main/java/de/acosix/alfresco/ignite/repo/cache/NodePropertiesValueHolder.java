/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package de.acosix.alfresco.ignite.repo.cache;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;

import org.alfresco.service.namespace.QName;
import org.alfresco.util.ParameterCheck;

/**
 * @author Axel Faust
 */
public class NodePropertiesValueHolder implements Serializable
{

    private static final long serialVersionUID = 1L;

    protected final long[] qnameIds;

    protected final BitSet contentDataQNameIds = new BitSet();

    protected final BitSet lovQNameIds = new BitSet();

    protected final Serializable[] values;

    protected transient Map<QName, Serializable> properties;

    public NodePropertiesValueHolder(final long[] qnameIds, final Serializable[] values, final int[] contentDataQNameIdxs,
            final int[] lovQNameIdxs)
    {
        ParameterCheck.mandatory("qnameIds", qnameIds);
        ParameterCheck.mandatory("values", values);

        if (values.length != qnameIds.length)
        {
            throw new IllegalArgumentException("Length of values and QName IDs must match");
        }

        this.qnameIds = new long[qnameIds.length];
        System.arraycopy(qnameIds, 0, this.qnameIds, 0, qnameIds.length);

        this.values = new Serializable[values.length];
        System.arraycopy(values, 0, this.values, 0, values.length);

        if (contentDataQNameIdxs != null)
        {
            for (final int contentDataQNameIdx : contentDataQNameIdxs)
            {
                this.contentDataQNameIds.set(contentDataQNameIdx);
            }
        }

        if (lovQNameIdxs != null)
        {
            for (final int lovQNameIdx : lovQNameIdxs)
            {
                this.lovQNameIds.set(lovQNameIdx);
            }
        }
    }

    /**
     * @return the properties
     */
    public Map<QName, Serializable> getProperties()
    {
        return this.properties != null ? Collections.unmodifiableMap(this.properties) : null;
    }

    /**
     * @param properties
     *            the properties to set
     */
    public void setProperties(final Map<QName, Serializable> properties)
    {
        this.properties = properties;
    }

    /**
     * @return the qnameIds
     */
    public long[] getQnameIds()
    {
        final long[] qnameIds = new long[this.qnameIds.length];
        System.arraycopy(this.qnameIds, 0, qnameIds, 0, qnameIds.length);
        return qnameIds;
    }

    /**
     * @return the values
     */
    public Serializable[] getValues()
    {
        final Serializable[] values = new Serializable[this.values.length];
        System.arraycopy(this.values, 0, values, 0, values.length);
        return values;
    }

    public boolean isLovQNameId(final int qnameIdx)
    {
        return this.lovQNameIds.get(qnameIdx);
    }

    public boolean isContentDataQNameId(final int qnameIdx)
    {
        return this.contentDataQNameIds.get(qnameIdx);
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        this.properties = null;
    }
}