/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This utility class provides the means to associate a thread-local, "external" context to operations dealing with Ignite components. The
 * term "external" in this case refers to the fact that Ignite internals are completely unaware of this context and no Ingite-specific
 * context needs to be active for using this utility.
 *
 * @author Axel Faust
 */
public final class ExternalContext
{

    public static final String KEY_IGNITE_INSTANCE_NAME = "igniteInstanceName";

    /**
     *
     * @author Axel Faust
     */
    @FunctionalInterface
    public static interface IgniteContextOperation<T>
    {

        /**
         * Executes this operation.
         *
         * @return the result of the operation
         */
        T execute();
    }

    /**
     *
     * @author Axel Faust
     */
    @FunctionalInterface
    public static interface IgniteContextOperationFailureHandler
    {

        /**
         * Executes this failure handler.
         *
         * @param t
         *            the cause of the failure
         */
        void onFailure(Throwable t);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalContext.class);

    private static final ThreadLocal<List<Pair<List<IgniteContextOperationFailureHandler>, Map<String, Object>>>> CONTEXT_STACK = new ThreadLocal<>();

    private ExternalContext()
    {
        // NO-OP
    }

    /**
     * Executes an operation in an external Ignite context populated with properties inherited from the currently active external context
     * (if any) and the parameter properties.
     *
     * @param operation
     *            the operation to execute
     * @param contextProperties
     *            the context properties to store in the new active context - these will override any properties with identical keys that
     *            may be inherited from the currently active external context
     * @param <R>
     *            the type of the operation's result
     * @return the result of the operation
     */
    public static <R> R withExternalContext(final IgniteContextOperation<R> operation, final Map<String, ?> contextProperties)
    {
        List<Pair<List<IgniteContextOperationFailureHandler>, Map<String, Object>>> stack = CONTEXT_STACK.get();
        Pair<List<IgniteContextOperationFailureHandler>, Map<String, Object>> context;
        Map<String, Object> contextMap;
        if (stack == null)
        {
            stack = new LinkedList<>();
            contextMap = new HashMap<>(contextProperties);
            context = new Pair<>(new ArrayList<>(), contextMap);
            stack.add(0, context);
            CONTEXT_STACK.set(stack);
        }
        else
        {
            final Pair<List<IgniteContextOperationFailureHandler>, Map<String, Object>> currentContext = stack.get(0);
            final Map<String, Object> currentContextMap = currentContext.getSecond();
            contextMap = new HashMap<>(currentContextMap);
            if (contextProperties != null)
            {
                contextMap.putAll(contextProperties);
            }
            context = new Pair<>(new ArrayList<>(), contextMap);
            stack.add(0, context);
        }

        try
        {
            final R result = operation.execute();
            return result;
        }
        catch (final Throwable t)
        {

            final List<IgniteContextOperationFailureHandler> failureHandlers = context.getFirst();
            try
            {
                if (!failureHandlers.isEmpty())
                {
                    LOGGER.debug("Executing failure handlers for IgniteContextOperation error", t);
                    for (final IgniteContextOperationFailureHandler failureHandler : failureHandlers)
                    {
                        failureHandler.onFailure(t);
                    }
                }
                else
                {
                    LOGGER.debug("No failure handlers registered in current context to handle IgniteContextOperation error", t);
                }
            }
            catch (final Throwable handlerFailure)
            {
                LOGGER.error("Error executing failureHandler", handlerFailure);
            }
            if (t instanceof RuntimeException)
            {
                throw (RuntimeException) t;
            }
            throw new AlfrescoRuntimeException("Error executing IgniteContextOperation", t);
        }
        finally
        {
            stack.remove(0);
            if (stack.isEmpty())
            {
                CONTEXT_STACK.remove();
            }
        }
    }

    /**
     * Registers a failure handler to be called if an {@link IgniteContextOperation} fails when being executed via
     * {@link #withExternalContext(IgniteContextOperation, Map) withExternalContext}. The registered handler may be associated with the
     * current or top-most (global) context.
     *
     * @param failureHandler
     *            the handler to register
     * @param global
     *            {@code true} if the failure handler should be registered with the top-most (global) context
     */
    public static void registerFailureHandler(final IgniteContextOperationFailureHandler failureHandler, final boolean global)
    {
        final List<Pair<List<IgniteContextOperationFailureHandler>, Map<String, Object>>> stack = CONTEXT_STACK.get();

        if (stack != null)
        {
            final Pair<List<IgniteContextOperationFailureHandler>, Map<String, Object>> context;

            if (global)
            {
                context = stack.get(stack.size() - 1);
            }
            else
            {
                context = stack.get(0);
            }

            context.getFirst().add(failureHandler);
        }
        else
        {
            LOGGER.warn("No external Ignite context is currentyl active - not registering failure handler", new RuntimeException());
        }
    }

    /**
     * Checks if there is currently a context active on the current thread.
     *
     * @return {@code true} if a context is active, {@code false} otherwise
     */
    public static boolean hasActiveContext()
    {
        final List<Pair<List<IgniteContextOperationFailureHandler>, Map<String, Object>>> stack = CONTEXT_STACK.get();
        return stack != null;
    }

    /**
     * Retrieves an attribute from the currently active, external Ignite context.
     *
     * @param key
     *            the key of the attribute to retrieve
     * @return the value of the attribute, or {@code null} if no attribute identified by the key exists or no context is currently active
     */
    public static Object getExternalContextAttribute(final String key)
    {
        final List<Pair<List<IgniteContextOperationFailureHandler>, Map<String, Object>>> stack = CONTEXT_STACK.get();
        final Pair<List<IgniteContextOperationFailureHandler>, Map<String, Object>> currentContext = stack.get(0);
        final Map<String, Object> currentContextMap = currentContext != null ? currentContext.getSecond() : null;
        final Object value = currentContextMap != null ? currentContextMap.get(key) : null;
        return value;
    }
}
