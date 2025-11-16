package org.aldica.common.ignite.web;

import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import org.apache.ignite.internal.websession.WebSessionEntity;
import org.apache.ignite.marshaller.Marshaller;

/**
 *
 * @author Axel Faust
 */
@SuppressWarnings("deprecation")
public class JavaxSession extends BaseSession implements HttpSession
{

    private static final HttpSessionContext EMPTY_SES_CTX = new HttpSessionContext()
    {

        @Override
        public HttpSession getSession(final String id)
        {
            return null;
        }

        @Override
        public Enumeration<String> getIds()
        {
            return Collections.enumeration(Collections.<String> emptyList());
        }
    };

    private final ServletContext ctx;

    private final HttpSession session;

    JavaxSession(final String id, final HttpSession session, final boolean isNew, final ServletContext ctx, final WebSessionEntity entity,
            final Marshaller marshaller)
    {
        super(id, isNew,
                entity != null || session == null ? entity
                        : new WebSessionEntity(id, session.getCreationTime(), System.currentTimeMillis(), session.getMaxInactiveInterval()),
                marshaller);

        assert ctx != null;

        this.ctx = ctx;
        this.session = session;

        if (session != null)
        {
            final Enumeration<String> attributeNames = session.getAttributeNames();
            while (attributeNames.hasMoreElements())
            {
                final String name = attributeNames.nextElement();
                this.attributes.put(name, session.getAttribute(name));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServletContext getServletContext()
    {
        return this.ctx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpSessionContext getSessionContext()
    {
        return EMPTY_SES_CTX;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doInvalidate()
    {
        if (this.session != null)
        {
            try
            {
                this.session.invalidate();
            }
            catch (final IllegalStateException ignored)
            {
                // Already invalidated, keep going.
            }
        }
    }

}
