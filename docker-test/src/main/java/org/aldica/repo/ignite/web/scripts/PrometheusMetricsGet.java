/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.web.scripts;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.alfresco.util.PropertyCheck;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * @author Axel Faust
 */
public class PrometheusMetricsGet extends AbstractWebScript implements InitializingBean
{

    private PrometheusMeterRegistry meterRegistry;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "meterRegistry", this.meterRegistry);
    }

    /**
     * @param meterRegistry
     *     the meterRegistry to set
     */
    public void setMeterRegistry(final PrometheusMeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final WebScriptRequest req, final WebScriptResponse res) throws IOException
    {
        final String response = this.meterRegistry.scrape();
        res.setStatus(200);
        res.setContentEncoding("UTF-8");
        res.setHeader("length", String.valueOf(response.getBytes(StandardCharsets.UTF_8).length));
        res.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
    }

}
