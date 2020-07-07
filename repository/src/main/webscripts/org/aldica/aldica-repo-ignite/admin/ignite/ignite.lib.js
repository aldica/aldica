/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

function buildInstanceInfo(instance)
{
    var instanceInfo, cluster, ArrayList, HashSet, BigDecimal, cpus, heap, nonHeap, nodes, n, nodeMetrics;

    cluster = instance.cluster();
    ArrayList = Packages.java.util.ArrayList;
    HashSet = Packages.java.util.HashSet;
    BigDecimal = Packages.java.math.BigDecimal;

    cpus = 0;
    heap = BigDecimal.ZERO;
    nonHeap = BigDecimal.ZERO;

    nodes = new ArrayList(cluster.nodes());

    for (n = 0; n < nodes.size(); n++)
    {
        nodeMetrics = nodes.get(n).metrics();
        cpus += nodeMetrics.totalCpus;
        heap = heap.add(BigDecimal.valueOf(nodeMetrics.heapMemoryCommitted));
        nonHeap = nonHeap.add(BigDecimal.valueOf(nodeMetrics.nonHeapMemoryCommitted));
    }

    instanceInfo = {
        name : instance.name(),
        numberOfCaches : instance.cacheNames().size(),
        numberOfGridNodes : cluster.forServers().nodes().size(),
        numberOfCPUs : cpus,
        heapMemory : heap,
        nonHeapMemory : nonHeap,
        topologyVersion : cluster.topologyVersion()
    };

    return instanceInfo;
}

function buildCacheInfo(instanceName, cache, propertyGetter)
{
    var CacheConfiguration, cacheConfig, localMetrics, evictionManager, evictionPolicy, configuredType, cacheInfo;

    CacheConfiguration = Packages.org.apache.ignite.configuration.CacheConfiguration;
    cacheConfig = cache.getConfiguration(CacheConfiguration);
    localMetrics = cache.localMetrics();
    evictionManager = cache.context().evicts();
    evictionPolicy = evictionManager !== null ? evictionManager.evictionPolicy : null;

    configuredType = '<not set>';
    configuredType = propertyGetter('cache.' + cache.name + '.' + 'cluster.type') || configuredType;
    configuredType = propertyGetter('cache.' + cache.name + '.' + 'ignite.cache.type') || configuredType;

    cacheInfo = {
        grid : instanceName,
        name : cache.name,
        definedType : configuredType,
        type : String(cacheConfig.cacheMode).toLowerCase(),
        metrics : localMetrics,
        evictionPolicy : evictionPolicy
    };

    return cacheInfo;
}

function buildPropertyGetter(ctxt)
{
    var globalProperties, placeholderHelper, propertyGetter;

    globalProperties = ctxt.getBean('global-properties', Packages.java.util.Properties);
    placeholderHelper = new Packages.org.springframework.util.PropertyPlaceholderHelper('${', '}', ':', true);

    propertyGetter = function(propertyName)
    {
        var propertyValue;

        propertyValue = globalProperties[propertyName];
        if (propertyValue)
        {
            propertyValue = placeholderHelper.replacePlaceholders(propertyValue, globalProperties);
        }

        return propertyValue;
    };

    return propertyGetter;
}

/* exported buildInstances */
function buildInstances()
{
    var allInstances, instanceInfos, i;

    allInstances = Packages.org.apache.ignite.Ignition.allGrids();
    instanceInfos = [];

    for (i = 0; i < allInstances.size(); i++)
    {
        instanceInfos.push(buildInstanceInfo(allInstances.get(i)));
    }

    model.instanceInfos = instanceInfos;
}

/* exported buildCaches */
function buildCaches()
{
    var ctxt, propertyGetter, ArrayList, instances, allInstances, cacheInfos, i, cacheNames, j, cache;

    ctxt = Packages.org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
    propertyGetter = buildPropertyGetter(ctxt);

    ArrayList = Packages.java.util.ArrayList;

    if (args.instance !== undefined && args.instance !== null && String(args.instance) !== '')
    {
        instances = [ Packages.org.apache.ignite.Ignition.ignite(args.instance) ];
    }
    else
    {
        allInstances = Packages.org.apache.ignite.Ignition.allGrids();
        instances = [];
        for (i = 0; i < allInstances.size(); i++)
        {
            instances.push(allInstances.get(i));
        }
    }

    cacheInfos = [];
    for (i = 0; i < instances.length; i++)
    {
        cacheNames = new ArrayList(instances[i].cacheNames());

        for (j = 0; j < cacheNames.size(); j++)
        {
            cache = instances[i].cache(cacheNames.get(j));
            cacheInfos.push(buildCacheInfo(instances[i].name(), cache, propertyGetter));
        }
    }

    cacheInfos.sort(function(a, b)
    {
        var result;
        result = a.grid.localeCompare(b.grid);
        if (result === 0)
        {
            result = a.name.localeCompare(b.name);
        }
        return result;
    });

    model.cacheInfos = cacheInfos;
}

/* exported buildRegions */
function buildRegions()
{
    /* global gridRegionMetrics: false */
    var instances, allInstances, i, localGridRegionMetrics, gridRegionMetricsModel, localNodeRegionMetricsModel;

    if (gridRegionMetrics)
    {
        model.gridRegionMetrics = gridRegionMetrics;
    }
    else
    {
        if (args.instance !== undefined && args.instance !== null && String(args.instance) !== '')
        {
            instances = [ Packages.org.apache.ignite.Ignition.ignite(args.instance) ];
        }
        else
        {
            allInstances = Packages.org.apache.ignite.Ignition.allGrids();
            instances = [];
            for (i = 0; i < allInstances.size(); i++)
            {
                instances.push(allInstances.get(i));
            }
        }

        localGridRegionMetrics = [];
        for (i = 0; i < instances.length; i++)
        {
            gridRegionMetricsModel = {
                grid : instances[i].name(),
                gridNodeRegionMetrics : []
            };

            localNodeRegionMetricsModel = {
                node : instances[i].cluster().localNode(),
                dataRegionMetrics : instances[i].dataRegionMetrics()
            };
            gridRegionMetricsModel.gridNodeRegionMetrics.push(localNodeRegionMetricsModel);
            localGridRegionMetrics.push(gridRegionMetricsModel);
        }
        model.gridRegionMetrics = localGridRegionMetrics;
    }
}
