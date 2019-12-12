/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.web.scripts;

import java.io.IOException;

import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.util.PropertyCheck;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * @author Axel Faust
 */
public class ClearNodeCaches extends AbstractWebScript implements InitializingBean
{

    protected SimpleCache<?, ?> nodesCache;

    protected SimpleCache<?, ?> aspectsCache;

    protected SimpleCache<?, ?> propertiesCache;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "nodesCache", this.nodesCache);
        PropertyCheck.mandatory(this, "aspectsCache", this.aspectsCache);
        PropertyCheck.mandatory(this, "propertiesCache", this.propertiesCache);
    }

    /**
     * @param nodesCache
     *            the nodesCache to set
     */
    public void setNodesCache(final SimpleCache<?, ?> nodesCache)
    {
        this.nodesCache = nodesCache;
    }

    /**
     * @param aspectsCache
     *            the aspectsCache to set
     */
    public void setAspectsCache(final SimpleCache<?, ?> aspectsCache)
    {
        this.aspectsCache = aspectsCache;
    }

    /**
     * @param propertiesCache
     *            the propertiesCache to set
     */
    public void setPropertiesCache(final SimpleCache<?, ?> propertiesCache)
    {
        this.propertiesCache = propertiesCache;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void execute(final WebScriptRequest req, final WebScriptResponse res) throws IOException
    {
        this.nodesCache.clear();
        this.aspectsCache.clear();
        this.propertiesCache.clear();

        res.setStatus(Status.STATUS_NO_CONTENT);
    }

}
