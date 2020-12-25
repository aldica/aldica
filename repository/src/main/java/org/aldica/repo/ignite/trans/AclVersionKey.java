/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.trans;

import java.io.Serializable;

import org.alfresco.repo.domain.permissions.AclEntity;

/**
 * Instances of this class act as drop-in replacements to {@link AclEntity ACL entities} in case where those are used as keys into cached
 * data. Such replacements are necessary for use with Ignite-backed caches as the ACL entity class contained way more state than what is
 * actually used for key-related {@link AclEntity#hashCode() hashCode} and {@link AclEntity#equals(Object) equals} logic, and Ignite always
 * uses all state for its optimised hashCode/equals variants on serialised keys.
 *
 * @author Axel Faust
 */
public class AclVersionKey implements Serializable
{

    private static final long serialVersionUID = -7031061988884742978L;

    private final Long id;

    private final Long version;

    public AclVersionKey(final Long id, final Long version)
    {
        this.id = id;
        this.version = version;
    }

    /**
     * @return the id
     */
    public Long getId()
    {
        return this.id;
    }

    /**
     * @return the version
     */
    public Long getVersion()
    {
        return this.version;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.id == null) ? 0 : this.id.hashCode());
        result = prime * result + ((this.version == null) ? 0 : this.version.hashCode());
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
        final AclVersionKey other = (AclVersionKey) obj;
        if (this.id == null)
        {
            if (other.id != null)
            {
                return false;
            }
        }
        else if (!this.id.equals(other.id))
        {
            return false;
        }
        if (this.version == null)
        {
            if (other.version != null)
            {
                return false;
            }
        }
        else if (!this.version.equals(other.version))
        {
            return false;
        }
        return true;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(512);
        sb.append("AclKey").append("[ ID=").append(this.id).append(", version=").append(this.version).append("]");
        return sb.toString();
    }
}
