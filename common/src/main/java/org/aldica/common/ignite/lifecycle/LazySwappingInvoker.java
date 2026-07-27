/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.lifecycle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.alfresco.error.AlfrescoRuntimeException;

/**
 * @author Axel Faust
 */
public class LazySwappingInvoker<E> implements InvocationHandler
{

    protected E backingObject;

    private final Map<Method, MethodHandle> boundHandles = new HashMap<>();

    protected LazySwappingInvoker(final E backingObject)
    {
        this.backingObject = backingObject;

        // pre-bind
        final Method[] methods = this.backingObject.getClass().getMethods();
        for (final Method method : methods)
        {
            if (method.getDeclaringClass().isInterface())
            {
                this.boundHandles.put(method, this.bindIfSupported(method));
            }
        }
    }

    public E getBackingObject()
    {
        return this.backingObject;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable
    {
        // use bound method handles for least possible overhead
        // computeIfAbsent would require use of ConcurrentHashMap
        // modifications are too rare to warrant its overhead
        // so we use a basic synchronisation pattern
        MethodHandle methodHandle = this.boundHandles.get(method);
        if (methodHandle == null)
        {
            synchronized (this)
            {
                methodHandle = this.boundHandles.get(method);
                if (methodHandle == null)
                {
                    methodHandle = this.bindIfSupported(method);
                    if (methodHandle != null)
                    {
                        this.boundHandles.put(method, methodHandle);
                    }
                }
            }
        }

        if (methodHandle != null)
        {
            Object result = null;
            if (void.class.equals(method.getReturnType()))
            {
                if (args != null)
                {
                    methodHandle.invokeWithArguments(args);
                }
                else
                {
                    methodHandle.invokeExact();
                }
            }
            else
            {
                result = args != null ? methodHandle.invokeWithArguments(args) : methodHandle.invoke();
            }
            return result;
        }
        throw new UnsupportedOperationException(method.getName() + " is not supported by " + this.backingObject.getClass());
    }

    protected MethodHandle bindIfSupported(final Method method)
    {
        MethodHandle mh = null;
        boolean bound = false;
        final Class<?> declaringClass = method.getDeclaringClass();
        try
        {
            if (declaringClass.isInstance(this.backingObject))
            {
                mh = MethodHandles.publicLookup().unreflect(method);
            }
            else if (declaringClass.isInstance(this))
            {
                mh = MethodHandles.publicLookup().unreflect(method);
                mh = mh.bindTo(this);
                bound = true;
            }
        }
        catch (final IllegalAccessException iae)
        {
            throw new AlfrescoRuntimeException("Cannot obtain method handle", iae);
        }
        return mh != null && !bound ? mh.bindTo(this.backingObject) : mh;
    }

    protected void resetBoundMethodHandles()
    {
        final Set<Method> methods = new HashSet<>(this.boundHandles.keySet());
        this.boundHandles.clear();

        // rebind in advance (may avoid concurrent update later)
        for (final Method method : methods)
        {
            this.boundHandles.put(method, this.bindIfSupported(method));
        }
    }
}
