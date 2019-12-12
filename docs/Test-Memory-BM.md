# About
This detached sub-module of the project is aimed at providing the means for general memory footprint benchmarks of default Alfresco caches and Ignite-backed caches. Using a simple content model and a set of generate / clear cache / load cache web scripts, the sub-module offers standardised, easily reproducible comparisons of the amount of memory require to support a specific number of cached nodes in both types of systems.

# Comparing Core Node Caches
The primary/only benchmark currently provided by this sub-module is aimed at comparing the memory footprints of the node aspects and properties caches between default Alfresco and aldica's Ignite-backed caches. This benchmark focuses on the caches which store unique values for each node, avoiding any caches that may hold data specific to only a subset of nodes, e.g. content data / URL caches for nodes with attached binary contents.

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

- Xms/Xmx set to 2 GiB
- global aldica extension enablement flag (```aldica.core.enabled```) set to ```true```
- default Ignite storage region

The instance ```aldica-mem-bm-repository-test-2``` is started with the following parameters (only listing diverging settings):

- Xms/Xmx set to 4 GiB (to accommodate for the expected higher heap memory usage of on-heap only caches in Alfresco default
- global aldica extension enablement flag (```aldica.core.enabled```) set to ```false```

The following parameters have been specified as common parameters for both instances, though the aldica-specific parameters only take effect in instance ```aldica-mem-bm-repository-test-1```:

- disable TTL based eviction on ```nodesSharedCache```
- increase sizes of ```nodesSharedCache```, ```aspectsSharedCache``` and ```propertiesSharedCache``` to accommodate 1,000,000 nodes
- setup an Ignite data region ```nodes``` of 10 MiB (initial size) and size limit of 5 GiB in ```${java.io.tmpdir}/IgniteWork/nodes``` (essentially within the Tomcat temp directory)
- associate aldica Ignite-backed caches ```aspectsSharedCache``` and ```propertiesSharedCache``` with the data region ```nodes```

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

# clear caches on aldica-enabled instance and load nodes into cache
curl -X POST -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/clearNodeCaches'
curl -X GET -i 'http://localhost:8180/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=4'

docker exec -ti aldica-mem-bm-repository-test-1 /usr/java/default/bin/jmap -dump:format=b,live,file=/tmp/aldica-1m-nodes.hprof 1
docker cp aldica-mem-bm-repository-test-1:/tmp/aldica-1m-nodes.hprof ./aldica-1m-nodes.hprof

# clear caches on default Alfresco instance and load nodes into cache
curl -X POST -i 'http://localhost:8280/alfresco/s/aldica/mem-bm/clearNodeCaches'
curl -X GET -i 'http://localhost:8280/alfresco/s/aldica/mem-bm/cacheNodes?countNodes=1000000&threads=4'

docker exec -ti aldica-mem-bm-repository-test-2 /usr/java/default/bin/jmap -dump:format=b,live,file=/tmp/alfresco-1m-nodes.hprof 1
docker cp aldica-mem-bm-repository-test-2:/tmp/alfresco-1m-nodes.hprof ./alfresco-1m-nodes.hprof
```

In addition to the heap dumps retrieved, it is necessary to retrieve the size of the Ignite ```nodes``` data region using the Admin Console page http://localhost:8180/alfresco/s/aldica/admin/ignite-data-regions
Additionally, general cache state can also be compared using the Admin Console pages http://localhost:8180/alfresco/s/ootbee/admin/caches and http://localhost:8280/alfresco/s/ootbee/admin/caches 

## Results

### General Cache State
When comparing the general cache states of the aldica-enabled and Alfresco default instances, a minor difference in the effective cache sizes may be noticed. This difference is due to how the cache implementations handle value eviction upon nearing / reaching the limit. The Google Cache backed Alfresco default can prematurely evict cache entries (and more than may be needed to be evicted), based on its internal segment distribution structure and the configured concurrency level (Alfresco never configures this, so it is always set to 4). When using 1,000,000 nodes with pseudi-random property values, the overall difference should be sufficiently marginal.

### Heap Dump Analysis
In the heap dump of both instances, the biggest object by retained heap memory is the ```nodesSharedCache```, taking roughly 810-830 MiB of heap memory. Due to Alfresco design flaws (mutable state in cache entries and usage patterns relying on server-local object semantics), this cache cannot be supported by an Ignite-backed cache, and as such there is no relevant difference in cache size between the two instances, apart from the minor difference due to different internal limit eviction behaviours.

In the heap dump of the ```aldica-mem-bm-repository-test-2``` instance (alfresco-1m-nodes.hprof) the two next biggest objects by retained heap memory are ```aspectsSharedCache``` and ```propertiesSharedCache```, with roughly 355 MiB and 760 MiB respectively. 


### Overal Memory Usage
The Ignite data region ```nodes``` of the ```aldica-mem-bm-repository-test-1``` instance uses a total amount of roughly 620 MiB of physical memory for the cached data of ```aspectsSharedCache``` and ```propertiesSharedCache```. The individual on-heap facades for Ignite-backed caches only make up a few hundred bytes each, so are negligible. In total, the Ignite-backed caches thus uses roughly 1450 MiB of memory for all three caches related to node identity, aspects and properties, compared to roughly 1925 MiB of memory used by default Alfresco caches, resulting in a 25 % reduction in used memory relative to the default (66 % reduction in heap memory). 

## Remarks

### Effective Cost of On-Heap Memory
The effective cost of on-heap memory is typically higher than the measured amount of used memory. In order to compensate for overhead effects introduced by various garbage collection mechanisms, each 1 MiB of used heap memory should be considered as requiring an increase of the overall heap size (Xms/Xmx parameters) of 1.2 (G1) - 1.5 MiB (CMS), unless low-level JVM GC parameters are used to fine tune the algorithms accordingly.

### String Deduplication / Value Optimisation
The heap memory usage of the default Alfresco cache ```propertiesSharedCache``` would be even higher if a different GC algorithm was used or the string deduplication of G1GC was not enabled. Each textual property loaded from the database into caches represents a distinct string instance. Despite the textual property in this benchmark using a list-of-values constraint to constrain the set of used strings to only 9, the effective number of strings for 1,000,000 nodes is 1,000,000. With G1GC string deduplication enabled, the number of instances does not change, but the garbage collection mechanism transparently deduplicates the char[] arrays of these instances, which contain the actual character data. As a result, each string instance "only" costs the  overhead of the string wrapper instance (24 byte with compressed oops). With string deduplication disabled, each instance would add the cost of its character data to that overhead. Considering the cost of 32 bytes of character data per string instance in the list of values constraint, this would mean an additional 30 MiB of on-heap memory used for ```propertiesSharedCache```, just for the single text property in our custom content model. Considering that string deduplication also affects all instances of qualified names used as identifiers for aspects and properties, the overall savings by having G1GC string deduplication active will be even more substantial. The impact of G1GC string deduplication is generally estimated at about 10 % of the overall heap memory usage in typical Java application. 

Since Ignite-backed caches save data off heap and outside the reach of deduplication mechanisms of the garbage collection algorithm, they do not benefit from this feature. Using a naive implementation, these caches might actually be less efficient than default caches in terms of memory used, as a simple serialisation of the Java object structure would include a lot of wrapper instances (i.e. Map structures) and result in redundant data being written to memory, including redundant identifiers, such as qualified names for properties. In order to avoid such overhead, Ignite-backed caches support value transformations from/to cache, and both the ```aspectsSharedCache``` and ```propertiesSharedCache``` caches come with optimising transformations out-of-the-box. The optimisations currently include:

- replacing qualified name instances (aspects / property identifiers) with their database ID
- replacing content data instances (property values) with their database ID
- transforming collections into simpler arrays
- explicitly interning list-of-values string instances when read from off-heap memory into on-heap object form, to dedulicate not only the character data, but also the string wrapper, regardless of garbage collector configuration