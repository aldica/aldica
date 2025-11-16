# About
This detached sub-module of the project is aimed at providing the means for general memory footprint benchmarks of default Alfresco caches and Ignite-backed caches. Using a simple content model and a set of generate / clear cache / load cache web scripts, the sub-module offers standardised, easily reproducible comparisons of the amount of memory required to support a specific number of cached nodes in both types of systems.

# Comparing Core Node Caches
The primary / only benchmark currently provided by this sub-module is aimed at comparing the memory footprints of the node aspects and properties caches between default Alfresco and aldica's Ignite-backed caches. This benchmark focuses on the caches which store unique values for each node, avoiding any caches that may hold data specific to only a subset of nodes, e.g. content data / URL caches for nodes with attached binary contents.

For this comparison, the following configurations and extensions have been bundled in this sub-module:

- custom content model bmModel.xml defining a single data aspect with a property of each scalar data type except boolean (int, long, float, double, date, datetime, text, noderef, qname)
- web script using relative URI /aldica/mem-bm/generateNodes to generate a defined number of nodes using pseudo-random property values within the allowed value ranges
    - date/datetime between January 1st 2010 and time of system start (exact time of Java class being loaded)
    - text according to a defined list-of-values constraint
    - floating numbers between 0 and 1 (exclusive)
    - integer numbers in the full value spectrum from min to max value representable by the primitive Java types
    - node references with the store set to workspace://SpacesStore and a random UUID
    - qualified names using a random namespace of pre-defined Alfresco namespaces and a random local name using a "lorem ipsum" generator
    - plain text file content using a "lorem ipsum" generator
- web script using relative URI /aldica/mem-bm/clearNodeCaches to clear caches after initial node generation / system start for a comparable base line
- web script using relative URI /aldica/mem-bm/cacheNodes to load a defined number of nodes into the caches with as little effect on other caches as possible

## Configuration
The benchmark makes use of the Docker-based integration test environment configured within the Maven project to start up two Alfresco Content Services instances with an identical set of deployed extensions. The only differences between the two instances lie in the JAVA_OPTS passed to the containers.

The instance `docker-aldica-mem-bm-repository-ignite-1` is started with the following parameters (only listing diverging settings):

- global aldica extension enablement flag (`aldica.core.enabled`) set to `true`
- default Ignite storage region
- 2 GiB heap memory

The instance `docker-aldica-mem-bm-repository-default-1` is started with the following parameters (only listing diverging settings):

- global aldica extension enablement flag (`aldica.core.enabled`) set to `false`
- 8 GiB heap memory (to accomodate for more on-heap caching)

The following parameters have been specified as common parameters for both instances, though the aldica-specific parameters only take effect in instance `docker-aldica-mem-bm-repository-ignite-1`:

- disable TTL based eviction on `nodesSharedCache`
- increase size of `nodesSharedCache` to accomodate regular and inverse lookups for 4,000,000 nodes (2 x 1,250,000 nodes for directly loaded nodes and another 1,250,000 nodes for indirect lookups performed by `NodeRefPropertyMethodInterceptor`, validating node properties of type `d:noderef` / `d:category`)
- increase sizes of `aspectsSharedCache`, `propertiesSharedCache`, `contentDataSharedCache`, `contentUrlSharedCache`, `nodeOwnerSharedCache` to accommodate 1,250,000 nodes, in order to avoid any premature eviction when loading 1,000,000 nodes
- increase sizes of `aclSharedCache`, `aclEntitySharedCache`, `readersSharedCache`, `readersDeniedSharedCache` to accommodate 500,000 ACLs (generated/applied only on folder level, not all generated nodes)
- setup Ignite data regions `nodes`, `nodeAspects`, `nodeProperties`, `contentData`, `contentUrl`, `acl` of 10 MiB (initial size) and size limit of 5 GiB in `${java.io.tmpdir}/IgniteWork/nodes` / `${java.io.tmpdir}/IgniteWork/nodeAspects` / `${java.io.tmpdir}/IgniteWork/nodeProperties` / `${java.io.tmpdir}/IgniteWork/contentData` / `${java.io.tmpdir}/IgniteWork/contentUrl` / `${java.io.tmpdir}/IgniteWork/acl` (separate region for `contentUrl` defined in order to be able to exclude it from memory usage calculations for a fair comparison, since `contentUrlSharedCache` for default Alfresco is essentially configured to be empty)
- associate aldica Ignite-backed caches `nodesSharedCache`, `aspectsSharedCache`, `propertiesSharedCache`, `contentDataSharedCache` and `contentUrlSharedCache` with the data region `nodes`, `nodeAspects`, `nodeProperties`, `contentData` and `contentUrl` respectively
- associate aldica Ignite-backed caches `nodeOwnerSharedCache`, `readersSharedCache`, `readersDeniedSharedCache`, `aclSharedCache`, `aclEntitySharedCache` with the data region `acl`

Apart from the listed changes, both instances use the same default configuration as the integration test instances in the main project sub-modules. With regards to Java heap management, this means:

- use of Garbage First (G1) garbage collector
- enabled string deduplication of G1 GC
- forced scavenge before doing a full GC cycle
- enabled parallel processing of references
- pre-touch Java heap during JVM initialisation (all pages zeroed)

## Full Execution + Data Retrieval

```
# use Maven integration test to spin up instances
cd aldica-repo-ignite-mem-bm
mvn clean integration-test

# stop default ACS instance for generation and Ignite test
docker stop docker-aldica-mem-bm-repository-default-1

# prepare DB indices to support common lookups (smooth out any differences between runs)
docker exec -ti docker-aldica-mem-bm-postgres-1 psql alfresco -U alfresco
> CREATE INDEX idx_alf_nprop_cd ON alf_node_properties (node_id, long_value) WHERE actual_type_n = 21 OR actual_type_n = 3;
> CREATE INDEX idx_alf_nprop_name ON alf_node_properties (string_value, node_id) WHERE qname_id = 26;
> VACUUM FULL ANALYZE;
> \q

# generate 1,000,000 nodes on one instance
curl -X POST -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/generateNodes?countNodes=1000000&threads=8&maxChildrenPerFolder=20&wordsPerNode=20'

# optimise DB after high-volume data generation
docker exec -ti docker-aldica-mem-bm-postgres-1 psql alfresco -U alfresco
> VACUUM FULL ANALYZE;
> \q

# restart repo in order to fully clear the Ignite caches on aldica-enabled instance
docker restart docker-aldica-mem-bm-repository-ignite-1

# run load from empty state (not measured) to pre-warm DB, code, and ensure JIT compilation
curl -X GET -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=12&preloadQNames=true'

# clear caches via OOTBee Support Tools
# run asynchronously / in background since each request will take a while
nohup curl -X POST -i -u admin:admin 'http://localhost:8180/alfresco/s/ootbee/admin/caches/contentDataSharedCache/clear' &
nohup curl -X POST -i -u admin:admin 'http://localhost:8180/alfresco/s/ootbee/admin/caches/contentUrlSharedCache/clear' &
nohup curl -X POST -i -u admin:admin 'http://localhost:8180/alfresco/s/ootbee/admin/caches/node%25dot%25nodesSharedCache/clear' &
nohup curl -X POST -i -u admin:admin 'http://localhost:8180/alfresco/s/ootbee/admin/caches/node%25dot%25propertiesSharedCache/clear' &
nohup curl -X POST -i -u admin:admin 'http://localhost:8180/alfresco/s/ootbee/admin/caches/node%25dot%25aspectsSharedCache/clear' &
nohup curl -X POST -i -u admin:admin 'http://localhost:8180/alfresco/s/ootbee/admin/caches/nodeOwnerSharedCache/clear' &
nohup curl -X POST -i -u admin:admin 'http://localhost:8180/alfresco/s/ootbee/admin/caches/aclEntitySharedCache/clear' &
nohup curl -X POST -i -u admin:admin 'http://localhost:8180/alfresco/s/ootbee/admin/caches/aclSharedCache/clear' &
nohup curl -X POST -i -u admin:admin 'http://localhost:8180/alfresco/s/ootbee/admin/caches/readersSharedCache/clear' &
nohup curl -X POST -i -u admin:admin 'http://localhost:8180/alfresco/s/ootbee/admin/caches/readersDeniedSharedCache/clear' &

# run initial measured cache load from empty state
curl -X GET -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=12&preloadQNames=true'

# take and store heap dump for analysis
docker exec -ti docker-aldica-mem-bm-repository-ignite-1 jmap -dump:format=b,live,file=/tmp/aldica-1m-nodes.hprof 1
docker cp docker-aldica-mem-bm-repository-ignite-1:/tmp/aldica-1m-nodes.hprof ./aldica-1m-nodes.hprof

# take other metrics from Ignite Admin Console pages, e.g. data region sizes, average cache get/put times
# http://localhost:8180/alfresco/s/aldica/admin/ignite-data-regions
# http://localhost:8180/alfresco/s/aldica/admin/ignite-caches

# repeat cache load to measure throughput / cache get timing from pre-filled state
curl -X GET -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=12&preloadQNames=true'

# take further metrics from Ignite Admin Console pages (updated average cache get times + cache hit ratio) 

# stop Ignite instance and start default ACS instance
docker stop docker-aldica-mem-bm-repository-ignite-1
# restart DB in between so that default ACS instance has same sequence of pre-warming and measurement
docker restart docker-aldica-mem-bm-repository-postgres-1
docker start docker-aldica-mem-bm-repository-default-1

# run load from empty state (not measured) to pre-warm DB, code, and ensure JIT compilation
curl -X GET -i 'http://localhost:8280/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=12&preloadQNames=true'

# clear caches via OOTBee Support Tools
curl -X POST -i -u admin:admin 'http://localhost:8280/alfresco/s/ootbee/admin/caches/contentDataSharedCache/clear'
curl -X POST -i -u admin:admin 'http://localhost:8280/alfresco/s/ootbee/admin/caches/contentUrlSharedCache/clear'
curl -X POST -i -u admin:admin 'http://localhost:8280/alfresco/s/ootbee/admin/caches/node%25dot%25nodesSharedCache/clear'
curl -X POST -i -u admin:admin 'http://localhost:8280/alfresco/s/ootbee/admin/caches/node%25dot%25propertiesSharedCache/clear'
curl -X POST -i -u admin:admin 'http://localhost:8280/alfresco/s/ootbee/admin/caches/node%25dot%25aspectsSharedCache/clear'
curl -X POST -i -u admin:admin 'http://localhost:8280/alfresco/s/ootbee/admin/caches/nodeOwnerSharedCache/clear'
curl -X POST -i -u admin:admin 'http://localhost:8280/alfresco/s/ootbee/admin/caches/aclEntitySharedCache/clear'
curl -X POST -i -u admin:admin 'http://localhost:8280/alfresco/s/ootbee/admin/caches/aclSharedCache/clear'
curl -X POST -i -u admin:admin 'http://localhost:8280/alfresco/s/ootbee/admin/caches/readersSharedCache/clear'
curl -X POST -i -u admin:admin 'http://localhost:8280/alfresco/s/ootbee/admin/caches/readersDeniedSharedCache/clear'

# run initial cache load from empty state on server with default Alfresco caching
curl -X GET -i 'http://localhost:8280/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=12&preloadQNames=true'

# take and store heap dump for analysis
docker exec -ti docker-aldica-mem-bm-repository-default-1 jmap -dump:format=b,live,file=/tmp/alfresco-1m-nodes.hprof 1
docker cp docker-aldica-mem-bm-repository-default-1:/tmp/alfresco-1m-nodes.hprof ./alfresco-1m-nodes.hprof

# repeat cache load to measure throughput from pre-filled state
curl -X GET -i 'http://localhost:8280/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=12&preloadQNames=true'
```

In order to test the various levels of [binary serialisation optimisations](./Concept-BinarySerialiser.md) available with the aldica module, it would be necessary to repeat the test runs for the aldica-enabled instance by
- stopping + removing all containers via `mvn clean`
- reconfiguring the `JAVA_OPTS` for the aldica-enabled instance in the `./aldica-repo-ignite-mem-bm/src/main/docker/docker-compose.yaml` (e.g. by adding `-Daldica.core.binary.optimisation.enabled=false` to disable all but the most basic optimisations or `-Daldica.core.binary.optimisation.useIdsWhenReasonable=false`/`-Daldica.core.binary.optimisation.useIdsWhenPossible=false` to disable the more aggressive ID substitution optimisations)
- (alternatively) the `./aldica-repo-ignite-mem-bm/target/classes/docker/docker-compose.yaml` may be manually modified and the container be re-instantiated/updated via `docker compose -f target/classes/docker/docker-compose.yaml up -d aldica-mem-bm-repository-ignite`
- rerunning the test except for the initial step to generate the 1 million nodes

## Results

### Collected Metrics

- Version: aldica 1.1.0
- Date of benchmark: 2025-11-01
- System: Lenovo T14 Gen 3, Intel Core i7-1270P @ 2.20 GHz, 12 Core (16 logical processors), 48 GiB RAM, Windows 11, Docker engine on Ubuntu via WSL2 (10 Cores, 24 GiB RAM assigned), SAMSUNG MZVL21T0HCLR 1 TiB SSD
- Concurrent threads: 12

| Measure | Alfresco 25.2.0 | aldica (medium opt) | aldica (max opt) | aldica (max opt + no swap) | aldica (min opt) | aldica 1.0.1 @ ACS 6.2.0-ga* |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Heap (Xmx) | 8 GiB | 2 GiB | 2 GiB | 2 GiB | 2 GiB | 4 GiB |
| Heap (total)* | 3.44 GiB | 234.25 MiB | 235.25 MiB | 241.00 MiB | 224.00 MiB | 1.33 GiB |
| `nodesSharedCache` | 849.75 MiB | 634.00 MiB | 637.75 MiB | 633.50 MiB | 795.25 MiB | 1.11 GiB |
| `nodeAspectsCache` | 359.00 MiB | 218.25 MiB | 153.00 MiB | 153.25 MiB | 328.25 MiB | 201.75 MiB |
| `nodePropertiesCache` | 1.03 GiB | 805.00 MiB | 307.00 MiB | 307.00 MiB | 1.29 GiB | 483.00 MiB |
| `contentDataCache` | 176.00 MiB | 232.50 MiB | 217.25 MiB | 215.50 MiB | 261.00 MiB | 247.00 MiB |
| `contentUrlCache` | 271 MiB | 402.75 MiB | 402.50 MiB | 402.50 MiB | 429.75 MiB | 450.50 MiB |
| `acl`* | 222.00 | ... | 208.75 MiB | ... | ... | ... |
| `defaultDataRegion`* | N/A | 50.75 MiB | 50.75 MiB | 58.25 MiB | 50.75 MiB | 46.00 MiB |
| Memory (used)* | 3.44 GiB | ... | 2.17 GiB | ... | ... | ... |
| Memory reduction (used) | N/A | ... | 37% | ... | ... | ... |
| Memory (eff)* | 8.6 GiB | ... | 2.94 GiB (2.51 GiB) | ... | ... | ... |
| Memory reduction (eff)* | N/A | ... | 65.8% (70.8%) | ... | ... | ... |
| Avg. throughput - initial load | 2187/s | 2125/s | 1811/s | 2326/s | 1999/s | 1750/s |
| Peak avg. throughput - initial load | 3001/s | 2139/s | 2172/s | 2660/s | 2033/s | 2604/s |
| Avg. throughput - 2nd load | 3868/s | 4535/s | 3982*/s | 6517/s | 4487/s | 4437/s |
| Peak avg. throughput - 2nd load | 6156/s | 6413/s | 5773*/s | 7531/s | 6243/s | 6286/s |

Notes:
- memory values generally rounded up to the nearest quarter MiB (fluctuations typically +/- 0.10-0.30 MiB in repeated identical runs)
- the database was restarted between runs with different configurations
- both Garbage Collection and database performance may have large impacts on the overall performance of test runs
    - decreasing heap size for aldica tests by an additional 2 GiB reduced throughput in initial load by 15% due to GC overhead
    - according to application performance metrics, JDBC queries (even if just running the FTS queries to locate the nodes to cache) account for 35 to 50% of overall execution time
    - if runnning half a dozen to a dozen tests with different configurations without restarting DB, throughput was observed to suddenly increase by ~30% both in initial and 2nd loads (likely due to query planner optimising for frequent query + parameter combinations), making comparison difficult
- disk IO performance may have large impacts on test runs for swap-enabled aldica instances
- test measurements on different days showed varying degress of differences, likely depending on the state of Docker host
    - all measurements were obtained in a single session without starting/stopping major services/applications on Docker host
- Heap (total)*: Heap memory used as reported in a Java heap memory dump of live objects while in idle state
- Memory (used)*: Total comparable memory - heap and off-heap memory
- Memory (eff)*: Effective memory allocated, taking into account required heap memory size so that "Heap (total)" accounts for only "Old Generation" using standard Java G1 GC sizing defaults (max. ~40%, but at least 1 GiB total heap)
    - this does not take into account the heap used for the benchmark test, only the heap that would be necessary to support the idle state of memory usage with all test data loaded into caches
    - if the 1 GiB total heap baseline is enforced, a value in parentheses gives the value calculated from from the actually used heap based on relative usage of 40% without potentially triggering mixed GC
- `acl`*: contains miscellaneous ACL-related data, mostly data of the `nodeOwnerSharedCache`, `readersSharedCache`, `aclSharedCache`, `aclEntitySharedCache`
- `defaultDataRegion`*: contains miscellaneous off-heap data, mostly data of the `immutableEntitySharedCache`, other Alfresco caches that are barely used in this benchmark, and internal Ignite infrastructure/metadata data
- aldica 1.0.1 @ ACS 6.2.0-ga*: for a reasonable comparison with older version(s) of aldica / ACS, using default/maximum optimisations
    - the `nodesSharedCache` in this version is not an aldica off-heap cache
    - none of the ACL-related caches in this version were optimised with regards to serialisation / memory footprint
    - any previous benchmarks using aldica 1.0.1 on ACS 6.1.2 cannot be taken as reference, due to differences ranging from test machine hardware to revised and improved test + comparison procedure (specifically concerning the extent of warm-up before measured tests and inclusion of ACL-related structures)

Analysis remarks:
- default ACS `contentDataCache` accounts for less heap memory than the equivalent aldica off-heap caches use because cached content data is technically shared between `contentDataCache` and `nodePropertiesCache` - since some of the aldica optimisations include content data ID substitution in `nodePropertiesCache` and we look at the overall memory use, this bit of fuzzy/shared memory ownership is perfectly fine for the benchmark
- default ACS `nodeAspectsCache` and `nodePropertiesCache` sizes do not include full cost for `QName` instances of aspect and property names, which are shared with the `immutableEntitySharedCache` (contained in the total heap used), while aldica caches would include the full cost if it wasn't for the serialisation optimisations
- aldica 1.1.0 added significant memory optimisation improvements compared to version 1.0.1, doubling the relative reduction of used memory
- despite aldica needing to de-serialise cache values on read access, the 2nd load average throughput (read-only cache use) of the slowest aldica optimisation variants is still only 10-12% lower than normal ACS, and with maximum optimisations enabled is slightly higher or up to 30% higher when pure memory (swap-less) caches are used
    - the difference in performance between the aldica optimisations modes very likely relate to the amount of memory used to represent cache values, since less memory needs to be stored and/or swapped in/out of memory - even though the optimisations add logical complexity, the overall cost of execution is still reduced enough to be a net positive
    - enabling the maximum of optimisations in the aldica default configuration offers the best combination of memory reduction and performance
    - using a file-backed swap-enabled default data region in aldica makes a compromise between performance and allowing caches larger than the total available memory
    - the high peak average throughput of normal ACS shows that its heap-focussed caching could be much faster with even more heap allocated to ACS to reduce GC pressure, but this would further increase the effective memory necessary for the same amount of cached entries when the memory comparison is already at a huge disadvantage for normal ACS
- operations with cache writes (initial load) are generally slower in aldica due to the serialisation overhead, but show a similar correlation with improved performance with more extensive optimisations due the smaller memory footprint of serialised cache values
    - with the aldica default configuration, the initial load is only 7% slower in throughput than normal ACS, which sort-of balances out with a 6% higher throughput in the 2nd load for read-only cache access, which - through repeated cache reads - more than makes up for the initial overhead
    - writes in swap-enabled aldica are not any slower than in a memory-only configuration - this is due to writes to disk/swap being handled asynchronously by the OS (via memory-mapped files), adding no immediate overhead during the load benchmark
- the more aggressive serialisation optimisations are extremely effective for deeply-nested / complex structures
    - each inlined object saves around 20 bytes for the Ignite object header - `nodesPropertiesCache` with its map of properties with an arbitrary amount of key/value-pairs and diverse types of values benefits immensely from this already with the medium set of optimisations
    - in various cases, the `null`-ness of multiple inlined fields in a cached value or other serialisation metadata (e.g. unsigned nature of reference IDs/numeric values) is aggregated into bit maps, saving 1-2 bytes per nullable field and/or ID reference/numeric field
    - with minimal optimisations alone, `QName` instances (aspect or property names) are typically less than half the size as they would be with Ignite's default serialisation as well-known namespace URIs (static ACS namespaces) are substituted with a single byte), or `StoreRef` instances used as part of `NodeRef`s are typically written in a single byte instead of the separate `protocol` and `identifier`
    - with maximum optimisations `QName` instances may often be written in only 1-2 bytes (inlined) + 1-2 flag bits for any `QName` whose corresponding `id` in the `alf_qname` table is between -8192 and 8191, and at most 4 bytes + 1-2 flag bits unless the `id` is lower than `-536870912` / higher than `536870911` (both unrealistic)

### Collected Metrics (Previous Releases)

- Date of benchmark: 2020-06-30 / 2020-07-01
- System: Lenovo T460p, Intel Core i7-6700HQ @ 2.60 GHz, 4 Core, 32 GiB RAM, Windows 10, Docker for Desktop (2 CPU, 14 GiB RAM assigned), SAMSUNG MZ7LN512HMJP 512 GiB SSD
- Concurrent threads: 6

| Measure | Alfresco 6.1.2 | aldica (medium opt) | aldica (default / max opt) | aldica (min opt) |
| :--- | ---: | ---: | ---: | ---: |
| Heap (Xmx) | 6 GiB | 4 GiB | 4 GiB | 4 GiB |
| Heap (total)* | 3 GiB | 1.3 GiB | 1.3 GiB | 1.3 GiB |
| `nodesSharedCache` | 984.4 MiB | 1.1 GiB | 1.1 GiB | 1.1 GiB |
| `nodeAspectsCache` | 359 MiB | 200.2 MiB | 199.9 MiB | 331 MiB |
| `nodePropertiesCache` | 1 GiB | 621.1 MiB | 480.9 MiB | 1.1 GiB |
| `contentDataCache` | 175.9 MiB | 247.5 MiB | 247.5 MiB | 297 MiB |
| Memory (used)* | 3 GiB | 2.35 GiB | 2.2 GiB | 3 GiB |
| Memory reduction (used) | N/A | 21% | 26% | 0% |
| Memory (eff)* | 6 GiB | 5.05 GiB | 4.9 GiB | 5.71 GiB |
| Memory reduction (eff)* | N/A | 16% | 18% | 5% |
| Avg. throughput - initial load | 424/s | 427/s | 426/s | 442/s |
| Peak avg. throughput - initial load | 441/s | 460/s | 443/s | 484/s |
| Avg. throughput - 2nd load | 1274/s | 1654/s | 1913/s | 1605/s |
| Peak avg. throughput - 2nd load | 1276/s | 1654/s | 1913/s | 1651/s |

Notes:
- Heap (total)*: Heap memory used as reported in a Java heap memory dump of live objects while in idle state
- Memory (used)*: Total comparable memory - heap and off-heap memory - excluding the aldica `contentUrl` data region, which holds data excluded from caching in Alfresco on-heap caches, as that cache is filled only once and never used again in the benchmark - cached data is only relevant for actual content access / download, which was out-of-scope
- Memory (eff)*: Effective memory allocated, taking into account maximum allocated heap memory at which both systems have a similar amount of remaining available heap for actual operations (3 GiB vs. 2.7 GiB)

### Memory Analysis
In the heap dump of both instances, the biggest object by retained heap memory is the `nodesSharedCache`, taking 980 MiB to 1,1 GiB of heap memory. Due to Alfresco design flaws (mutable state in cache entries and usage patterns relying on server-local object semantics), this cache cannot be supported by an Ignite-backed cache. The noticeable difference in cache size can be explained by value sharing between the on-heap `nodesSharedCache` and `nodePropertiesCache` instances in default - due to our test model having a single property of type `d:noderef`, the `NodeRefPropertyMethodInterceptor` is triggered, which performs an existence check on the node identified by the property value, causing a cache entry to be created in `nodesSharedCache` with a key used as a value in `nodePropertiesCache`, so both caches essentially split ownership and thus cost. In the aldica systems, this cannot occur due to serialisation in the off-heap `nodePropertiesCache`, so the `nodesSharedCache` bears the full heap cost..

Tied for the largest cache and/or first runner up for the largest cache in our benchmark is the `nodePropertiesCache`, with great variability in the aldica systems as the different optimisation levels in binary serialisation are applied. Specifically, by using dynamic value substitution for sub-value structures handled in secondary caches, this cache currently achieves a 57% reduction in footprint between the least and most aggressive optimisation configurations.

The on-heap `nodeAspectsCache` in the Alfresco system surprisingly is always larger than the corresponding aldica off-heap variants. The data model used in our benchmark was very simplistic, effectively using only two aspects per node - `cm:auditable` and `bm:benchmarkProperties`. This should have allowed the on-heap cache to capitalise on value sharing with the same `QName` instance stored in the `immutableEntitySharedCache` (entire cache totals ~70 KiB of shared `QName`, `Locale`, namespace URIs, encoding and mimetype values). The analysis of the heap dump shows that while `cm:auditable` is in fact referenced by a million cache entries, the qualified name for `bm:benchmarkProperties` seems to have at least 192 distinct instances, which can be traced via the `byte[]` array of characters that was deduplicated by the G1 GC algorithm, and randomly checked instances are used at most 150 times. It is assumed that in our specific benchmark procedure which uses concurrent processes to load data into caches, Alfresco `TransactionalCache`'s handling of freshly loaded entries fails to determine which concurrently loaded instance of the qualified name to put into the `immutableEntitySharedCached`, and in the end, each loaded entry is only used until invalidated / overruled. The observed 150 times reuse of instances also aligns with the product of the number of concurrent thread and the size of concurrently loaded batches (6 threads @ 25 nodes per batch = 150).
Additionally, the extensive map, segment, key and value holder instances used for on-heap caches add a overhead which is quite significant if the value of the entry itself should consist of effectively two object handles of 24 bytes each (assuming compressed oops are used). By using value substitution for qualified names, the aldica off-heap cache reduces the memory footprint by 44%, and even with the most basic optimisations - eliminating unnecessary fields from the serial form - it is 7% smaller.

**Addendum**: Code analysis of Alfresco's `TransactionalCache` and default cache configurations has revealed [a systemic flaw](https://github.com/aldica/aldica/issues/35) which causes the `immutableEntitySharedCache` to be unstable with regards to the `QName` lookup for the benchmark's custom model. This in turn causes the `QName` instances for the aspect and properties of the model to not be properly reused in on-heap caches, and skews the memory footprint for the Alfresco default caches to be higher. A re-run of the benchmark for Alfresco default caches with the extra URL parameter `preloadQNames=true` has shown that this issue did not skew the memory results of the benchmark in any meaningful manner. The use of Garbage First (G1) garbage collection and its String deduplication feature did reduce the overhead to the negligible cost of the superflous `QName` instance object pointers. Assuming that a single of the duplicated instances was reused for 150 node aspect sets, the overhead for aspects would amount to a maximum of \~470 KiB for the object pointers to `QName` instances and the nested two `String` instances for `namespaceURI` and `localName`. If we expand the assumption of duplicated `QName` instances to the properties of nodes, the overhead would increase to a maximum of \~4.6 MiB in total. 

With regards to the `contentDataCache`, the aldica off-heap cache consistently uses more memory than the on-heap variant in Alfresco default. This may be a false impression though as the `ContentDataWithId` values stored in this cache are reused as sub-values in the `nodePropertiesCache` and thus the retained heap memory reported by the heap analysis tools are likely skewed. But it is indeed the case that `ContentDataWithId` is difficult to optimise as the majority of its cost is determined by the content URL string. The application of value substitution on the `Locale`, encoding and mimetype fragments of `ContentDataWithId` at least is able to reduce the footprint by 17% between the minimum and default / most aggressive serialisation optimisation options..

### Throughput Analysis
Though originally not a goal of the memory benchmark, it provides an easy and effective means to determine the performance / throughput difference between Alfresco default caching and aldica, as well between the various optimisation levels that aldica provides with regards to key / value serialisation. The throughput values can be extracted from the `alfresco.log` by checking the output of the cache load batch process.

In all benchmark tests, the initial load showed a generally consistent throughput no matter what caching technology or optimsiation level was used. This clearly indicates that the primary bootleneck in this case is the retrieval of data from the database, and any differences in cache PUT performance, e.g. due to serialisation in aldica caches, are mostly drowned out. Still, aldica caches appear to have a negligible advantage considering all their throughput values are consistently slightly higher. Since we do not perform a start-to-end tracing of each batches performance, it is impossible to determine if this may be the result of a slow-down towards the end of the load operation for default Alfresco caches, when the heap usage starts to get into ranges where more frequent / costly GC cycles may be expected.

When running the load operation a second time once all the caches have already been initialised with data, the aldica caches show a clear advantage with 25 to 50% higher throughput than the Alfresco default caches, despite involving more processing logic with deserialisation. Though not fully analysed as of yet, it is assumed that the observed differences compared to Alfresco default is the result of reduced GC overhead, since aldica instances use only ~35% of assigned heap while Alfresco default uses 50%. The differences between the various optimisation levels of aldica binary serialisation may be a result of fewer overhead in off-heap memory access and OS-level swapping, which appears to more than compensate for the expected cost of more complex serialisation / deserialisation logic. **Further benchmarks need to be conducted** without using OS-level swapping for Ignite data regions to investigate this assumed impact on throughput.

## Remarks
### Effective Cost of On-Heap Memory
The effective cost of on-heap memory is typically higher than the measured amount of used memory. In order to compensate for overhead effects introduced by various garbage collection mechanisms, each 1 MiB of used heap memory should be considered as requiring an increase of the overall heap size (Xms/Xmx parameters) of 1.2 (G1) - 1.5 MiB (CMS), unless low-level JVM GC parameters are used to fine tune the algorithms accordingly. These factors are influenced e.g. by the relative amount of memory that the GC algorithm tries to keep "free" as a buffer for moving data during GC cycles, the relative sizing of memory generations (Young (Eden/Survivor) and Old in CMS), or GC initiation thresholds, which can cause noticable performance impact when tripped, and more intensive / aggressive GC cycles are run.

### Deduplication / Value Optimisationn
The heap memory usage of the default Alfresco cache `propertiesSharedCache` would be even higher if a different GC algorithm was used or the string deduplication of G1 GC was not enabled. Each textual property loaded from the database into caches represents a distinct string instance. Despite the textual property in this benchmark using a list-of-values constraint to constrain the set of used strings to only 9 distinct values, the effective number of strings for 1,000,000 nodes is 1,000,000. With G1 GC string deduplication enabled, the number of instances does not change, but the garbage collection mechanism transparently deduplicates the backing `byte[]` of these instances, which contain the actual character data. As a result, each string instance "only" costs the  overhead of the string wrapper instance (24 byte with compressed oops). With string deduplication disabled, each instance would add the cost of its character data to that overhead. Considering the cost of 32 bytes of character data per string instance in the list of values constraint, this would mean an additional 30 MiB of on-heap memory used for `propertiesSharedCache`, just for the single text property in our custom content model. Considering that string deduplication also affects all instances of qualified names used as identifiers for aspects and properties, the overall savings by having G1 GC string deduplication active will be even more substantial. The impact of G1 GC string deduplication is generally estimated at about 10 % of the overall heap memory usage in typical Java applications.

Since Ignite-backed caches save data off heap and outside the reach of deduplication mechanisms of the garbage collection algorithm, they do not benefit from this feature. Using a naive implementation, these caches might actually be less efficient than default caches in terms of memory used, as a simple serialisation of the Java object structure would include a lot of wrapper instances (i.e. Map structures) and result in redundant data being written to memory, including redundant identifiers, such as qualified names for properties. In order to avoid such overhead, the [serialisation mechanism](./Concept-BinarySerialiser.md) used for Ignite-backed caches applies various optimisations during (de-)serialisation, such as:

- replacing well-known values (namespaces in `QName`, store references or just store protocols in `NodeRef`, `Locale` in `MLText` / `ContentDataWithId` / `ContentData`, encodings and mimetypes in `ContentDataWithId` / `ContentData`
- replacing qualified name instances (aspects / property identifiers) with their database ID
- replacing content data instances (property values) with their database ID (optional)
- writing serialised objects in a raw serial format without structural metadata

Despite these optimisations, there are various data redundancies / duplications that are built into the design of specific Alfresco caches, which cannot be fully compensated. E.g. `ContentData` instances are stored both in the `propertiesSharedCache` and `contentDataSharedCache`, while a fragment of these instances, the content URL, is additionally also stored in the `contentUrlSharedCache` - similarly, the immutable value objects for `QName`, `Locale`, `NodeRef` / `StoreRef`, which are used as keys in caches or cache entry value structures, are shared across potentially many caches and/or cache entries. While this poses no issue with the on-heap caching in default Alfresco, where value instances can be shared or (partially) deduplicated by the GC algorithm, the serialised off-heap cache entries in Ignite-backed caches have to store the same value twice or more, depending on how many entries reference the same value. This is one of the reasons why aldica includes [binary serialisation optimisations](./Concept-BinarySerialiser.md) to transparently address and limit such redundancies by substituting (sub-)entites with their IDs.

### Comparison with Alfresco Enterprise

The benchmark can also be run against Alfresco Content Services in the Enterprise Edition. Since aldica does not support running in an Enterprise Edition instance, the benchmark can only be run with two clustered default ACS EE instances, instead of the default comparison setup with an Ignite-enabled and a default instance. In order to run with Enterprise Edition, the following changes need to be performed:

- modify sub-module pom.xml to enable use of an alternative base Docker image via the module's &lt;properties&gt;
- modify src/test/docker/Repository-Dockerfile to adapt USER / RUN directives for building an Enterprise-based image
- modify src/test/repository-it.xml to exclude aldica libraries and dependencies

All files that need to be modified contain comments to indicate the required changes.

When run with ACS EE 6.2.0, this benchmark shows similar memory usage patterns as with the default ACS Community instance. This is to be expected as all caches use the same default cache implementation. Both `aspectsSharedCache` and `propertiesSharedCache` are not cluster-enabled at all in Enterprise Edition, and the `nodesSharedCache` only uses a thin facade to support Hazelcast-backed remote invalidation while the actual data structure is handled by the default cache implementation. 