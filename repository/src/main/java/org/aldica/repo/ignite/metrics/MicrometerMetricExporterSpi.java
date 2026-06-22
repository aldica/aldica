/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.alfresco.util.PropertyCheck;
import org.apache.ignite.spi.IgniteSpiAdapter;
import org.apache.ignite.spi.IgniteSpiConsistencyChecked;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.metric.DoubleMetric;
import org.apache.ignite.spi.metric.HistogramMetric;
import org.apache.ignite.spi.metric.IntMetric;
import org.apache.ignite.spi.metric.LongMetric;
import org.apache.ignite.spi.metric.Metric;
import org.apache.ignite.spi.metric.MetricExporterSpi;
import org.apache.ignite.spi.metric.ReadOnlyMetricManager;
import org.apache.ignite.spi.metric.ReadOnlyMetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * This metric exporter SPI allows Ignite metrics to be exposed via a Micrometer registry.
 *
 * @author Axel Faust
 */
@IgniteSpiConsistencyChecked(optional = true, checkClient = false)
public class MicrometerMetricExporterSpi extends IgniteSpiAdapter
        implements MetricExporterSpi, InitializingBean, ApplicationListener<ApplicationEvent>
{

    private static final String TAG_VALUE_BOUND = "value_bound";

    private static final String TAG_IGNITE_INSTANCE = "ignite_instance";

    private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerMetricExporterSpi.class);

    private ReadOnlyMetricManager registry;

    private Predicate<ReadOnlyMetricRegistry> filter;

    private MeterRegistry meterRegistry;

    private final List<String> metricNames = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() throws Exception
    {
        PropertyCheck.mandatory(this, "meterRegistry", this.meterRegistry);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onApplicationEvent(final ApplicationEvent event)
    {
        if (event instanceof ContextRefreshedEvent)
        {
            // second run - even though a metric registry may be present at spiStart
            // it's individual metrics may be lazily registered
            // there is no event listener for this
            this.registry.forEach(this::register);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void spiStart(final String igniteInstanceName) throws IgniteSpiException
    {
        LOGGER.info("Starting MicrometerMetricsExporterSpi");
        this.registry.forEach(this::register);

        this.registry.addMetricRegistryCreationListener(this::register);
        this.registry.addMetricRegistryRemoveListener(this::unregister);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void spiStop() throws IgniteSpiException
    {
        LOGGER.info("Stopping MicrometerMetricsExporterSpi");

        final List<Meter> metricsToRemove = this.meterRegistry.getMeters().stream()
                .filter(m -> this.metricNames.contains(m.getId().getName())
                        && m.getId().getTag(TAG_IGNITE_INSTANCE).equals(this.igniteInstanceName))
                .collect(Collectors.toList());
        metricsToRemove.forEach(this.meterRegistry::remove);
        this.metricNames.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMetricRegistry(final ReadOnlyMetricManager registry)
    {
        this.registry = registry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExportFilter(final Predicate<ReadOnlyMetricRegistry> filter)
    {
        this.filter = filter;
    }

    /**
     * @param meterRegistry
     *     the meterRegistry to set
     */
    public void setMeterRegistry(final MeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;
    }

    private void register(final ReadOnlyMetricRegistry metricRegistry)
    {
        if (this.filter == null || this.filter.test(metricRegistry))
        {
            final String registryName = metricRegistry.name().toLowerCase(Locale.ENGLISH);
            LOGGER.debug("Registering metrics for {} and Ignite instance {}", registryName, this.igniteInstanceName);

            for (final Metric metric : metricRegistry)
            {
                String metricName = registryName + '.' + metric.name().toLowerCase(Locale.ENGLISH);
                // replace duplicates if registry name is prefix to metric name
                metricName = metricName.replaceAll("((?:(?:[a-z]+)[\\._])+?)\\1", "$1");

                if (this.metricNames.contains(metricName))
                {
                    LOGGER.trace("Metric {} has already been registered", metricName);
                    continue;
                }

                boolean registered = false;
                if (metric instanceof IntMetric)
                {
                    this.meterRegistry.gauge(metricName, Tags.of(TAG_IGNITE_INSTANCE, this.igniteInstanceName), (IntMetric) metric,
                            intMetric -> Double.valueOf(intMetric.value()));
                    registered = true;
                    LOGGER.trace("Registered int metric {} as gauge", metricName);
                }
                else if (metric instanceof LongMetric)
                {
                    this.meterRegistry.gauge(metricName, Tags.of(TAG_IGNITE_INSTANCE, this.igniteInstanceName), (LongMetric) metric,
                            longMetric -> Double.valueOf(longMetric.value()));
                    registered = true;
                    LOGGER.trace("Registered long metric {} as gauge", metricName);
                }
                else if (metric instanceof DoubleMetric)
                {
                    this.meterRegistry.gauge(metricName, Tags.of(TAG_IGNITE_INSTANCE, this.igniteInstanceName), (DoubleMetric) metric,
                            DoubleMetric::value);
                    registered = true;
                    LOGGER.trace("Registered double metric {} as gauge", metricName);
                }
                else if (metric instanceof HistogramMetric)
                {
                    LOGGER.trace("Processing histogram metric {}", metricName);
                    // none of the micrometer histogram / distribution support meters really support this case
                    // register a bunch of individual gauges for each bound as tagged meters

                    final HistogramMetric hMetric = ((HistogramMetric) metric);
                    final long[] bounds = hMetric.bounds();
                    for (int i = 0; i < bounds.length; i++)
                    {
                        final long bound = bounds[i];
                        final int idx = i;
                        this.meterRegistry.gauge(metricName,
                                Tags.of(TAG_IGNITE_INSTANCE, this.igniteInstanceName, TAG_VALUE_BOUND, String.valueOf(bound)), hMetric,
                                histoMetric -> Double.valueOf(histoMetric.value()[idx]));
                        LOGGER.trace("Registered long metric {} as gauge", metricName);
                    }
                    registered = true;
                }
                else
                {
                    LOGGER.trace("Cannot handle metric {}", metricName);
                }

                if (registered)
                {
                    this.metricNames.add(metricName);
                }
            }
        }
    }

    private void unregister(final ReadOnlyMetricRegistry metricRegistry)
    {
        if (this.filter == null || this.filter.test(metricRegistry))
        {
            final String registryName = metricRegistry.name().toLowerCase(Locale.ENGLISH);
            LOGGER.debug("Unregistering metrics for {} and Ignite instance {}", registryName, this.igniteInstanceName);

            final String baseName = registryName + '.';

            final Set<Meter> metricsToRemove = this.meterRegistry.getMeters().stream().filter(
                    m -> m.getId().getName().startsWith(baseName) && m.getId().getTag(TAG_IGNITE_INSTANCE).equals(this.igniteInstanceName))
                    .collect(Collectors.toSet());
            metricsToRemove.forEach(m -> {
                this.metricNames.remove(m.getId().getName());
                this.meterRegistry.remove(m);
            });
        }
    }
}
