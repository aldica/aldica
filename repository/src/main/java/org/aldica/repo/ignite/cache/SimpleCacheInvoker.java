/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.cache;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.aldica.common.ignite.lifecycle.LazySwappingInvoker;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.cache.SimpleCache;

/**
 * Instances of this AOP handler provide a uniform access to all caches.
 *
 * @author Axel Faust
 */
// as an invocation handler we basically have to use raw types as generics can not be reliably used for reflection use cases
@SuppressWarnings("rawtypes")
public class SimpleCacheInvoker extends LazySwappingInvoker<SimpleCache> implements CacheWithMetrics
{

    private static final Map<Method, MethodHandle> CACHE_HANDLES = new HashMap<>();
    static
    {
        final MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
        final Method[] methods = SimpleCache.class.getDeclaredMethods();
        try
        {
            for (final Method method : methods)
            {
                CACHE_HANDLES.put(method, publicLookup.unreflect(method));
            }
        }
        catch (final IllegalAccessException iae)
        {
            throw new AlfrescoRuntimeException("Cannot obtain method handle", iae);
        }
    }

    protected SimpleCacheInvoker(final SimpleCache backingCache)
    {
        super(backingCache);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected MethodHandle bindIfSupported(final Method method)
    {
        final MethodHandle mh = CACHE_HANDLES.get(method);
        return mh != null ? mh.bindTo(this.backingObject) : super.bindIfSupported(method);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMetrics getMetrics()
    {
        throw new UnsupportedOperationException(this.backingObject.getClass() + " cannot provide detailed cache metrics");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size()
    {
        return this.backingObject.getKeys().size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int localSize()
    {
        return this.backingObject.getKeys().size();
    }
}