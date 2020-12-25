/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.trans;

import org.alfresco.repo.domain.permissions.AclEntity;

/**
 * Instances of this class map ACL entities into simpler ACL key instances for use as keys in Alfresco caches.
 *
 * @author Axel Faust
 */
public class AclVersionKeyTransformer implements CacheObjectTransformer<AclEntity, AclVersionKey>
{

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public AclEntity transformToExternalValue(final AclVersionKey cacheValue)
    {
        throw new UnsupportedOperationException("Transformer only support uni-directional transformation of AclEntity into AclKey");
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public AclVersionKey transformToCacheValue(final AclEntity externalValue)
    {
        return new AclVersionKey(externalValue.getId(), externalValue.getVersion());
    }
}
