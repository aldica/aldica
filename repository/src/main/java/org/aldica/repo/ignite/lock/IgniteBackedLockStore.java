/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.lock;

import java.util.HashSet;
import java.util.Set;

import org.alfresco.repo.lock.mem.AbstractLockStore;
import org.alfresco.repo.lock.mem.LockState;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.ignite.IgniteCache;

/**
 * This class provides a lock store implementation backed by a local or distributed Ignite cache.
 *
 * @author Axel Faust
 */
public class IgniteBackedLockStore extends AbstractLockStore<CacheConcurrentMapFacade<NodeRef, LockState>>
{

    protected final IgniteCache<NodeRef, LockState> lockCache;

    public IgniteBackedLockStore(final IgniteCache<NodeRef, LockState> lockCache)
    {
        super(new CacheConcurrentMapFacade<>(lockCache, NodeRef.class));
        this.lockCache = lockCache;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Set<NodeRef> getNodes()
    {
        // need to override this as CacheConcurrentMapFacade does not support values()
        final Set<NodeRef> nodes = new HashSet<>();
        this.lockCache.iterator().forEachRemaining(entry -> {
            nodes.add(entry.getKey());
        });
        return nodes;
    }
}
