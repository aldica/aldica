/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.spring;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import org.springframework.beans.factory.FactoryBean;

/**
 * @author Axel Faust
 */
public class PrometheusMeterRegistryFactoryBean implements FactoryBean<PrometheusMeterRegistry>
{

    /**
     * {@inheritDoc}
     */
    @Override
    public PrometheusMeterRegistry getObject() throws Exception
    {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<PrometheusMeterRegistry> getObjectType()
    {
        return PrometheusMeterRegistry.class;
    }

}
