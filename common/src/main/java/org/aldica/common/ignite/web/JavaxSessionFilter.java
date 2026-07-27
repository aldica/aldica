/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.web;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

import org.apache.ignite.IgniteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Axel Faust
 */
public class JavaxSessionFilter extends BaseSessionFilter implements Filter
{

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaxSessionFilter.class);

    private final ThreadLocal<ServletContext> ctx = new ThreadLocal<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final FilterConfig filterConfig) throws ServletException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException
    {
        if (this.enabled && request instanceof HttpServletRequest)
        {
            this.ctx.set(request.getServletContext());

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            httpRequest = new RequestWrapper(httpRequest);

            try
            {
                chain.doFilter(httpRequest, response);
            }
            finally
            {
                final JavaxSession session = (JavaxSession) httpRequest.getSession(false);
                this.ctx.remove();

                if (session != null && session.isValid())
                {
                    this.storeSession(session);
                }
            }
        }
        else
        {
            chain.doFilter(request, response);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy()
    {
        // NO-OP
    }

    protected JavaxSession loadSession(final String sessionId, final HttpSession realSession)
    {
        return this.loadSession(sessionId,
                entity -> new JavaxSession(sessionId, realSession, false, this.ctx.get(), entity, this.marshaller));
    }

    protected JavaxSession createSession(final String sessionId, final HttpSession realSession)
    {
        try
        {
            return this.createSession(sessionId, (entity, isNew) -> new JavaxSession(sessionId, realSession, isNew && realSession.isNew(),
                    this.ctx.get(), entity, this.marshaller));
        }
        catch (final IOException e)
        {
            throw new IgniteException(e);
        }
    }

    /**
     *
     * @author Axel Faust
     */
    private class RequestWrapper extends HttpServletRequestWrapper
    {

        private boolean requestedSessionChecked = false;

        private JavaxSession currentSession;

        private RequestWrapper(final HttpServletRequest req)
        {
            super(req);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public HttpSession getSession(final boolean create)
        {
            if (this.currentSession != null && !this.currentSession.isValid())
            {
                LOGGER.debug("Removing invalidated session {} from cache", this.currentSession.getId());
                JavaxSessionFilter.this.sessionEntityCache.remove(this.currentSession.getId());
                this.currentSession = null;
            }

            if (this.currentSession == null && !this.requestedSessionChecked)
            {
                this.requestedSessionChecked = true;

                final String requestedSessionId = ((HttpServletRequest) this.getRequest()).getRequestedSessionId();
                if (requestedSessionId != null)
                {
                    this.currentSession = JavaxSessionFilter.this.loadSession(requestedSessionId,
                            ((HttpServletRequest) this.getRequest()).getSession(false));
                    if (this.currentSession != null)
                    {
                        LOGGER.debug("Loaded requested session {} from cache", requestedSessionId);
                    }
                }
            }

            if (this.currentSession == null)
            {
                final HttpSession realSession = ((HttpServletRequest) this.getRequest()).getSession(create);
                if (realSession != null)
                {
                    this.currentSession = JavaxSessionFilter.this.loadSession(realSession.getId(), realSession);
                    if (this.currentSession == null)
                    {
                        LOGGER.debug("Add new session {} initial state to cache", realSession.getId());
                        this.currentSession = JavaxSessionFilter.this.createSession(realSession.getId(), realSession);
                    }
                    else
                    {
                        LOGGER.debug("Loaded state for session {} from cache", realSession.getId());
                    }
                }
            }

            return this.currentSession;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public HttpSession getSession()
        {
            return this.getSession(true);
        }

    }
}
