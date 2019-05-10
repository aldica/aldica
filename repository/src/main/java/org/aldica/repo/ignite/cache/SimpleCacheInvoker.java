/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.cache.TransactionalCache;

/**
 * Instances of this AOP handler provide a uniform access to all caches.
 *
 * @author Axel Faust
 */
// as an invocation handler we basically have to use raw types as generics can not be reliably used for reflection use cases
@SuppressWarnings("rawtypes")
public class SimpleCacheInvoker<K, V> implements InvocationHandler
{

    protected SimpleCache backingCache;

    protected SimpleCacheInvoker(final SimpleCache backingCache)
    {
        this.backingCache = backingCache;
    }

    public SimpleCache getBackingObject()
    {
        return this.backingCache;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable
    {
        Object result = null;
        final Class<?> declaringClass = method.getDeclaringClass();
        final String methodName = method.getName();
        if (declaringClass.isInstance(this.backingCache))
        {
            try
            {
                result = method.invoke(this.backingCache, args);
            }
            catch (final InvocationTargetException e)
            {
                throw e.getTargetException();
            }
        }
        else if (!TransactionalCache.class.isInstance(this.backingCache) && CacheWithMetrics.class.isAssignableFrom(declaringClass)
                && ("getMtrics".equals(methodName) || "size".equals(methodName) || "localSize".equals(methodName)))
        {
            switch (methodName)
            {
                case "getMetrics":
                    throw new UnsupportedOperationException(this.backingCache.getClass() + " cannot provide detailed cache metrics");
                case "size":
                case "localSize":
                    result = this.backingCache.getKeys().size();
                    break;
                default:
                    throw new UnsupportedOperationException(methodName + " is not supported by " + this.backingCache.getClass());
            }
        }
        else
        {
            throw new UnsupportedOperationException(methodName + " is not supported by " + this.backingCache.getClass());
        }

        return result;
    }
}