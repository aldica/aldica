/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.web;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.cache.CacheException;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.expiry.ModifiedExpiryPolicy;

import org.aldica.common.ignite.util.PropertyCheck;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteClientDisconnectedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterTopologyException;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.websession.WebSessionAttributeProcessor;
import org.apache.ignite.internal.websession.WebSessionEntity;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.marshaller.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 *
 * @author Axel Faust
 */
public abstract class BaseSessionFilter implements InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseSessionFilter.class);

    protected boolean enabled = false;

    protected IgniteCache<String, WebSessionEntity> sessionEntityCache;

    protected Marshaller marshaller;

    private String igniteInstanceName;

    private String igniteCacheName;

    private int retries;

    private int retriesTimeout;

    /**
     * Sets the enable flag for the filter.
     *
     * @param enabled
     *     the enabled to set
     */
    public void setEnabled(final boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * Sets the name of the Ignite instance to use for the web session cache.
     *
     * @param igniteInstanceName
     *     the igniteInstanceName to set
     */
    public void setIgniteInstanceName(final String igniteInstanceName)
    {
        this.igniteInstanceName = igniteInstanceName;
    }

    /**
     * Sets the name cache to use for handling web sessions.
     *
     * @param igniteCacheName
     *     the igniteCacheName to set
     */
    public void setIgniteCacheName(final String igniteCacheName)
    {
        this.igniteCacheName = igniteCacheName;
    }

    /**
     * Sets the number of retries for cache operations.
     *
     * @param retries
     *     the retries to set
     */
    public void setRetries(final int retries)
    {
        this.retries = retries;
    }

    /**
     * Sets the timeout (in millseconds) to wait for a cluster/cache to recover before retrying a cache operation.
     *
     * @param retriesTimeout
     *     the retriesTimeout to set
     */
    public void setRetriesTimeout(final int retriesTimeout)
    {
        this.retriesTimeout = retriesTimeout;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void afterPropertiesSet()
    {
        if (this.enabled)
        {
            LOGGER.info("Session filter is enabled");

            PropertyCheck.mandatory(this, "instanceName", this.igniteInstanceName);
            PropertyCheck.mandatory(this, "cacheName", this.igniteCacheName);

            final Ignite ignite = Ignition.ignite(this.igniteInstanceName);
            this.sessionEntityCache = ignite.cache(this.igniteCacheName);
            this.marshaller = ignite.configuration().getMarshaller();
        }
    }

    protected IgniteCache<String, WebSessionEntity> cacheWithExpiryPolicy(final int maxInactiveInteval)
    {
        if (maxInactiveInteval > 0)
        {
            final long ttl = maxInactiveInteval * 1000L;
            final ExpiryPolicy plc = new ModifiedExpiryPolicy(new Duration(MILLISECONDS, ttl));
            return this.sessionEntityCache.withExpiryPolicy(plc);
        }

        return this.sessionEntityCache;
    }

    protected <T extends BaseSession> T loadSession(final String sessionId, final Function<WebSessionEntity, T> transformer)
    {
        for (int i = 0; i < this.retries; i++)
        {
            try
            {
                final WebSessionEntity entity = this.sessionEntityCache.get(sessionId);

                if (entity != null)
                {
                    return transformer.apply(entity);
                }

                break;
            }
            catch (CacheException | IgniteException | IllegalStateException e)
            {
                this.handleLoadSessionException(sessionId, i, e);
            }
        }
        return null;
    }

    protected <T extends BaseSession> T createSession(final String sessionId, final BiFunction<WebSessionEntity, Boolean, T> transformer)
            throws IOException
    {
        T cachedSession = transformer.apply(null, Boolean.TRUE);
        final WebSessionEntity marshalled = cachedSession.marshalAttributes();

        for (int i = 0; i < this.retries; i++)
        {
            try
            {
                final WebSessionEntity old = this.cacheWithExpiryPolicy(cachedSession.getMaxInactiveInterval()).getAndPutIfAbsent(sessionId,
                        marshalled);

                if (old != null)
                {
                    cachedSession = transformer.apply(old, Boolean.FALSE);
                }
                else
                {
                    cachedSession = transformer.apply(marshalled, Boolean.TRUE);
                }

                break;
            }
            catch (CacheException | IgniteException | IllegalStateException e)
            {
                this.handleCreateSessionException(sessionId, i, e);
            }
        }
        return cachedSession;
    }

    protected <T extends BaseSession> void storeSession(final T session) throws IOException
    {
        final String sessionId = session.getId();
        final Map<String, byte[]> updatesMap = session.binaryUpdatesMap();
        LOGGER.debug("Session binary attributes updated [id={}, updates={}]", sessionId, updatesMap.keySet());

        try
        {
            for (int i = 0; i < this.retries; i++)
            {
                try
                {
                    this.cacheWithExpiryPolicy(session.getMaxInactiveInterval()).invoke(sessionId,
                            new WebSessionAttributeProcessor(updatesMap.isEmpty() ? null : updatesMap, session.getLastAccessedTime(),
                                    session.getMaxInactiveInterval(), session.isMaxInactiveIntervalChanged()));
                    break;
                }
                catch (CacheException | IgniteException | IllegalStateException e)
                {
                    this.handleAttributeUpdateException(sessionId, i, e);
                }
            }
        }
        catch (final Exception e)
        {
            LOGGER.error("Failed to update session V2 attributes [id={}]", sessionId, e);
        }
    }

    private void handleLoadSessionException(final String sesId, final int tryCnt, final RuntimeException e)
    {
        LOGGER.debug("Handling error loading session", e);

        if (tryCnt == this.retries - 1)
        {
            throw new IgniteException("Failed to handle request [session= " + sesId + "]", e);
        }
        else
        {
            LOGGER.debug("Failed to handle request (will retry): {}", sesId);

            this.handleCacheOperationException(e);
        }
    }

    private void handleCreateSessionException(final String sesId, final int tryCnt, final RuntimeException e)
    {
        LOGGER.debug("Handling error creating session", e);

        if (tryCnt == this.retries - 1)
        {
            throw new IgniteException("Failed to save session: " + sesId, e);
        }
        else
        {
            LOGGER.debug("Failed to save session (will retry): {}", sesId);
            this.handleCacheOperationException(e);
        }
    }

    private void handleAttributeUpdateException(final String sesId, final int tryCnt, final RuntimeException e)
    {
        if (tryCnt == this.retries - 1)
        {
            LOGGER.error("Failed to apply updates for session (maximum number of retries exceeded) [sesId={}, retries={}]", sesId, tryCnt,
                    e);
        }
        else
        {
            LOGGER.warn("Failed to apply updates for session (will retry): {}", sesId);
            this.handleCacheOperationException(e);
        }
    }

    private void handleCacheOperationException(final Exception e)
    {
        IgniteFuture<?> retryFut = null;

        if (X.hasCause(e, IgniteClientDisconnectedException.class))
        {
            final IgniteClientDisconnectedException cause = X.cause(e, IgniteClientDisconnectedException.class);
            assert cause != null : e;
            retryFut = cause.reconnectFuture();
        }
        else if (X.hasCause(e, ClusterTopologyException.class))
        {
            final ClusterTopologyException cause = X.cause(e, ClusterTopologyException.class);
            assert cause != null : e;
            retryFut = cause.retryReadyFuture();
        }

        if (retryFut != null)
        {
            try
            {
                retryFut.get(this.retriesTimeout);
            }
            catch (final IgniteException retryErr)
            {
                throw new IgniteException("Failed to wait for retry: " + retryErr);
            }
        }
    }
}
