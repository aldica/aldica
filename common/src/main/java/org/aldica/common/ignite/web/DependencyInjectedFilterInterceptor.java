/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.web;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.extensions.webscripts.servlet.DependencyInjectedFilter;

/**
 *
 * @author Axel Faust
 */
public class DependencyInjectedFilterInterceptor implements MethodInterceptor
{

    private Method targetMethod;

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable
    {
        Object result;
        final Method method = invocation.getMethod();
        if (DependencyInjectedFilter.class.equals(method.getDeclaringClass()) && "doFilter".equals(method.getName()))
        {
            final Object targetObject = invocation.getThis();
            if (this.targetMethod == null)
            {
                final Class<?>[] parameterTypes = method.getParameterTypes();
                final Class<?>[] servletParameterTypes = new Class[parameterTypes.length - 1];
                System.arraycopy(parameterTypes, 1, servletParameterTypes, 0, servletParameterTypes.length);

                this.targetMethod = targetObject.getClass().getDeclaredMethod("doFilter", servletParameterTypes);
            }

            final Object[] arguments = invocation.getArguments();
            final Object[] servletApiArguments = new Object[arguments.length - 1];
            System.arraycopy(arguments, 1, servletApiArguments, 0, servletApiArguments.length);

            result = this.targetMethod.invoke(targetObject, servletApiArguments);
        }
        else
        {
            result = invocation.proceed();
        }
        return result;
    }

}
