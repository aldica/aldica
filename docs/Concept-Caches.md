# Concept: Caches
This documentation section provides high-level concepts / information on caching both in default Alfresco and as provided by the aldica module. Since caching occurs primarily on the Alfresco Repository tier, this section will be heavily focused on its specific caches.

## Standard Repository Caches
### Alfresco Cache Framework
Caching in the Alfresco Repository application is typically used within Data Access Object (DAO) components to store data loaded from the database. This is aimed to avoid subsequent calls to the database for data previously accessed, both to reduce load on the common infrastructure component but also to avoid the overhead of such calls, typically in the form of network latency, query parsing and execution costs. In the caching framework used in Alfresco Repository, there are three distinct variants of caches used in such a scenario:

- [EntityLookupCache](https://github.com/Alfresco/alfresco-repository/blob/master/src/main/java/org/alfresco/repo/cache/lookup/EntityLookupCache.java): the high level, functional cache layer for use by DAO components which deals with common lookup / creation / update patterns
- [SimpleCache](https://github.com/Alfresco/alfresco-data-model/blob/master/src/main/java/org/alfresco/repo/cache/SimpleCache.java): the general abstraction of general purpose caches
    - [TransactionalCache](https://github.com/Alfresco/alfresco-repository/blob/master/src/main/java/org/alfresco/repo/cache/TransactionalCache.java): a transaction bound facade for an actual cache which keeps data loaded / updated / removed in a transaction isolated from other transactions, and propagates those changes only upon commit of the transaction
    - [DefaultSimpleCache](https://github.com/Alfresco/alfresco-repository/blob/master/src/main/java/org/alfresco/repo/cache/DefaultSimpleCache.java): the default implementation of a global / shared cache instance which stores key-value data entries for a particular use case

Only the last layer in this caching framework, on which the DefaultSimpleCache implementation is used by default, is designed to be extensible / changeable. Alfresco provides an interface contract for a [cache factory](https://github.com/Alfresco/alfresco-repository/blob/master/src/main/java/org/alfresco/repo/cache/CacheFactory.java), and different implementations can be set up by overriding the [default factory implementation](https://github.com/Alfresco/alfresco-repository/blob/master/src/main/java/org/alfresco/repo/cache/DefaultCacheFactory.java). The Alfresco Enterprise Edition itself does this to inject its Hazelcast-backed cluster-aware cache factory. The aldica module does the same to provide its enhanced caches unless that feature is explicitly disabled.

### Aldica Cache Types
The [cache factory](../blob/master/repository/src/main/java/org/aldica/repo/ignite/cache/CacheFactoryImpl.java) provided by the aldica module is capable of providing the following types of cache implementations, based on the individual configuration for each specific cache instance in the Alfresco configuration files (such as alfresco-global.properties):

- Ignite-backed
    - *local*: all data is held locally without any distribution / communication with corresponding caches on other servers in a data grid
    - *invalidating*: all data is held locally, but messages concerning update / removal operations on cache keys are distributed to other servers in a data grid for invalidation of locally held data in their corresponding caches
    - *partitioned*: data is collectively held by all servers in a data grid, split into a defined number of partitions, with one server acting as the primary control server for a specific partition, and one or more other servers maintaining a backup of that partition; when a server needs to use a cache entry not stored in a primary partition managed by this server, it needs to perform a network call to another server to check for its existence / obtain the entry ([Ignite partitioned cache mode details](https://apacheignite.readme.io/docs/cache-modes#partitioned-mode)) 
    - *replicated*: similar to a *partitioned* cache, but all servers in a data grid have all data partitions in their local memory, and never need to perform network calls for read-only operations ([Ignite replicated cache mode details](https://apacheignite.readme.io/docs/cache-modes#replicated-mode))
- Not Ignite-backed
    - *nullCache*: Alfresco's no-op cache, allowing targeted disabling / read-through semantics for individual caches
    - *localDefaultSimple*: the default, non-distributed type of caches created by default Alfresco, relevant for use cases where cache keys/values or their pattern of use do not support a distributed type of use and storage in serialised form
- Mixed Ignite / non-Ignite
    - *invalidatingDefaultSimple*: an enhanced variant of the default Alfresco cache type, where messages concerning update / removal operations on cache keys are distributed to other servers in a data grid for invalidation of locally held data in their corresponding caches

Unless the full data grid mode has been enabled in the configuration of the aldica module, all caches provided by the module will be limited to the *local* / *localDefaultSimple* cache types.

### On-Heap vs. Off-Heap
The default Alfresco caches, both in Community and Enterprise Editions, exclusively use "on-heap" storage of cache keys and values. This means all data objects is held in the Java heap and subject to Java Garbage Collection (GC) mechanisms and constraints. Increasing the size of caches always requires an increase in the size of the Java heap, and depending on the type of GC used, this may require a greater increase than expected. E.g. using the Concurrent Mark-Sweep (CMS) collector with its rather static composition into memory generations, if one intends to increase the size of the old generation (where all long-term cached data eventually resides) a corresponding relative increase in the young generation has to be factored into the increase of the overall heap, unless extremely fine-grained (generally discouraged) GC configuration is used. With increased Java heap memory, the "Full GC" cycles may include longer and longer "Stop-the-World" (STW) pauses where all application threads are suspended, introducing significant delays in processing of users requests. In severe cases, these STW pauses can take several seconds to even over a minute, which has been observed in some cases to even disrupt cluster operation within Alfresco Enterprise installations.

"Off-heap" storage cache keys and values are stored in a directly allocated memory area outside of Java heap, without any impact on the GC mechanism. Off-heap memory areas can be purely backed by physical memory or memory-mapped files on disk, essentially using disk space and the operating system's ability to swap as an extension to the available physical memory. By using off-heap storage for (long-term) cached data, the Java heap of the overall application can remain fairly small, only needing to handle a few global singleton constructs (configuration / service objects) and the short-lived objects of specific user requests or background jobs. This kind of usage fits extremely well with newer GC variants, such as the Garbage First (G1) collector.

All Ignite-backed caches provided by the aldica module can store cache data “on-heap” and “off-heap”. The Apache Ignite library also supports storing cache data in a persistent, on-disk manner to survive restarts of the Java application, but the aldica module does not utilize this capability as cache data stored that way cannot be reliably kept in sync with data stored in the database.

By default, Ignite-backed caches will not use on-heap storage. This default has been chosen to assure cached data exhibits the least amount of pressure on Java heap management as possible. The use of on-heap storage can be enabled either globally or for specific caches only. If on-heap storage is configured, cached entries are stored both in the on-heap and off-heap storage areas. Off-heap storage can be segregated into multiple storage regions with individual memory limits, but by default all Ignite-backed caches share a global default storage region. The amount of data an individual cache is allowed to store cannot be restricted apart from assigning that cache to a dedicated storage region.

Typically - for best possible performance - the total maximum size of all storage regions should not exceed the amount of available physical memory after accounting for the Java heap, Java metaspace, operating system needs and other applications on the same host. This size restriction can be avoided by assigning a disk-based swap path to a storage region, allowing the region’s data to be swapped to disk when there is no more free physical memory. If a region reaches its configured, maximum size, its memory pages are evicted using a “random least recently used” algorithm, specifically a random sample of 5 memory pages is picked, and the least recently used page of that sample is evicted. The default off-heap storage region configured by the aldica module is limited to 16 GiB of memory and uses the configured Java temporary file path for swapping to file.

### Ignite-incompatible Caches
Not all of the default Alfresco caches are used to cache actual, immutable data entities. In some instances, caches are used to manage singleton instances of specific services, including references to their dependencies, and/or configuration states. In other instances, cached data entries may contain mutable state that is modified at runtime without replacing / updating the entire cache entry. Such cache uses within Alfresco are incompatible with using Ignite-backed caches. For this reason, the cache factory provided by the aldica module is capable of providing several cache types that use the default Alfresco cache implementations, but may add functionality on top of this default implementation to make them work in a data grid when necessary.

The following default Alfresco caches are incompatible with Ignite-backed caches and use the *invalidatingDefaultSimple* or *localDefaultSimple* cache type:

- immutableSingletonSharedCache
- globalConfigSharedCache
- routingContentStoreSharedCache
- cachingContentStoreCache
- messagesSharedCache
- loadedResourceBundlessSharedCache
- resourceBundleBaseNamesSharedCache
- openCMISRegistrySharedCache
- imapMessageSharedCache
- contentDiskDriver

Apart from the cachingContentStoreCache, resourceBundleBaseNamesSharedCache and imapMessageSharedCache, all unsupported caches only handle actual functional components instead of data entries, and distributing such components to other servers of the data grid would not make sense.
The cachingContentStoreCache stores paths for local temporary files, which would typically not be valid on servers other than the one which created the temporary file. The resourceBundleBaseNamesSharedCache stores names of localisation bundles loaded from either the classpath of the server or the logical data repository, so contains a mixture of data that is either universal to all servers or specific only to the local server.
The imapMessageSharedCache stores full instances of `javax.mail.internet.MimeMessage`, which may contain references to functional components or connection management elements (e.g. `javax.mail.Session`). The contentDiskDriver indirectly stores value objects with direct references to Alfresco services, file channel and content accessor (reader/writer) instances. Any kind of serialisation in Ignite-backed caches would break apart these references.

### Aldica Cache Optimisations
The aldica module includes various optimisations to a sub-set of the default Alfresco caches to improve their performance and/or utility. Some of these optimisations may also be enabled on other caches by setting specific configuration properties.

In Enterprise Edition, the “invalidating” variants of caches always send out invalidation messages when a cache entry is set. This includes constellations when there was no previous cache entry and there may not even have been a value modification on the database necessitating an invalidation of cache entries on other servers, most notably when a server merely loads a value into cache for the first time. Since an invalidation requires that all other servers in the data region remove any cache entry for the same cache key, this behaviour can cause a detrimental “ping-pong” effect in the data grid, where different servers repeatedly put the same entry into their cache, invalidating the entry on other servers and causing those servers to re-put the entry into their cache upon next use, closing the invalidating “ping-pong” loop. The cache implementations provided by the aldica module can optionally be configured to only send invalidations to other servers when there has been an actual change in value, and contains default configuration to that effect for a sub-set of default caches for which tests have shown this can be safely done. Invalidations that occur as part of an explicit remove-type operation are unaffected and continue to be sent for every invocation.

Some Alfresco default caches manage values which can contain quite verbose data structures. While this may not be problematic when using on-heap storage only, as is the case with Alfresco default caches as provided out-of-the-box by Community and Enterprise Edition, this can be quite wasteful when using off-heap storage. The aldica module enhances two such caches, the node aspects and properties caches, to use more efficient value structures when stored off-heap. This is by adding a transparent value transformation layer to the TransactionalCache instances for these caches. The value transformations for these caches will:

- replace QName aspect name values with the DB IDs for these values
- replace ContentData property values with the DB IDs for these values
- replace AclEntity instances used as cache keys with instances of the custom class AclVersionKey

The replacement of the complex QName and ContentData values makes use of the fact that these values already have their own caches, making any additional storage of the full values in the node aspects or properties cache extremely redundant.
The replacement of AclEntity with AclVersionKey instances is necessary to support use of Ignite-backed caches for the caches in the AclDAOImpl and PermissionService, and fix a design problem with Alfresco's default implementation: instead of using a proper key class (like is already done with NodeVersionKey), the default implementation of the two mentioned components uses a complex value entity and uses it as a key, when only two out of literally a dozen fields is relevant for this operation. Since Ignite-backed caches perform hashCode/equals operations on the serialised form and do not take into account a custom implementation of hashCode/equals in Java code, using the complex value entity as a key would introduce mismatch issues when a lookup occurs with only a partially initialised entity.

A small set of default Alfresco caches serves in specific use cases that warrant a deviation from their default Alfresco cache type configuration to make the best possible use of them. The following caches will only ever contain at most a handful of cache entries and thus use either *localDefaultSimple* or *invalidatingDefaultSimple* cache types to avoid the overhead of a full Ignite cache:

- node.rootNodesSharedCache
- node.allRootNodesSharedCache
- propertyClassCache

Furthermore, the default cache *ticketsCache* is used in such a way that its default type as a *partitioned* (Alfresco term: *fully-distributed*) cache can cause significant overhead on user login or use of operations that list the currently authenticated users based on their cached tickets. Since at least the user login case occurs regularly, the cache type is overwritten by the aldica module to that of a *replicated* cache.
Overall, the following caches have been overwritten to use a *replicated* cache type for reasons of performance due for frequency of use, considering a generally limited number of small-ish entries:

- ticketsCache
- authenticationSharedCache
- immutableEntitySharedCache

The following caches have been overwritten to use a *partitioned* cache type instead of the Alfresco default (either *invalidating* or *local*) in order to reduce redundant data and increase the overall effective amount of data cached among all nodes in a grid, and avoid wasteful invalidations between nodes when non-sticky access patterns are used and same data is accessed successively on different nodes:

- node.nodesSharedCache
- node.aspectsSharedCache
- node.propertiesSharedCache
- propertyValueCache
- propertyUniqueContextSharedCache

## Asynchronously Refreshed Caches
In addition to the vast amount of standard caches in the Alfresco Repository (about 52 in Alfresco 6.1) there are a handful of caches using a distinct technical concept and interface. A [AsynchronouslyRefreshedCache](https://github.com/Alfresco/alfresco-core/blob/master/src/main/java/org/alfresco/util/cache/AsynchronouslyRefreshedCache.java) is a special type of cache that can have its values regenerated / recalculated asynchronously. It is used to manage rather complex data structures where a simple change can require extensive, cascading updates and/or recalculation of data, which would be too costly to handle as part of the original user action. It is used in the default Alfresco Repository for the following use cases:

- compiled data models of node types, aspects, properties and associations
- registry of web scripts in the Repository for legacy, private and public APIs
- XML configuration sections from Alfresco-specific &lt;alfresco-config&gt; files
- authority “bridge tables” for authority parent / ancestor lookups

These caches are by their design non-extensible and use on-heap storage. For that reason the aldica module does not directly modify or replace these caches in any way. But the module adds functionality based on the [RefreshableCacheListener interface](https://github.com/Alfresco/alfresco-core/blob/master/src/main/java/org/alfresco/util/cache/RefreshableCacheListener.java), which allows interested components to register for and receive events from these caches. The listener registered by the aldica module specifically reacts to “refresh request” events, and asynchronously sends them to all other servers in the data grid. As a result, whenever a local cache has been requested to perform an update / refresh after a change in data, that request is automatically published on all servers, ensuring that their local caches will also refresh and thus be consistent with the changed data.

Due to a design error in how Alfresco handles the deployment / activation of dynamic models via the Data Dictionary Repository structure and the subsequent refresh of compiled models, there is a functional limitation that the use of a RefreshableCacheListener cannot overcome. In this scenario, Alfresco does not emit a “refresh request” event as the refresh is forced within the same transaction as the user action. The aldica module uses a custom behaviour to react to low-level changes on the dictionary XML model itself in order to circumvent this limitation, otherwise it would not have been able to distribute the message to effect a model (de)activation on other servers in the data grid.

## Share Caches
Alfresco Share-tier maintains a small set of caches to manage Surf model objects, namely the definitions of pages, templates, page/template components, extension modules and dictionary query elements. Contrary to the Repository-tier, the Share-tier does not provide a central cache factory that allows for custom cache implementations to be provided. Rather, the cache implementations are tightly coupled with the components that use them. The aldica module thus cannot and does not provide any custom cache instances on the Alfresco Share-tier.

In order to maintain consistency between multiple Share instances, the caches use event / invalidation messaging via a ClusterService interface. The aldica module provides an implementation of that interface to use the Apache Ignite data grid messaging capabilities to distribute the various cache event / invalidation messages across all the servers in the data grid. Since this data grid messaging functionality is currently the only data grid-related feature the aldica module provides to Alfresco Share, the feature is immediately active upon installation of the module.