/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.compute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.spi.IgniteSpiAdapter;
import org.apache.ignite.spi.IgniteSpiContext;
import org.apache.ignite.spi.metric.BooleanMetric;
import org.apache.ignite.spi.metric.DoubleMetric;
import org.apache.ignite.spi.metric.IntMetric;
import org.apache.ignite.spi.metric.LongMetric;
import org.apache.ignite.spi.metric.MetricExporterSpi;
import org.apache.ignite.spi.metric.ReadOnlyMetricRegistry;

/**
 * Instances of this are used to perform lookups of data region metrics for a grid node.
 *
 * @author Axel Faust
 */
public class DataRegionMetricsLookup implements IgniteCallable<Map<String, Object>>
{

    private static final long serialVersionUID = 5422208737004731609L;

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> call() throws Exception
    {
        final Map<String, Object> nodeRegionMetrics = new HashMap<>();
        final Ignite localIgnite = Ignition.localIgnite();
        nodeRegionMetrics.put("node", localIgnite.cluster().localNode());

        IgniteSpiContext spiContext = null;

        final IgniteConfiguration configuration = localIgnite.configuration();
        for (final MetricExporterSpi metricExporterSpi : configuration.getMetricExporterSpi())
        {
            if (metricExporterSpi instanceof IgniteSpiAdapter)
            {
                spiContext = ((IgniteSpiAdapter) metricExporterSpi).getSpiContext();
                break;
            }
        }

        final List<DataRegionConfiguration> dataRegionConfigs = new ArrayList<>();
        final DataStorageConfiguration dataStorageConfiguration = configuration.getDataStorageConfiguration();
        dataRegionConfigs.add(dataStorageConfiguration.getDefaultDataRegionConfiguration());
        dataRegionConfigs.addAll(Arrays.asList(dataStorageConfiguration.getDataRegionConfigurations()));

        final List<Map<String, Object>> dataRegionMetrics = new ArrayList<>();
        for (final DataRegionConfiguration dataRegionConfig : dataRegionConfigs)
        {
            final Map<String, Object> regionMetric = new HashMap<>();
            String regionName = dataRegionConfig.getName();

            final ReadOnlyMetricRegistry dataRegionMetricRegistry = spiContext.getOrCreateMetricRegistry("io.dataregion." + regionName);

            if (regionName.indexOf('.') != -1)
            {
                regionName = regionName.substring(regionName.lastIndexOf('.') + 1);
            }
            regionMetric.put("name", regionName);
            dataRegionMetricRegistry.forEach(m -> {
                String name = m.name();
                name = name.substring(name.lastIndexOf('.') + 1);
                name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
                if (m instanceof LongMetric)
                {
                    regionMetric.put(name, ((LongMetric) m).value());
                }
                else if (m instanceof BooleanMetric)
                {
                    regionMetric.put(name, ((BooleanMetric) m).value());
                }
                else if (m instanceof DoubleMetric)
                {
                    regionMetric.put(name, ((DoubleMetric) m).value());
                }
                else if (m instanceof IntMetric)
                {
                    regionMetric.put(name, ((IntMetric) m).value());
                }
            });
            dataRegionMetrics.add(regionMetric);
        }
        nodeRegionMetrics.put("dataRegionMetrics", dataRegionMetrics);

        return nodeRegionMetrics;
    }

}
