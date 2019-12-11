# Configuration: Alfresco Repository Property Reference

All configuration on the Alfresco Share-tier - apart from the JVM properties - is done by setting Share global properties. Share global properties are a feature not available in default Alfresco and provided by the Acosix Utility project, which this project depends upon. All properties can be set via the _share-global.properties_ file in _&lt;tomcatPath&gt;/shared/classes/_. When using a deployment based on standard Alfresco Docker images for Alfresco Content Services, most properties can be set via the JAVA\_OPTS environment variables in Docker Compose or Kubernetes deployments.

## Core Ignite Grid

The following configuration properties affect the instantiation of the core Ignite data grid instance that backs all of the more advanced / specific functionalities. They mostly deal with general enablement, network communication, memory area management and miscellaneous Ignite internals.

### High-Level Properties

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.core.enabled | ``true`` | Central enablement flag for the module - if set to ``false``, the module will not activate its abstract Spring beans to instantiate an Ignite grid nor modify any default Spring beans to injects its functionalities / components (other features, especially those not directly controllable via properties, like web scripts for the Admin Console, will remain active/available) |
| aldica.core.name | ``shareGrid`` | Name of the Ignite grid instance (technically, multiple grid instances can be created in a single JVM, so this is used to distinguish and select the instance to be used for all features of aldica) |
| aldica.core.login | ``share`` | Part of authentication data for a server to join an existing data grid |
| aldica.core.password | ``shareGrid-dev`` | Part of authentication data for a server to join an existing data grid - should be changed **always** to avoid a server accidentally joining a data grid it is not meant to join  |
| aldica.core.local.id |  | Unique ID / name of this server within the distributed Ignite data grid, used for more efficient handling of discovery as well as grid split scenarios - it is **recommended** to always set this to a stable logical name identifying the server, e.g. repository01, repository02... |

### Network Properties

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.core.local.host |  | Network address (host / IP) that this server should use for determining on which interface to binds its network ports for listing for communication requests within the data grid |
| aldica.core.public.host |  | Network address (host / IP) to consider as the publicly accessible address of this server for use in pro-active network address translation handling (see [Address Translation](./Concept-GridMemberDiscovery.md#Address+Translation)) - if set to a host name, the name **must** be resolvable to a publicly accessible IP address for this server as Ignite primarily exchanges / uses address information based on resolved IP addresses |
| aldica.core.local.comm.port | ``47130`` | Network port on which to bind for the general TCP-based communication within the data grid |
| aldica.core.local.comm.portRange | ``0`` | Range / number of alternative ports (relative to _aldica.core.local.comm.port_) on which to attempt to bind if the configured port is not available |
| aldica.core.public.comm.port |  | Publicly accessible port for general grid communication used in network address translation handling, e.g. in a NAT-ed environment |
| aldica.core.local.comm.messageQueueLimit | ``10000`` | Message queue limit for incoming and outgoing messages - a value of ``0`` enabled unlimited messages, which might cause Out-of-Memory errors at runtime |
| aldica.core.local.comm.connectTimeout | ``1000`` | Initial timeout (in ms) for establishing connections with remote nodes, which will be increased up to the maximum allowed connection timeout when handshake procedures need to be repeated due to current connection timeout being breached - ``0`` for an infinite timeout |
| aldica.core.local.comm.maxConnectTimeout | ``10000`` | Maximum timeout (in ms) for establishing connections with remote nodes - ``0`` for an infinite timeout |
| aldica.core.local.comm.socketTimeout | ``5000``| Timeout (in ms) for writing messages to network sockets - if breached, the connection to the other server will be closed and a reconnect will be attempted |
| aldica.core.local.comm.connectionsPerNode | ``1`` | Number of network connections to maintain with each server in the data grid |
| aldica.core.local.comm.filterUnreachableAddresses | ``false`` | Flag specifying if lists of possible network addresses for a specific server should be filtered based on accessibility checks before attempting a connection - this property is **highly recommended** to be set to ``true`` in deployment scenarios with network address translation to avoid delays in member discovery / network communication due to connection timeouts and repeated connection attempts |
| aldica.core.local.disco.port | ``47140`` | Network port on which to bind for the TCP-based member discovery handling, primarily for other servers to connect to in order join the data grid of which this server is a member |
| aldica.core.local.disco.portRange | ``0`` | Range / number of alternative ports (relative to _aldica.core.local.disco.port_) on which to attempt to bind if the configured port is not available |
| aldica.core.public.disco.port |  | Publicly accessible port for member discovery handling used in network address translation handling, e.g. in a NAT-ed environment |
| aldica.core.local.disco.joinTimeout | ``0`` | Timeout (in ms) for handling operations related to joining an existing data grid, e.g. registering the local network addresses with the central database or connecting to any of the configured/registered addresses of existing grid members - ``0`` for an infinite timeout |
| aldica.core.local.disco.ackTimeout | ``5000`` | Timeout (in ms) for receiving acknowledgements for sent join-related messages before messages are resent |
| aldica.core.local.disco.socketTimeout | ``5000`` | Timeout (in ms) for establishing connections / writing to sockets in join-related operations |
| aldica.core.local.disco.networkTimeout | ``15000`` | Maximum timeout (in ms) for join-related network operations |
| aldica.core.local.time.port | ``47150`` | Network port on which to bind for the UDP-based time server handling |
| aldica.core.local.time.portRange | ``0`` | Range / number of alternative ports (relative to _aldica.core.local.time.port_) on which to attempt to bind if the configured port is not available |
| aldica.core.public.time.port |  | Publicly accessible port for time server handling used in network address translation handling, e.g. in a NAT-ed environment |
| aldica.core.initialMembers |  | Comma-separated list of accessible network addresses to attempt to contact as initial data grid members during [member discovery](./Concept-GridMemberDiscovery.md). This is generally **not necessary** as aldica will handle discovery using database-stored address registrations. Configured addresses may use host names / IP addresses with or without ports / port ranges (based on the _aldica.core.local.disco.port_ and related settings), e.g. ``repo1.acme.com,192.168.0.2,repo2.acme.com:47110,192.168.0.4:47110-47119`` |

### Storage Properties

These properties affect the management of off-heap storage regions for system internal functionalities. 

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.core.storage.pageSize | ``4096`` | Size (in bytes) for a single memory page size - should ideally be aligned with file system block sizes |
| aldica.core.storage.systemInitialSize | ``10485760`` (10 MiB) | Initial size (in bytes) of the data region reserved for internal Ignite data structures / management of the Ignite data grid |
| aldica.core.storage.systemMaxSize | ``20971520`` (20 MiB) | Maximum size (in bytes) of the data region reserved for internal Ignite data structures / management of the Ignite data grid |
| aldica.core.storage.defaultStorageRegion.initialSize | ``31457280`` (30 MiB) | Initial size (in bytes) of the primary data region used to back all Ignite-based caches unless individual caches have been configured to use dedicated data regions |
| aldica.core.storage.defaultStorageRegion.maxSize | ``134217728`` (128 MiB) | Maximum size (in bytes) of the primary data region used to back all Ignite-based caches unless individual caches have been configured to use dedicated data regions |
| aldica.core.storage.defaultStorageRegion.swapPath | ``${java.io.tmpdir}/aldica/defaultDataRegionSwap`` | Path to a file system directory in which the primary data region will swap if the available physical memory is not sufficient to handle the size of the data region |

### Internal Properties

These properties should generally not need to be set / modified. They refer to various internal Ignite configuration properties which have been set to reasonable defaults, and have solely been prepared / exposed as properties just in case / for the highly unexpected case one user / deployment runs into an issue where those might need to be altered.

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.core.failureDetectionTimeout | ``10000`` | Timeout (in ms) for detecting various kinds of failures in grid communication - this property provides the default for a variety of other timeouts, but since we use dedicated configuration properties for those, its presence is only meant to provide a fallback for any timeout that might have been missed or may be added in future releases of Apache Ignite |
| aldica.core.systemWorkerBlockedTimeout | ``${aldica.core.failureDetectionTimeout}`` | Timeout (in ms) for detecting a system worker thread to be blocked / in a non-responsive state |
| aldica.core.publicThreadPoolSize | ``2`` | Number of threads in the Ignite public thread pool, responsible for processing distributed compute jobs - default Ignite would actually use ``Math.max(8, #available_proc_count)`` without this property, though aldica currently does not use distributed compute jobs (setting a value of ``0`` is not supported) |
| aldica.core.serviceThreadPoolSize | ``${aldica.core.publicThreadPoolSize}`` | Number of threads in the Ignite service thread pool, responsible for processing distributed service proxy invocations - default Ignite would actually use ``Math.max(8, #available_proc_count)`` without this property, though aldica currently does not use distributed service proxies (setting a value of ``0`` is not supported) |
| aldica.core.systemThreadPoolSize | ``${aldica.core.publicThreadPoolSize}`` | Number of threads in the Ignite system thread pool, responsible for processing internal system messages - default Ignite would actually use ``Math.max(8, #available_proc_count)`` without this property (setting a value of ``0`` is not supported) |
| aldica.core.asyncCallbackThreadPoolSize | ``1`` | Number of threads in the Ignite async callback thread pool, responsible for processing asynchronous callback - aldica in its current state does not use async callbacks either directly or indirectly, so this is set extremely low instead of the ``Math.max(8, #available_proc_count)`` default value (setting a value of ``0`` is not supported) |
| aldica.core.managementThreadPoolSize | ``4`` | Number of threads in the Ignite management pool, responsible for processing internal / visor compute jobs (setting a value of ``0`` is not supported) |
| aldica.core.peerClassLoadingThreadPoolSize | ``1`` | Number of threads in the Ignite async callback thread pool, responsible for processing loading of classes from remote servers - aldica in its current state disallows peer class loading, so this is set even lower than the default value of ``2`` (setting a value of ``0`` is not supported) |
| aldica.core.igfsThreadPoolSize | ``1`` | Number of threads in the Ignite file system pool, responsible for processing outgoing Ignite file system messages - aldica in its current state does not use the distributed Ignite file system either directly or indirectly, so this is set lower than the default value of ``#available_proc_count`` (setting a value of ``0`` is not supported) |
| aldica.core.dataStreamerThreadPoolSize | ``${aldica.core.publicThreadPoolSize}`` | Number of threads in the Ignite data streamer pool, responsible for processing data stream messages - default Ignite would actually use ``Math.max(8, #available_proc_count)`` without this property (setting a value of ``0`` is not supported) |
| aldica.core.utilityCacheThreadPoolSize | ``${aldica.core.publicThreadPoolSize}`` | Number of threads in the Ignite utility pool, responsible for processing utility cache messages - default Ignite would actually use ``Math.max(8, #available_proc_count)`` without this property (setting a value of ``0`` is not supported) |
| aldica.core.queryThreadPoolSize | ``1`` | Number of threads in the Ignite query pool, responsible for processing query messages - aldica in its current state does not use the distributed queries either directly or indirectly, so this is set significantly lower than the default value of ``Math.max(8, #available_proc_count)`` (setting a value of ``0`` is not supported) |
| aldica.core.rebalanceThreadPoolSize | ``1`` | Number of threads in the Ignite rebalance pool, responsible for processing rebalancing of cached data on join / leave of servers (setting a value of ``0`` is not supported) |

## Ignite-backed Caches

The following configuration properties affect Ignite-backed cache instances. This mostly refers to instances using the default Alfresco caching framework based on the ``SimpleCache`` interface, though individual properties may also affect the Alfresco lock store and asynchronously refreshed caches.

### High-Level Properties

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.webSessionCache.enabled | ``false`` | Enablement flag for the pre-configured cache to handle HTTP session replication between servers for full failover / high-availability functionality (requires modification of Alfresco Repository web.xml to fully enable) |
| aldica.webSessionCache.gridName | ``${aldica.core.name}`` | The name of the data grid to use for instantiating the web session cache |
| aldica.webSessionCache.cacheName | ``servlet.webSesssionCache`` | The unique name / identifier of the cache, which must be identical on all active servers in the data grid |
| aldica.webSessionCache.retriesOnFailure | ``2`` | The number of retries that should be attempted when retrieving / storing a web session |
| aldica.webSessionCache.retriesTimeout | ``5000`` | The timeout (in ms) between retries that should be attempted when retrieving / storing a web session |
| aldica.webSessionCache.keepBinary | ``true`` | Flag to control whether the cache should keep / use the serialised form of the web session across all cache tiers of Ignite (on-heap, off-heap) |
| aldica.webSessionCache.cacheMode | ``REPLICATED`` | The mode of the web session cache - defaults to ``REPLICATED`` for the best possible read performance (as HTTP sessions are rarely modified on the Alfresco Repository tier) and least chance for data loss in case of a sudden failure of a data grid member |
| aldica.webSessionCache.backups | ``1`` | The number of backups to keep for each partition of the cache |
| aldica.webSessionCache.maxSize | ``10000`` | The maximum amount of session to keep in the on-heap cache | 

## Web Session Cache

The configuration of the web session cache requires a change to the default Alfresco Share _web.xml_ file in addition to setting one or more properties in _share-global.properties_. Due to limitations in the Java Servlet specification, it is not possible to provide this feature in a way that does not require this change by the administrator / developer / end-user who wish to use this feature.

### Configuration Properties

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.webSessionCache.enabled | ``false``  | Central enablement flag for the Ignite web session cache - if set to ``false`` the cache will be inactive regardless of the configuration change made to _web.xml_ |
| aldica.webSessionCache.instanceName | ``${aldica.core.name}`` | The name of the data grid to use for instantiating the Ignite cache |
| aldica.webSessionCache.cacheName | ``servlet.webSessionCache`` | The name of the Ignite cache to instantiate for the feature |
| aldica.webSessionCache.retriesOnFailure | ``2`` | The number of retries that should be attempted when a cache operation affecting a session failed |
| aldica.webSessionCache.retriesTimeout | ``5000`` | The number of milliseconds before a retry cache operation affecting a session will timeout |
| aldica.webSessionCache.keepBinary | ``true`` | Technical flag to specify whether the Ignite backed cache should keep the internal binary representation on all internal layers - should never need to be changed |
| aldica.webSessionCache.cacheMode | ``REPLICATED`` | The mode in which the Ignite cache should operate - no other cache mode makes sense for the use case of a distributed web session cache, so this should never need to be changed |
| aldica.webSessionCache.maxSize | ``10000`` | The limit of session objects to hold in the on-heap cache |
| aldica.webSessionCache.partitionsCount | ``32`` | The number of partitions that should be used to split the data of the Ignite cache - since there is no global ``aldica.caches.partitionsCount`` property in Share like there is for the Repository (the web session cache is the only Ignite cache actually used in Share), this is set directly on the cache; the value should generally be significantly higher than the number of servers in a data grid |

### _web.xml_ Changes

The web session cache requires an additional web filter to be defined and registered on a global level before any of the default filters defined by Alfresco. This configuration change is not possible via a Web Fragment, and so requires explicit change of the _web.xml_ file. The following configuration snippets need to be added to the file - it is important that the &lt;filter-mapping&gt; section be added before any similar sections of the default file.

```xml
<filter>
    <filter-name>WebSessionCacheFilter</filter-name>
    <filter-class>org.aldica.common.ignite.web.GlobalConfigAwareWebSessionFilter</filter-class>
</filter>

<filter-mapping>
    <filter-name>WebSessionCacheFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```