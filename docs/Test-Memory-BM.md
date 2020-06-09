# About
This detached sub-module of the project is aimed at providing the means for general memory footprint benchmarks of default Alfresco caches and Ignite-backed caches. Using a simple content model and a set of generate / clear cache / load cache web scripts, the sub-module offers standardised, easily reproducible comparisons of the amount of memory require to support a specific number of cached nodes in both types of systems.

# Comparing Core Node Caches
The primary / only benchmark currently provided by this sub-module is aimed at comparing the memory footprints of the node aspects and properties caches between default Alfresco and aldica's Ignite-backed caches. This benchmark focuses on the caches which store unique values for each node, avoiding any caches that may hold data specific to only a subset of nodes, e.g. content data / URL caches for nodes with attached binary contents.

For this comparison, the following configurations and extensions have been bundled in this sub-module:

- custom content model bmModel.xml defining a single data aspect with a property of each scalar data type except boolean (int, long, float, double, date, datetime, text)
- web script using relative URI /aldica/mem-bm/generateNodes to generate a defined number of nodes using pseudo-random property values within the allowed value ranges
    - date/datetime between January 1st 2010 and time of system start (exact time of Java class being loaded)
    - text according to a defined list-of-values constraint
    - floating numbers between 0 and 1 (exclusive)
    - integer numbers in the full value spectrum from min to max value representable by the primitive Java types
- web script using relative URI /aldica/mem-bm/clearNodeCaches to clear caches after initial node generation / system start for a comparable base line
- web script using relative URI /aldica/mem-bm/cacheNodes to load a defined number of nodes into the caches with as little effect on other caches as possible

## Configuration
The benchmark makes use of the Docker-based integration test environment configured within the Maven project to start up two Alfresco Content Services instances with an identical set of deployed extensions. The only differences between the two instances lie in the JAVA_OPTS passed to the containers.

The instance ```aldica-mem-bm-repository-test-1``` is started with the following parameters (only listing diverging settings):

- global aldica extension enablement flag (```aldica.core.enabled```) set to ```true```
- default Ignite storage region

The instance ```aldica-mem-bm-repository-test-2``` is started with the following parameters (only listing diverging settings):

- global aldica extension enablement flag (```aldica.core.enabled```) set to ```false```

The following parameters have been specified as common parameters for both instances, though the aldica-specific parameters only take effect in instance ```aldica-mem-bm-repository-test-1```:

- disable TTL based eviction on ```nodesSharedCache```
- increase sizes of ```nodesSharedCache```, ```aspectsSharedCache``` and ```propertiesSharedCache``` to accommodate 1,250,000 nodes, in order to avoid any premature eviction when loading 1,000,000 nodes
- setup a Ignite data regions ```nodeAspects``` and ```nodeProperties``` of 10 MiB (initial size) and size limit of 5 GiB in ```${java.io.tmpdir}/IgniteWork/nodeAspects``` / ```${java.io.tmpdir}/IgniteWork/nodeProperties``` (essentially within the Tomcat temp directory)
- associate aldica Ignite-backed caches ```aspectsSharedCache``` and ```propertiesSharedCache``` with the data region ```nodeAspects``` and ```nodeProperties``` respectively

Apart from the listed changes, both instances use the same default configuration as the integration test instances in the main project sub-modules. With regards to Java heap management, this means:

- use of Garbage First (G1) garbage collector
- enabled string deduplication of G1 GC
- forced scavenge before doing a full GC cycle
- enabled parallel processing of references
- pre-touch Java heap during JVM initialisation (all pages zeroed)
- use of 4 GiB heap memory

## Full Execution + Data Retrieval

```
# use Maven integration test to spin up instances
mvn clean integration-test -Ddocker.tests.enabled=true

# generate 1,000,000 nodes on one instance
curl -X POST -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/generateNodes?countNodes=1000000&threads=4&maxChildrenPerFolder=50'

# restart repo in order to fully clear the Ignite caches on aldica-enabled instance and load nodes into cache
docker restart aldica-mem-bm-repository-test-1
curl -X GET -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=4'

docker exec -ti aldica-mem-bm-repository-test-1 /usr/java/default/bin/jmap -dump:format=b,live,file=/tmp/aldica-1m-nodes.hprof 1
docker cp aldica-mem-bm-repository-test-1:/tmp/aldica-1m-nodes.hprof ./aldica-1m-nodes.hprof

# clear caches on default Alfresco instance and load nodes into cache
curl -X POST -i 'http://localhost:8280/alfresco/s/aldica/mem-bm/clearNodeCaches'
curl -X GET -i 'http://localhost:8280/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=4'

docker exec -ti aldica-mem-bm-repository-test-2 /usr/java/default/bin/jmap -dump:format=b,live,file=/tmp/alfresco-1m-nodes.hprof 1
docker cp aldica-mem-bm-repository-test-2:/tmp/alfresco-1m-nodes.hprof ./alfresco-1m-nodes.hprof
```

In addition to the heap dumps retrieved, it is necessary to retrieve the size of the Ignite ```nodeAspects``` and ```nodeProperties``` data regions using the Admin Console page http://localhost:8180/alfresco/s/aldica/admin/ignite-data-regions
Additionally, general cache state can also be compared using the Admin Console pages http://localhost:8180/alfresco/s/ootbee/admin/caches and http://localhost:8280/alfresco/s/ootbee/admin/caches

**Note**: The Ignite-backed caches are primarily limited by the maximum size of the off-heap memory data regions configured for the caches. The Alfresco-typical configuration of a maximum number of items only affects the on-heap cache layer intended to reduce IO / serialisation overhead for the most recently used entries, which is disabled by default (configured via the aldica.caches.ignoreDefaultEvictionConfiguration property).

## Results

### Heap Dump Analysis
In the heap dump of both instances, the biggest object by retained heap memory is the ```nodesSharedCache```, taking roughly 810 MiB of heap memory. Due to Alfresco design flaws (mutable state in cache entries and usage patterns relying on server-local object semantics), this cache cannot be supported by an Ignite-backed cache, and as such there is no relevant difference in cache size between the two instances, apart from the minor difference due to different internal limit eviction behaviours.

In the heap dump of the ```aldica-mem-bm-repository-test-2``` instance (alfresco-1m-nodes.hprof) the two next biggest objects by retained heap memory are ```aspectsSharedCache``` and ```propertiesSharedCache```, with roughly 360 MiB and 763 MiB respectively.

### Overal Memory Usage
The Ignite data region ```nodeAspects``` of the ```aldica-mem-bm-repository-test-1``` instance uses roughly 218 MiB of physical memory for the cached data of ```aspectsSharedCache```, while ```nodeProperties``` uses roughly 408 MiB for ```propertiesSharedCache```. The individual on-heap facades for Ignite-backed caches only make up a few hundred bytes each, so are negligible. Compared directly to their on-heap counterparts in default Alfresco, the caches use 38 % and 46 % less memory respectively, not accounting for the fact that they each hold more entries overall due to a slight difference in limit / eviction handling.
In total, the Ignite-backed caches thus use roughly 1435 MiB of memory for all three caches related to node identity, aspects and properties, compared to roughly 1933 MiB of memory used by default Alfresco caches, resulting in a 25 % reduction in used memory relative to the default (58 % reduction in heap memory).

## Remarks

### Effective Cost of On-Heap Memory
The effective cost of on-heap memory is typically higher than the measured amount of used memory. In order to compensate for overhead effects introduced by various garbage collection mechanisms, each 1 MiB of used heap memory should be considered as requiring an increase of the overall heap size (Xms/Xmx parameters) of 1.2 (G1) - 1.5 MiB (CMS), unless low-level JVM GC parameters are used to fine tune the algorithms accordingly. These factors are influenced e.g. by the relative amount of memory that the GC algorithm tries to keep "free" as a buffer for moving data during GC cycles, the relative sizing of memory generations (Young (Eden/Survivor) and Old in CMS), or GC initiation thresholds, which can cause noticable performance impact when tripped, and more intensive / aggressive GC cycles are run.

### String Deduplication / Value Optimisation
The heap memory usage of the default Alfresco cache ```propertiesSharedCache``` would be even higher if a different GC algorithm was used or the string deduplication of G1GC was not enabled. Each textual property loaded from the database into caches represents a distinct string instance. Despite the textual property in this benchmark using a list-of-values constraint to constrain the set of used strings to only 9, the effective number of strings for 1,000,000 nodes is 1,000,000. With G1GC string deduplication enabled, the number of instances does not change, but the garbage collection mechanism transparently deduplicates the char[] arrays of these instances, which contain the actual character data. As a result, each string instance "only" costs the  overhead of the string wrapper instance (24 byte with compressed oops). With string deduplication disabled, each instance would add the cost of its character data to that overhead. Considering the cost of 32 bytes of character data per string instance in the list of values constraint, this would mean an additional 30 MiB of on-heap memory used for ```propertiesSharedCache```, just for the single text property in our custom content model. Considering that string deduplication also affects all instances of qualified names used as identifiers for aspects and properties, the overall savings by having G1GC string deduplication active will be even more substantial. The impact of G1GC string deduplication is generally estimated at about 10 % of the overall heap memory usage in typical Java application. 

Since Ignite-backed caches save data off heap and outside the reach of deduplication mechanisms of the garbage collection algorithm, they do not benefit from this feature. Using a naive implementation, these caches might actually be less efficient than default caches in terms of memory used, as a simple serialisation of the Java object structure would include a lot of wrapper instances (i.e. Map structures) and result in redundant data being written to memory, including redundant identifiers, such as qualified names for properties. In order to avoid such overhead, Ignite-backed caches support value transformations from/to cache, and both the ```aspectsSharedCache``` and ```propertiesSharedCache``` caches come with optimising transformations out-of-the-box. The optimisations currently include:

- replacing qualified name instances (aspects / property identifiers) with their database ID
- replacing content data instances (property values) with their database ID
- transforming collections into simpler arrays
- explicitly interning list-of-values string instances when read from off-heap memory into on-heap object form, to dedulicate not only the character data, but also the string wrapper, regardless of garbage collector configuration

### Comparison with Alfresco Enterprise

The benchmark can also be run against Alfresco Content Services in the Enterprise Edition. Since aldica does not support running in an Enterprise Edition instance, the benchmark can only be run with two clustered default ACS EE instances, instead of the default comparison setup with an Ignite-enabled and a default instance. In order to run with Enterprise Edition, the following changes need to be performed:

- modify sub-module pom.xml to enable use of an alternative base Docker image via the module's &lt;properties&gt;
- modify src/test/docker/Repository-Dockerfile to adapt USER / RUN directives for building an Enterprise-based image
- modify src/test/repository-it.xml to exclude aldica libraries and dependencies

All files that need to be modified contain comments to indicate the required changes.

When run with ACS EE 6.2.0, this benchmark shows similar memory usage patterns as with the default ACS Community instance. This is to be expected as all caches use the same default cache implementation. Both ```aspectsSharedCache``` and ```propertiesSharedCache``` are not cluster-enabled at all in Enterprise Edition, and the ```nodesSharedCache``` only uses a thin facade to support Hazelcast-backed remote invalidation while the actual data structure is handled by the default cache implementation. Running the benchmark against one of the two clustered instances of ACS EE, the caches use a combined heap memory of 1910 MiB (800, 357 and 753 MiB respectively for ```nodesSharedCache```, ```aspectsSharedCache``` and ```propertiesSharedCache```). Running the benchmark against the second of the two clustered instances yields a similar result. Running the clearNodeCaches web script will also trigger the ```nodesSharedCache``` to be cleared / emptied on the first instance as well, so it will affect its memory usage. If the clearNodeCaches web script is NOT run, the ```nodesSharedCache``` will still be partially cleared on the first instance when running the benchmark against the second, as the Hazelcast-supported invalidating cache facade will trigger (superflous / overly cautious) invalidation messages to be sent when loading new data from the database into caches.

The primary take-away from running the benchmark against ACS EE is that there is no significant difference in memory usage between Community and Enterprise Edition when it comes to the most important caches for node data. The relative reductions in memory usage observed between Community Edition with default caching and with aldica's Ignite-backed caches enabled, thus also apply in a comparison between Enterprise Edition with default (Hazelcast-enabled) caching and Community Edition with aldica's Ignite-backed caches enabled.