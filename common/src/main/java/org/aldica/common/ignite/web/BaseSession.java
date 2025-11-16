package org.aldica.common.ignite.web;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ignite.IgniteException;
import org.apache.ignite.internal.websession.WebSessionEntity;
import org.apache.ignite.marshaller.Marshaller;

/**
 *
 *
 * @author Axel Faust
 */
public abstract class BaseSession
{

    /** Entity that holds binary attributes. */
    private final WebSessionEntity entity;

    protected final Map<String, Object> attributes = new HashMap<>();

    protected final Set<String> removedAttributeNames = new HashSet<>();

    /** Timestamp that shows when this object was created. (Last access time from user request) */
    private final long accessTime;

    /** Cached session TTL since last query. */
    private int maxInactiveInterval;

    /** Flag indicates if {@link #maxInactiveInterval} waiting for update in cache. */
    private boolean maxInactiveIntervalChanged;

    /** New session flag. */
    private final boolean isNew;

    /** Session invalidation flag. */
    private boolean invalidated;

    /** Grid marshaller. */
    private final Marshaller marshaller;

    BaseSession(final String id, final boolean isNew, final WebSessionEntity entity, final Marshaller marshaller)
    {
        assert id != null;
        assert marshaller != null;
        assert entity != null;

        this.marshaller = marshaller;
        this.isNew = isNew;

        this.entity = entity;

        this.accessTime = entity.accessTime();
        this.maxInactiveInterval = entity.maxInactiveInterval();
    }

    /**
     * Returns the time when this session was created, measured in milliseconds since midnight January 1, 1970 GMT.
     *
     * @return a <code>long</code> specifying when this session was created, expressed in milliseconds since 1/1/1970 GMT
     *
     * @exception IllegalStateException
     *     if this method is called on an invalidated session
     */
    public long getCreationTime()
    {
        this.assertValid();
        return this.entity.createTime();
    }

    /**
     * Returns a string containing the unique identifier assigned to this session. The identifier is assigned by the servlet
     * container and is implementation dependent.
     *
     * @return a string specifying the identifier assigned to this session
     */
    public String getId()
    {
        return this.entity.id();
    }

    /**
     *
     * Returns the last time the client sent a request associated with this session, as the number of milliseconds since
     * midnight January 1, 1970 GMT, and marked by the time the container received the request.
     *
     * Actions that your application takes, such as getting or setting a value associated with the session, do not affect
     * the access time.
     *
     * @return a <code>long</code> representing the last time the client sent a request associated with this session,
     * expressed in milliseconds since 1/1/1970 GMT
     *
     * @exception IllegalStateException
     *     if this method is called on an invalidated session
     */
    public long getLastAccessedTime()
    {
        this.assertValid();
        return this.accessTime;
    }

    /**
     * Specifies the time, in seconds, between client requests before the servlet container will invalidate this session.
     *
     * An interval value of zero or less indicates that the session should never timeout.
     *
     * @param interval
     *     An integer specifying the number of seconds
     */
    public void setMaxInactiveInterval(final int interval)
    {
        this.maxInactiveInterval = interval;
        this.maxInactiveIntervalChanged = true;
    }

    public boolean isMaxInactiveIntervalChanged()
    {
        return this.maxInactiveIntervalChanged;
    }

    /**
     * Returns the maximum time interval, in seconds, that the servlet container will keep this session open between client
     * accesses. After this interval, the servlet container will invalidate the session. The maximum time interval can be
     * set with the <code>setMaxInactiveInterval</code> method.
     *
     * A return value of zero or less indicates that the session will never timeout.
     *
     * @return an integer specifying the number of seconds this session remains open between client requests
     *
     * @see #setMaxInactiveInterval
     */
    public int getMaxInactiveInterval()
    {
        return this.maxInactiveInterval;
    }

    /**
     * Returns the object bound with the specified name in this session, or <code>null</code> if no object is bound under
     * the name.
     *
     * @param name
     *     a string specifying the name of the object
     *
     * @return the object with the specified name
     *
     * @exception IllegalStateException
     *     if this method is called on an invalidated session
     */
    public Object getAttribute(final String name)
    {
        this.assertValid();

        if (this.removedAttributeNames.contains(name))
        {
            return null;
        }

        Object attr = this.attributes.get(name);
        if (attr == null)
        {
            final byte[] bytes = this.entity.attributes().get(name);

            if (bytes != null)
            {
                try
                {
                    attr = this.unmarshal(bytes);
                }
                catch (final IOException e)
                {
                    throw new IgniteException(e);
                }

                this.attributes.put(name, attr);
            }
        }

        return attr;
    }

    /**
     * @see #getAttribute(String)
     *
     * @param name
     *     a string specifying the name of the object
     * @return the object with the specified name
     *
     * @exception IllegalStateException
     *     if this method is called on an invalidated session
     */
    public Object getValue(final String name)
    {
        return this.getAttribute(name);
    }

    /**
     * Binds an object to this session, using the name specified. If an object of the same name is already bound to the
     * session, the object is replaced.
     *
     * After this method executes, and if the new object implements <code>HttpSessionBindingListener</code>, the container
     * calls <code>HttpSessionBindingListener.valueBound</code>. The container then notifies any
     * <code>HttpSessionAttributeListener</code>s in the web application.
     *
     * If an object was already bound to this session of this name that implements <code>HttpSessionBindingListener</code>,
     * its <code>HttpSessionBindingListener.valueUnbound</code> method is called.
     *
     * If the value passed in is null, this has the same effect as calling <code>removeAttribute()</code>.
     *
     *
     * @param name
     *     the name to which the object is bound; cannot be null
     *
     * @param value
     *     the object to be bound
     *
     * @exception IllegalStateException
     *     if this method is called on an invalidated session
     */
    public void setAttribute(final String name, final Object value)
    {
        this.assertValid();

        if (value == null)
        {
            this.removeAttribute(name);
        }
        else
        {
            this.removedAttributeNames.remove(name);
            this.attributes.put(name, value);
        }
    }

    /**
     * Sets a session attribute value.
     *
     * @param name
     *     the name to which the object is bound; cannot be null
     * @param value
     *     the object to be bound; cannot be null
     *
     * @exception IllegalStateException
     *     if this method is called on an invalidated session
     */
    public void putValue(final String name, final Object value)
    {
        this.setAttribute(name, value);
    }

    /**
     * Returns an <code>Enumeration</code> of <code>String</code> objects containing the names of all the objects bound to
     * this session.
     *
     * @return an <code>Enumeration</code> of <code>String</code> objects specifying the names of all the objects bound to
     * this session
     *
     * @exception IllegalStateException
     *     if this method is called on an invalidated session
     */
    public Enumeration<String> getAttributeNames()
    {
        this.assertValid();
        return Collections.enumeration(this.attributeNames());
    }

    /**
     * @see #getAttributeNames()
     *
     * @return an array of <code>String</code> objects specifying the names of all the objects bound to this session
     *
     * @exception IllegalStateException
     *     if this method is called on an invalidated session
     */
    public String[] getValueNames()
    {
        this.assertValid();
        final Set<String> attributeNames = this.attributeNames();
        final String[] attributeNamesArr = attributeNames.toArray(new String[0]);
        return attributeNamesArr;
    }

    /**
     * Removes the object bound with the specified name from this session. If the session does not have an object bound with
     * the specified name, this method does nothing.
     *
     * After this method executes, and if the object implements <code>HttpSessionBindingListener</code>, the container calls
     * <code>HttpSessionBindingListener.valueUnbound</code>. The container then notifies any <code>HttpSessionAttributeListener</code>s in
     * the web application.
     *
     * @param name
     *     the name of the object to remove from this session
     *
     * @exception IllegalStateException
     *     if this method is called on an invalidated session
     */
    public void removeAttribute(final String name)
    {
        this.assertValid();
        this.attributes.remove(name);
        this.removedAttributeNames.add(name);
    }

    /**
     * @see #removeAttribute(String)
     *
     * @param name
     *     the name of the object to remove from this session
     *
     * @exception IllegalStateException
     *     if this method is called on an invalidated session
     */
    public void removeValue(final String name)
    {
        this.removeAttribute(name);
    }

    /**
     * Invalidates this session then unbinds any objects bound to it.
     *
     * @exception IllegalStateException
     *     if this method is called on an already invalidated session
     */
    public void invalidate()
    {
        this.assertValid();
        this.doInvalidate();
        this.invalidated = true;
    }

    /**
     * Returns <code>true</code> if the client does not yet know about the session or if the client chooses not to join the
     * session. For example, if the server used only cookie-based sessions, and the client had disabled the use of cookies,
     * then a session would be new on each request.
     *
     * @return <code>true</code> if the server has created a session, but the client has not yet joined
     *
     * @exception IllegalStateException
     *     if this method is called on an already invalidated session
     */
    public boolean isNew()
    {
        this.assertValid();
        return this.isNew;
    }

    /**
     * Retrieves whether the session is valid or not.
     *
     * @return {@code true} if the session is valid, {@code false} if invalidated
     */
    public boolean isValid()
    {
        return !this.invalidated;
    }

    /**
     * Marshals all attributes and save to serializable entity.
     *
     * @return the web session entity
     * @throws IOException
     *     if the underlying binary marshaller throws an error
     */
    public WebSessionEntity marshalAttributes() throws IOException
    {
        final WebSessionEntity marshaled = new WebSessionEntity(this.getId(), this.entity.createTime(), this.accessTime,
                this.maxInactiveInterval);

        for (final Map.Entry<String, Object> entry : this.attributes.entrySet())
        {
            marshaled.putAttribute(entry.getKey(), this.marshal(entry.getValue()));
        }

        return marshaled;
    }

    /**
     * Builds a map with binary data from the updated/removed attributes.
     *
     * @return the marshalled updates or empty map if no params.
     * @throws IOException
     *     if the underlying binary marshaller throws an error
     */
    public Map<String, byte[]> binaryUpdatesMap() throws IOException
    {
        if (this.attributes.isEmpty() && this.removedAttributeNames.isEmpty())
        {
            return Collections.emptyMap();
        }

        final Map<String, byte[]> res = new HashMap<>(this.attributes.size() + this.removedAttributeNames.size());

        for (final Map.Entry<String, Object> entry : this.attributes.entrySet())
        {
            res.put(entry.getKey(), this.marshal(entry.getValue()));
        }
        for (final String name : this.removedAttributeNames)
        {
            res.put(name, this.marshal(null));
        }

        return res;
    }

    protected void assertValid()
    {
        if (this.invalidated)
        {
            throw new IllegalStateException("Session was invalidated.");
        }
    }

    protected Set<String> attributeNames()
    {
        final Set<String> names = new HashSet<>(this.entity.attributes().size() + this.attributes.size());

        names.addAll(this.entity.attributes().keySet());
        names.addAll(this.attributes.keySet());
        names.removeAll(this.removedAttributeNames);

        return names;
    }

    abstract protected void doInvalidate();

    private <T> T unmarshal(final byte[] bytes) throws IOException
    {
        try
        {
            return this.marshaller.unmarshal(bytes, this.getClass().getClassLoader());
        }
        catch (final Exception e)
        {
            throw new IOException(e);
        }
    }

    private byte[] marshal(final Object obj) throws IOException
    {
        try
        {
            return this.marshaller.marshal(obj);
        }
        catch (final Exception e)
        {
            throw new IOException(e);
        }
    }
}
