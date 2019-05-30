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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a lock store implementation backed by a local or distributed Ignite cache.
 *
 * @author Axel Faust
 */
public class IgniteBackedLockStore extends AbstractLockStore<CacheConcurrentMapFacade<NodeRef, LockState>>
{

    private static final Logger LOGGER = LoggerFactory.getLogger(IgniteBackedLockStore.class);

    protected final IgniteCache<NodeRef, LockState> lockCache;

    /**
     * Instantiates a new instance of this class using the provided Ignite cache instances as the backing in-memory data structure.
     *
     * @param lockCache
     *            the Ignite cache to use for storing node lock states
     */
    public IgniteBackedLockStore(final IgniteCache<NodeRef, LockState> lockCache)
    {
        super(new CacheConcurrentMapFacade<>(lockCache, NodeRef.class));
        this.lockCache = lockCache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockState get(final NodeRef nodeRef)
    {
        LOGGER.debug("Retrieving lock state for node {}", nodeRef);
        final LockState lockState = super.get(nodeRef);
        LOGGER.debug("Retrieved lock state {} for node {}", lockState, nodeRef);
        return lockState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(final NodeRef nodeRef, final LockState lockState)
    {
        LOGGER.debug("Setting lock state {} for node {}", lockState, nodeRef);
        super.set(nodeRef, lockState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear()
    {
        // using warn-logging as LockStore JavaDoc states the operation as unsafe for production
        LOGGER.warn("Clearing all in-memory lock states");
        super.clear();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Set<NodeRef> getNodes()
    {
        LOGGER.debug("Retrieving all nodes with cached lock states");
        // need to override this as CacheConcurrentMapFacade does not support keySet() (in a manner compliant to the Map interface)
        final Set<NodeRef> nodes = new HashSet<>();
        this.lockCache.iterator().forEachRemaining(entry -> {
            nodes.add(entry.getKey());
        });
        LOGGER.debug("Retrieved {} total nodes with cached lock states", nodes.size());
        return nodes;
    }
}
