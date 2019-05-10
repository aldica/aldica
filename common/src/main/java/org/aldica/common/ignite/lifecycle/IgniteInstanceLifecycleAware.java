/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.lifecycle;

import org.apache.ignite.lifecycle.LifecycleBean;

/**
 * This interface allows an instantiated bean to react to Ignite instance lifecycle events. Any bean handling Ignite isntances will use this
 * interface to lookup lifecycle-aware beans from the application context to inform them about such events. Note that only singleton and
 * already initialised beans will be looked up.
 *
 * Unfortunately {@link LifecycleBean} does not provide sufficient information in some cases disqualifying it from this use case.
 *
 * @author Axel Faust
 */
public interface IgniteInstanceLifecycleAware
{

    /**
     * Informs this instance that an instance is about to be started.
     *
     * @param instanceName
     *            the name of the instance to be started
     */
    void beforeInstanceStartup(String instanceName);

    /**
     * Informs this instance that an instance has been started.
     *
     * @param instanceName
     *            the name of the instance that has been started
     */
    void afterInstanceStartup(String instanceName);

    /**
     * Informs this instance that an instance is about to be shutdown.
     *
     * @param instanceName
     *            the name of the instance to be shutdown
     */
    void beforeInstanceShutdown(String instanceName);

    /**
     * Informs this instance that an instance has been shutdown.
     *
     * @param instanceName
     *            the name of the instance that has been shutdown
     */
    void afterInstanceShutdown(String instanceName);

}
