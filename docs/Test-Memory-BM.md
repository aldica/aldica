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

The instance `aldica-mem-bm-repository-test-1` is started with the following parameters (only listing diverging settings):

- global aldica extension enablement flag (`aldica.core.enabled`) set to `true`
- default Ignite storage region
- 4 GiB heap memory

The instance `aldica-mem-bm-repository-test-2` is started with the following parameters (only listing diverging settings):

- global aldica extension enablement flag (`aldica.core.enabled`) set to `false`
- 6 GiB heap memory (to accomodate for more on-heap caching)

The following parameters have been specified as common parameters for both instances, though the aldica-specific parameters only take effect in instance `aldica-mem-bm-repository-test-1`:

- disable TTL based eviction on `nodesSharedCache`
- increase size of `nodesSharedCache` to accomodate regular and inverse lookups for 4,000,000 nodes (2 x 1,250,000 nodes for directly loaded nodes and another 1,250,000 nodes for indirect lookups performed by `NodeRefPropertyMethodInterceptor`, validating node properties of type `d:noderef` / `d:category`)
- increase sizes of `aspectsSharedCache`, `propertiesSharedCache`, `contentDataSharedCache` to accommodate 1,250,000 nodes, in order to avoid any premature eviction when loading 1,000,000 nodes
- limit size of `contentUrlSharedCache` to a maximum of one entry in order to correctly account for on-heap cost of (shared) `ContentData` instances in `propertiesSharedCache` and `contentDataSharedCache` via heap dump analysis (content URL string shared with content URL cache may not be properly accounted for otherwise)
- setup a Ignite data regions `nodeAspects`, `nodeProperties`, `contentData`, `contentUrl` of 10 MiB (initial size) and size limit of 5 GiB in `${java.io.tmpdir}/IgniteWork/nodeAspects` / `${java.io.tmpdir}/IgniteWork/nodeProperties` / `${java.io.tmpdir}/IgniteWork/contentData`/ `${java.io.tmpdir}/IgniteWork/contentUrl` (separate region for `contentUrl` defined in order to be able to exclude it from memory usage calculations for a fair comparison, since `contentUrlSharedCache` for default Alfresco is essentially configured to be empty)
- associate aldica Ignite-backed caches `aspectsSharedCache`, `propertiesSharedCache`, `contentDataSharedCache` and `contentUrlSharedCache` with the data region `nodeAspects`, `nodeProperties`, `contentData` and `contentUrl` respectively

Apart from the listed changes, both instances use the same default configuration as the integration test instances in the main project sub-modules. With regards to Java heap management, this means:

- use of Garbage First (G1) garbage collector
- enabled string deduplication of G1 GC
- forced scavenge before doing a full GC cycle
- enabled parallel processing of references
- pre-touch Java heap during JVM initialisation (all pages zeroed)

## Full Execution + Data Retrieval

```
# use Maven integration test to spin up instances
mvn clean integration-test -Ddocker.tests.enabled=true

# generate 1,000,000 nodes on one instance
curl -X POST -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/generateNodes?countNodes=1000000&threads=4&maxChildrenPerFolder=50'

# restart repo in order to fully clear the Ignite caches on aldica-enabled instance
docker restart aldica-mem-bm-repository-test-1

# run initial cache load from empty state
curl -X GET -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=4'

# take and store heap dump for analysis
docker exec -ti aldica-mem-bm-repository-test-1 /usr/java/default/bin/jmap -dump:format=b,live,file=/tmp/aldica-1m-nodes.hprof 1
docker cp aldica-mem-bm-repository-test-1:/tmp/aldica-1m-nodes.hprof ./aldica-1m-nodes-ignite.hprof

# take other metrics from Ignite Admin Console pages, e.g. data region sizes, average cache get/put times
# http://localhost:8180/alfresco/s/aldica/admin/ignite-data-regions
# http://localhost:8180/alfresco/s/aldica/admin/ignite-caches

# repeat cache load to measure throughput / cache get timing from pre-filled state
curl -X GET -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=4'

# take further metrics from Ignite Admin Console pages (updated average cache get times + cache hit ratio) 

# run initial cache load from empty state on server with default Alfresco caching
curl -X GET -i 'http://localhost:8280/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=4'

# take and store heap dump for analysis
docker exec -ti aldica-mem-bm-repository-test-2 /usr/java/default/bin/jmap -dump:format=b,live,file=/tmp/alfresco-1m-nodes.hprof 1
docker cp aldica-mem-bm-repository-test-2:/tmp/alfresco-1m-nodes.hprof ./alfresco-1m-nodes-alfrescoDefault.hprof

# repeat cache load to measure throughput from pre-filled state
curl -X GET -i 'http://localhost:8280/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=4'
```

In order to test the various levels of [binary serialisation optimisations](./Concept-BinarySerialiser.md) available with the aldica module, it would be necessary to repeat the test runs for the aldica-enabled instance by
- stopping all containers via `docker stop aldica-mem-bm-repository-test-1 aldica-mem-bm-repository-test-2 postgres-1`
- removing all containers via `docker rm aldica-mem-bm-repository-test-1 aldica-mem-bm-repository-test-2 postgres-1`
- reconfiguring the `JAVA_OPTS` for the aldica-enabled instance in the `pom.xml` (e.g. by adding `-Daldica.core.binary.optimisation.enabled=false` to disable all but the most basic optimisations or `-Daldica.core.binary.optimisation.useIdsWhenPossible=false` to disable the more aggressive optimisations)
- rerunning the test except for the initial step to generate the 1 million nodes  

## Results

### Collected Metrics

- Date of benchmark: 2020-06-30 / 2020-07-01
- System: Lenovo T460p, Intel Core i7-6700HQ @ 2.60 GHz, 4 Core, 32 GiB RAM, Windows 10, Docker for Desktop (2 CPU, 14 GiB RAM assigned), SAMSUNG MZ7LN512HMJP 512 GiB SSD
- Concurrent threads: 6

| Measure | Alfresco 6.1.2 | aldica (medium opt) | aldica (default / max opt) | aldica (min opt) |
| :--- | ---: | ---: | ---: | ---: |
| Heap (Xmx) | 6 GiB | 4 GiB | 4 GiB | 4 GiB |
| Heap (total) | 3 GiB | 1,3 GiB | 1,3 GiB | 1,3 GiB |
| `nodesSharedCache` | 984,4 MiB | 1,1 GiB | 1,1 GiB | 1,1 GiB |
| `nodeAspectsCache` | 359 MiB | 200,2 MiB | 199,9 MiB | 331 MiB |
| `nodePropertiesCache` | 1 GiB | 621,1 MiB | 480,9 MiB | 1,1 GiB |
| `contentDataCache` | 175,9 MiB | 247,5 MiB | 247,5 MiB | 297 MiB |
| Memory (used)* | 3 GiB | 2.35 GiB | 2,2 GiB | 3 GiB |
| Memory reduction (used) | N/A | 21% | 26% | 0% |
| Memory (eff)* | 6 GiB | 5.05 GiB | 4.9 GiB | 5.71 GiB |
| Memory reduction (eff)* | N/A | 16% | 18% | 5% |
| Avg. throughput - initial load | 424/s | 427/s | 426/s | 442/s |
| Peak avg. throughput - initial load | 441/s | 460/s | 443/s | 484/s |
| Avg. throughput - 2nd load | 1274/s | 1654/s | 1913/s | 1605/s |
| Peak avg. throughput - 2nd load | 1276/s | 1654/s | 1913/s | 1651/s |

Notes:
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