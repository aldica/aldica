# Configuration: Alfresco Repository Property Reference

All configuration on the Alfresco Repository-tier - apart from the JVM properties - is done by setting Alfresco global properties. All properties can be set via the _alfresco-global.properties_ file in _&lt;tomcatPath&gt;/shared/classes/_. When using a deployment based on standard Alfresco Docker images for Alfresco Content Services, most properties can be set via the JAVA\_OPTS environment variables in Docker Compose or Kubernetes deployments. A small sub-set of 3 (dynamic) properties cannot be set using _-D_ parameters in JAVA\_OPTS due to configuration lookup / inheritance short comings in those Alfresco Docker images. A [pull request to add an alternative way to provide global properties](https://github.com/Alfresco/acs-community-packaging/pull/201) free of this limitation has been filed with Alfresco.

## Core Ignite Grid

The following configuration properties affect the instantiation of the core Ignite data grid instance that backs all of the more advanced / specific functionalities. They mostly deal with general enablement, network communication, memory area management and miscellaneous Ignite internals.

### High-Level Properties

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.core.enabled | `true` | Central enablement flag for the module - if set to `false`, the module will not activate its abstract Spring beans to instantiate an Ignite grid nor modify any default Spring beans to injects its functionalities / components (other features, especially those not directly controllable via properties, like web scripts for the Admin Console, will remain active/available) |
| aldica.core.name | `repositoryGrid` | Name of the Ignite grid instance (technically, multiple grid instances can be created in a single JVM, so this is used to distinguish and select the instance to be used for all features of aldica) |
| aldica.core.login | `repository` | Part of authentication data for a server to join an existing data grid |
| aldica.core.password | `repositoryGrid-dev` | Part of authentication data for a server to join an existing data grid - should be changed **always** to avoid a server accidentally joining a data grid it is not meant to join  |
| aldica.core.local.id |  | Unique ID / name of this server within the distributed Ignite data grid, used for more efficient handling of discovery as well as grid split scenarios - it is **recommended** to always set this to a stable logical name identifying the server, e.g. repository01, repository02... |

### Network Properties

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.core.local.host |  | Network address (host / IP) that this server should use for determining on which interface to binds its network ports for listing for communication requests within the data grid |
| aldica.core.public.host |  | Network address (host / IP) to consider as the publicly accessible address of this server for use in pro-active network address translation handling (see [Address Translation](./Concept-GridMemberDiscovery.md#Address+Translation)) - if set to a host name, the name **must** be resolvable to a publicly accessible IP address for this server as Ignite primarily exchanges / uses address information based on resolved IP addresses |
| aldica.core.local.comm.port | `47100` | Network port on which to bind for the general TCP-based communication within the data grid |
| aldica.core.local.comm.portRange | `0` | Range / number of alternative ports (relative to _aldica.core.local.comm.port_) on which to attempt to bind if the configured port is not available |
| aldica.core.public.comm.port |  | Publicly accessible port for general grid communication used in network address translation handling, e.g. in a NAT-ed environment |
| aldica.core.local.comm.messageQueueLimit | `10000` | Message queue limit for incoming and outgoing messages - a value of `0` enabled unlimited messages, which might cause Out-of-Memory errors at runtime |
| aldica.core.local.comm.connectTimeout | `1000` | Initial timeout (in ms) for establishing connections with remote nodes, which will be increased up to the maximum allowed connection timeout when handshake procedures need to be repeated due to current connection timeout being breached - `0` for an infinite timeout |
| aldica.core.local.comm.maxConnectTimeout | `10000` | Maximum timeout (in ms) for establishing connections with remote nodes - `0` for an infinite timeout |
| aldica.core.local.comm.socketTimeout | `5000`| Timeout (in ms) for writing messages to network sockets - if breached, the connection to the other server will be closed and a reconnect will be attempted |
| aldica.core.local.comm.connectionsPerNode | `1` | Number of network connections to maintain with each server in the data grid |
| aldica.core.local.comm.filterUnreachableAddresses | `false` | Flag specifying if lists of possible network addresses for a specific server should be filtered based on accessibility checks before attempting a connection - this property is **highly recommended** to be set to `true` in deployment scenarios with network address translation to avoid delays in member discovery / network communication due to connection timeouts and repeated connection attempts |
| aldica.core.local.disco.port | `47110` | Network port on which to bind for the TCP-based member discovery handling, primarily for other servers to connect to in order join the data grid of which this server is a member |
| aldica.core.local.disco.portRange | `0` | Range / number of alternative ports (relative to _aldica.core.local.disco.port_) on which to attempt to bind if the configured port is not available |
| aldica.core.public.disco.port |  | Publicly accessible port for member discovery handling used in network address translation handling, e.g. in a NAT-ed environment |
| aldica.core.local.disco.joinTimeout | `0` | Timeout (in ms) for handling operations related to joining an existing data grid, e.g. registering the local network addresses with the central database or connecting to any of the configured/registered addresses of existing grid members - `0` for an infinite timeout |
| aldica.core.local.disco.ackTimeout | `5000` | Timeout (in ms) for receiving acknowledgements for sent join-related messages before messages are resent |
| aldica.core.local.disco.socketTimeout | `5000` | Timeout (in ms) for establishing connections / writing to sockets in join-related operations |
| aldica.core.local.disco.networkTimeout | `15000` | Maximum timeout (in ms) for join-related network operations |
| aldica.core.local.time.port | `47120` | Network port on which to bind for the UDP-based time server handling |
| aldica.core.local.time.portRange | `0` | Range / number of alternative ports (relative to _aldica.core.local.time.port_) on which to attempt to bind if the configured port is not available |
| aldica.core.public.time.port |  | Publicly accessible port for time server handling used in network address translation handling, e.g. in a NAT-ed environment |
| aldica.core.initialMembers |  | Comma-separated list of accessible network addresses to attempt to contact as initial data grid members during [member discovery](./Concept-GridMemberDiscovery.md). This is generally **not necessary** as aldica will handle discovery using database-stored address registrations. Configured addresses may use host names / IP addresses with or without ports / port ranges (based on the _aldica.core.local.disco.port_ and related settings), e.g. `repo1.acme.com,192.168.0.2,repo2.acme.com:47110,192.168.0.4:47110-47119` |

### Storage Properties

These properties affect the management of off-heap storage regions for system internal as well as cache functionalities. 

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.core.storage.pageSize | `16384` | Size (in bytes) for a single memory page size - should ideally be aligned with file system block sizes (allowed values: 1024, 2048, 4096, 8192, 16384) |
| aldica.core.storage.systemInitialSize | `20971520` (20 MiB) | Initial size (in bytes) of the data region reserved for internal Ignite data structures / management of the Ignite data grid |
| aldica.core.storage.systemMaxSize | `41943040` (40 MiB) | Maximum size (in bytes) of the data region reserved for internal Ignite data structures / management of the Ignite data grid |
| aldica.core.storage.defaultStorageRegion.initialSize | `1073741824` (1 GiB) | Initial size (in bytes) of the primary data region used to back all Ignite-based caches unless individual caches have been configured to use dedicated data regions |
| aldica.core.storage.defaultStorageRegion.maxSize | `17179869184` (16 GiB) | Maximum size (in bytes) of the primary data region used to back all Ignite-based caches unless individual caches have been configured to use dedicated data regions |
| aldica.core.storage.defaultStorageRegion.swapPath | `${java.io.tmpdir}/aldica/defaultDataRegionSwap` | Path to a file system directory in which the primary data region will swap if the available physical memory is not sufficient to handle the size of the data region |
| aldica.core.storage.region._&lt;name&gt;_.initialSize |  | Initial size (in bytes) of a dynamic, custom data region (identified by the _name_ fragment in the configuration property) - this property **cannot** be provided via JAVA\_OPTS _-D_ parameters|
| aldica.core.storage.region._&lt;name&gt;_.maxSize |  | Maximum size (in bytes) of a dynamic, custom data region (identified by the _name_ fragment in the configuration property) - this property **cannot** be provided via JAVA\_OPTS _-D_ parameters| |
| aldica.core.storage.region._&lt;name&gt;_.swapPath |  | Path to a file system directory in which the dynamic, custom data region (identified by the _name_ fragment in the configuration property) will swap if the available physical memory is not sufficient to handle the size of the data region - this property **cannot** be provided via JAVA\_OPTS _-D_ parameters| |

### Serialisation Optimisation Properties

These properties affect the various custom serialisation optimisations provided by the aldica module to try and reduce the memory footprint of serialised key / value objects.

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.core.binary.optimisation.enabled | `true` | global enablement flag for non-trivial optimisations |
| aldica.core.binary.optimisation.useRawSerial | `${aldica.core.binary.optimisation.enabled}` | global enablement flag for using raw serial form without structure metadata |
| aldica.core.binary.optimisation.useIdsWhenReasonable | `${aldica.core.binary.optimisation.enabled}` | global enablement flag for using dynamic value substitution for any entities backed by the Alfresco `immutableEntityCache` |
| aldica.core.binary.optimisation.useIdsWhenPossible | `${aldica.core.binary.optimisation.enabled}` | global enablement flag for using dynamic value substitution for all types of complex entities that can be resolved via secondary caches, even other complex value caches |
| aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers | `${aldica.core.binary.optimisation.useRawSerial}` | global enablement flag for using variable length integer values in raw serial form - **Note**: implications with regards to the reduced value space are addressed in the [raw serial form concept](./Concept-BinarySerialiser.md#Raw_Serial_Form) |
| aldica.core.binary.optimisation.rawSerial.handleNegativeIds | `false` | global enablement flag to support negative database IDs - Alfresco by default does not use negative IDs and this flag only needs to be set if manual manipulation of database entries has created entries with negative IDs |
| aldica.core.binary.optimisation.rawSerial.handle4EiBFileSizes | `false` | global enablement flag to support content file sizes in equal to or larger than 4 EiB - it is highly unlikely that any Alfresco system needs to handle individual files of these sizes |
| aldica.core.binary.optimisation.rawSerial.useOptimisedContentURL | `${aldica.core.binary.optimisation.useRawSerial}` | global enablement flag to use an optimised serialisation form for content URLs instead of writing them as regular Strings |
| aldica.core.binary.optimisation.txnCacheKey.enabled | `true` | enablement flag for optimisations applied to `TransactionalCache$CacheRegionKey` instances |
| aldica.core.binary.optimisation.txnCacheKey.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `TransactionalCache$CacheRegionKey` instances |
| aldica.core.binary.optimisation.txnCacheKey.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `TransactionalCache$CacheRegionKey` instances |
| aldica.core.binary.optimisation.txnCacheValueHolder.enabled | `true` | enablement flag for optimisations applied to `TransactionalCache$ValueHolder` instances |
| aldica.core.binary.optimisation.txnCacheValueHolder.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `TransactionalCache$ValueHolder` instances |
| aldica.core.binary.optimisation.txnCacheValueHolder.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `TransactionalCache$ValueHolder` instances |
| aldica.core.binary.optimisation.cacheKey.enabled | `true` | enablement flag for optimisations applied to `CacheRegionKey` instances |
| aldica.core.binary.optimisation.cacheKey.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `CacheRegionKey` instances |
| aldica.core.binary.optimisation.cacheKey.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `CacheRegionKey` instances |
| aldica.core.binary.optimisation.cacheValueKey.enabled | `true` | enablement flag for optimisations applied to `CacheRegionValueKey` instances |
| aldica.core.binary.optimisation.cacheValueKey.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `CacheRegionValueKey` instances |
| aldica.core.binary.optimisation.cacheValueKey.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `CacheRegionValueKey` instances |
| aldica.core.binary.optimisation.nodeVersionKey.enabled | `${aldica.core.binary.optimisation.enabled}` | enablement flag for optimisations applied to `NodeVersionKey` instances |
| aldica.core.binary.optimisation.nodeVersionKey.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `NodeVersionKey` instances |
| aldica.core.binary.optimisation.nodeVersionKey.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `NodeVersionKey` instances |
| aldica.core.binary.optimisation.nodeVersionKey.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `NodeVersionKey` instances |
| aldica.core.binary.optimisation.childByNameKey.enabled | `${aldica.core.binary.optimisation.enabled}` | enablement flag for optimisations applied to `ChildByNameKey` instances |
| aldica.core.binary.optimisation.childByNameKey.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `ChildByNameKey` instances |
| aldica.core.binary.optimisation.childByNameKey.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `ChildByNameKey` instances |
| aldica.core.binary.optimisation.childByNameKey.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `ChildByNameKey` instances |
| aldica.core.binary.optimisation.pucKey.enabled | `${aldica.core.binary.optimisation.enabled}` | enablement flag for optimisations applied to `CachePucKey` instances |
| aldica.core.binary.optimisation.pucKey.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `CachePucKey` instances |
| aldica.core.binary.optimisation.pucKey.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `CachePucKey` instances |
| aldica.core.binary.optimisation.pucKey.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `NodeVersionKey` instances |
| aldica.core.binary.optimisation.transactionEntity.enabled | `true` | enablement flag for optimisations applied to `TransactionEntity` instances |
| aldica.core.binary.optimisation.transactionEntity.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `TransactionEntity` instances |
| aldica.core.binary.optimisation.transactionEntity.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `TransactionEntity` instances |
| aldica.core.binary.optimisation.transactionEntity.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `TransactionEntity` instances |
| aldica.core.binary.optimisation.storeEntity.enabled | `true` | enablement flag for optimisations applied to `StoreEntity` instances |
| aldica.core.binary.optimisation.storeEntity.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `StoreEntity` instances |
| aldica.core.binary.optimisation.storeEntity.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `StoreEntity` instances |
| aldica.core.binary.optimisation.storeEntity.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `StoreEntity` instances |
| aldica.core.binary.optimisation.nodeEntity.enabled | `true` | enablement flag for optimisations applied to `NodeEntity` instances |
| aldica.core.binary.optimisation.nodeEntity.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `NodeEntity` instances |
| aldica.core.binary.optimisation.nodeEntity.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `NodeEntity` instances |
| aldica.core.binary.optimisation.nodeEntity.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `NodeEntity` instances |
| aldica.core.binary.optimisation.childAssocEntity.enabled | `true` | enablement flag for optimisations applied to `ChildAssocEntity` instances |
| aldica.core.binary.optimisation.childAssocEntity.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `ChildAssocEntity` instances |
| aldica.core.binary.optimisation.childAssocEntity.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `ChildAssocEntity` instances |
| aldica.core.binary.optimisation.childAssocEntity.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `ChildEntity` instances |
| aldica.core.binary.optimisation.aclEntity.enabled | `true` | enablement flag for optimisations applied to `AclEntity` instances |
| aldica.core.binary.optimisation.aclEntity.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `AclEntity` instances |
| aldica.core.binary.optimisation.aclEntity.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `AclEntity` instances |
| aldica.core.binary.optimisation.aclEntity.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `AclEntity` instances |
| aldica.core.binary.optimisation.contentUrlEntity.enabled | `true` | enablement flag for optimisations applied to `ContentUrlEntity` instances |
| aldica.core.binary.optimisation.contentUrlEntity.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `ContentUrlEntity` instances |
| aldica.core.binary.optimisation.contentUrlEntity.useIdsWhenReasonable | `${aldica.core.binary.optimisation.useIdsWhenReasonable}` | enablement flag for using dynamic value substitution for any entities backed by the Alfresco `immutableEntityCache` on `ContentUrlEntity` instances |
| aldica.core.binary.optimisation.contentUrlEntity.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `ContentUrlEntity` instances |
| aldica.core.binary.optimisation.contentUrlEntity.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `ContentUrlEntity` instances |
| aldica.core.binary.optimisation.contentUrlEntity.handle4EiBFileSizes | `${aldica.core.binary.optimisation.rawSerial.handle4EiBFileSizes}` | enablement flag for using static length integer values for files sizes in the raw serial form for `ContentUrlEntity` instances |
| aldica.core.binary.optimisation.contentUrlEntity.useOptimisedContentURL | `${aldica.core.binary.optimisation.rawSerial.useOptimisedContentURL}` | enablement flag for using the optimised content URL serialisation form in the raw serial form for `ContentUrlEntity` instances |
| aldica.core.binary.optimisation.propertyUniqueContextEntity.enabled | `true` | enablement flag for optimisations applied to `PropertyUniqueContextEntity` instances |
| aldica.core.binary.optimisation.propertyUniqueContextEntity.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `PropertyUniqueContextEntity` instances |
| aldica.core.binary.optimisation.propertyUniqueContextEntity.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `PropertyUniqueContextEntity` instances |
| aldica.core.binary.optimisation.propertyUniqueContextEntity.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `PropertyUniqueContextEntity` instances |
| aldica.core.binary.optimisation.storeRef.enabled | `true` | enablement flag for optimisations applied to `StoreRef` instances |
| aldica.core.binary.optimisation.storeRef.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `StoreRef` instances |
| aldica.core.binary.optimisation.storeRef.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `StoreRef` instances |
| aldica.core.binary.optimisation.nodeRef.enabled | `true` | enablement flag for optimisations applied to `NodeRef` instances |
| aldica.core.binary.optimisation.nodeRef.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `NodeRef` instances |
| aldica.core.binary.optimisation.nodeRef.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `NodeRef` instances |
| aldica.core.binary.optimisation.qname.enabled | `true` | enablement flag for optimisations applied to `QName` instances |
| aldica.core.binary.optimisation.qname.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `QName` instances |
| aldica.core.binary.optimisation.qname.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `QName` instances |
| aldica.core.binary.optimisation.mlText.enabled | `true` | enablement flag for optimisations applied to `MLText` instances |
| aldica.core.binary.optimisation.mlText.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `MLText` instances |
| aldica.core.binary.optimisation.mlText.useIdsWhenReasonable | `${aldica.core.binary.optimisation.useIdsWhenReasonable}` | enablement flag for using dynamic value substitution for any entities backed by the Alfresco `immutableEntityCache` on `MLText` instances |
| aldica.core.binary.optimisation.mlText.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `MLText` instances |
| aldica.core.binary.optimisation.mlText.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `MLText` instances |
| aldica.core.binary.optimisation.contentData.enabled | `true` | enablement flag for optimisations applied to `ContentData` / `ContentDataWithId` instances |
| aldica.core.binary.optimisation.contentData.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `ContentData` / `ContentDataWithId` instances |
| aldica.core.binary.optimisation.contentData.useIdsWhenReasonable | `${aldica.core.binary.optimisation.useIdsWhenReasonable}` | enablement flag for using dynamic value substitution for any entities backed by the Alfresco `immutableEntityCache` on `ContentData` / `ContentDataWithId` instances |
| aldica.core.binary.optimisation.contentData.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `ContentData` / `ContentDataWithId` instances |
| aldica.core.binary.optimisation.contentData.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `ContentData` / `ContentDataWithId` instances |
| aldica.core.binary.optimisation.contentData.handle4EiBFileSizes | `${aldica.core.binary.optimisation.rawSerial.handle4EiBFileSizes}` | enablement flag for using static length integer values for files sizes in the raw serial form for `ContentData` / `ContentDataWithId` instances |
| aldica.core.binary.optimisation.contentData.useOptimisedContentURL | `${aldica.core.binary.optimisation.rawSerial.useOptimisedContentURL}` | enablement flag for using the optimised content URL serialisation form in the raw serial form for `ContentData` / `ContentDataWithId` instances |
| aldica.core.binary.optimisation.moduleVersionNumber.enabled | `true` | enablement flag for optimisations applied to `ModuleVersionNumber` instances |
| aldica.core.binary.optimisation.moduleVersionNumber.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `ModuleVersionNumber` instances |
| aldica.core.binary.optimisation.moduleVersionNumber.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `ModuleVersionNumber` instances |
| aldica.core.binary.optimisation.nodeAspects.enabled | `${aldica.core.binary.optimisation.enabled}` | enablement flag for optimisations applied to `NodeAspectsCacheSet` instances - this flag also toggles the use of a `TransactionalCache` sub-class which transparently converts a regular node aspects set into a `NodeAspectsCacheSet`, so that its serialisation can be targeted for optimisation |
| aldica.core.binary.optimisation.nodeAspects.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `NodeAspectsCacheSet` instances |
| aldica.core.binary.optimisation.nodeAspects.useIdsWhenReasonable | `${aldica.core.binary.optimisation.useIdsWhenReasonable}` | enablement flag for using dynamic value substitution for any entities backed by the Alfresco `immutableEntityCache` on `NodeAspectsCacheSet` instances |
| aldica.core.binary.optimisation.nodeAspects.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `NodeAspectsCacheSet` instances |
| aldica.core.binary.optimisation.nodeAspects.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `ContentData` / `NodeAspectsCacheSet` instances |
| aldica.core.binary.optimisation.nodeProperties.enabled | `${aldica.core.binary.optimisation.enabled}` | enablement flag for optimisations applied to `NodePropertiesCacheMap` instances - this flag also toggles the use of a `TransactionalCache` sub-class which transparently converts a regular node properties map into a `NodePropertiesCacheMap`, so that its serialisation can be targeted for optimisation |
| aldica.core.binary.optimisation.nodeProperties.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `NodePropertiesCacheMap` instances |
| aldica.core.binary.optimisation.nodeProperties.useIdsWhenReasonable | `${aldica.core.binary.optimisation.useIdsWhenReasonable}` | enablement flag for using dynamic value substitution for any entities backed by the Alfresco `immutableEntityCache` on `NodePropertiesCacheMap` instances |
| aldica.core.binary.optimisation.nodeProperties.useIdsWhenPossible | `${aldica.core.binary.optimisation.useIdsWhenPossible}` | enablement flag for using dynamic value substitution for all entities backed by secondary Alfresco caches on `NodePropertiesCacheMap` instances |
| aldica.core.binary.optimisation.nodeProperties.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `NodePropertiesCacheMap` instances |
| aldica.core.binary.optimisation.nodeProperties.handleNegativeIds | `${aldica.core.binary.optimisation.rawSerial.handleNegativeIds}` | enablement flag for supporting negative database IDs in the raw serial form for `NodePropertiesCacheMap` instances |
| aldica.core.binary.optimisation.ticket.enabled | `${aldica.core.binary.optimisation.enabled}` | enablement flag for optimisations applied to `Ticket` instances |
| aldica.core.binary.optimisation.ticket.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `Ticket` instances |
| aldica.core.binary.optimisation.ticket.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `Ticket` instances |
| aldica.core.binary.optimisation.duration.enabled | `${aldica.core.binary.optimisation.enabled}` | enablement flag for optimisations applied to `Duration` instances |
| aldica.core.binary.optimisation.duration.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `Duration` instances |
| aldica.core.binary.optimisation.duration.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `Duration` instances |
| aldica.core.binary.optimisation.authenticationCacheEntry.enabled | `${aldica.core.binary.optimisation.enabled}` | enablement flag for optimisations applied to `RepositoryAuthenticationDao$CacheEntry` instances |
| aldica.core.binary.optimisation.authenticationCacheEntry.useRawSerial | `${aldica.core.binary.optimisation.useRawSerial}` | enablement flag for using raw serial form for `RepositoryAuthenticationDao$CacheEntry` instances |
| aldica.core.binary.optimisation.authenticationCacheEntry.useVariableLengthIntegers | `${aldica.core.binary.optimisation.rawSerial.useVariableLengthIntegers}` | enablement flag for using variable length integer values in the raw serial form for `RepositoryAuthenticationDao$CacheEntry` instances |

### Internal Properties

These properties should generally not need to be set / modified. They refer to various internal Ignite configuration properties which have been set to reasonable defaults, and have solely been prepared / exposed as properties just in case / for the highly unexpected case one user / deployment runs into an issue where those might need to be altered.

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.core.failureDetectionTimeout | `10000` | Timeout (in ms) for detecting various kinds of failures in grid communication - this property provides the default for a variety of other timeouts, but since we use dedicated configuration properties for those, its presence is only meant to provide a fallback for any timeout that might have been missed or may be added in future releases of Apache Ignite |
| aldica.core.systemWorkerBlockedTimeout | `${aldica.core.failureDetectionTimeout}` | Timeout (in ms) for detecting a system worker thread to be blocked / in a non-responsive state |
| aldica.core.publicThreadPoolSize | `8` | Number of threads in the Ignite public thread pool, responsible for processing distributed compute jobs - default Ignite would actually use `Math.max(8, #available_proc_count)` without this property, though aldica currently does not use distributed compute jobs (setting a value of `0` is not supported) |
| aldica.core.stripedThreadPoolSize| `${aldica.core.publicThreadPoolSize}` | Number of threads in the Ignite striped thread pool, responsible for processing cache requests jobs - default Ignite would actually use `Math.max(8, #available_proc_count)` without this property (setting a value of `0` is not supported) |
| aldica.core.serviceThreadPoolSize | `${aldica.core.publicThreadPoolSize}` | Number of threads in the Ignite service thread pool, responsible for processing distributed service proxy invocations - default Ignite would actually use `Math.max(8, #available_proc_count)` without this property, though aldica currently does not use distributed service proxies (setting a value of `0` is not supported) |
| aldica.core.systemThreadPoolSize | `${aldica.core.publicThreadPoolSize}` | Number of threads in the Ignite system thread pool, responsible for processing internal system messages - default Ignite would actually use `Math.max(8, #available_proc_count)` without this property (setting a value of `0` is not supported) |
| aldica.core.asyncCallbackThreadPoolSize | `${aldica.core.failureDetectionTimeout}` | Number of threads in the Ignite async callback thread pool, responsible for processing asynchronous callback - default Ignite would actually use `Math.max(8, #available_proc_count)` without this property (setting a value of `0` is not supported) |
| aldica.core.managementThreadPoolSize | `4` | Number of threads in the Ignite management pool, responsible for processing internal / visor compute jobs (setting a value of `0` is not supported) |
| aldica.core.peerClassLoadingThreadPoolSize | `1` | Number of threads in the Ignite async callback thread pool, responsible for processing loading of classes from remote servers - aldica in its current state disallows peer class loading, so this is set even lower than the default value of `2` (setting a value of `0` is not supported) |
| aldica.core.igfsThreadPoolSize | `1` | Number of threads in the Ignite file system pool, responsible for processing outgoing Ignite file system messages - aldica in its current state does not use the distributed Ignite file system either directly or indirectly, so this is set lower than the default value of `#available_proc_count` (setting a value of `0` is not supported) |
| aldica.core.dataStreamerThreadPoolSize | `${aldica.core.publicThreadPoolSize}` | Number of threads in the Ignite data streamer pool, responsible for processing data stream messages - default Ignite would actually use `Math.max(8, #available_proc_count)` without this property (setting a value of `0` is not supported) |
| aldica.core.utilityCacheThreadPoolSize | `${aldica.core.publicThreadPoolSize}` | Number of threads in the Ignite utility pool, responsible for processing utility cache messages - default Ignite would actually use `Math.max(8, #available_proc_count)` without this property (setting a value of `0` is not supported) |
| aldica.core.queryThreadPoolSize | `1` | Number of threads in the Ignite query pool, responsible for processing query messages - aldica in its current state does not use the distributed queries either directly or indirectly, so this is set significantly lower than the default value of `Math.max(8, #available_proc_count)` (setting a value of `0` is not supported) |
| aldica.core.rebalanceThreadPoolSize | `4` | Number of threads in the Ignite rebalance pool, responsible for processing rebalancing of cached data on join / leave of servers (setting a value of `0` is not supported, and the value must be lower than _aldica.core.systemThreadPoolSize_ ) |

## Ignite-backed Caches

The following configuration properties affect Ignite-backed cache instances. This mostly refers to instances using the default Alfresco caching framework based on the `SimpleCache` interface, though individual properties may also affect the Alfresco lock store and asynchronously refreshed caches.

### High-Level Properties

| Property | Default Value | Description |
| --- | ---: | --- |
|  aldica.caches.enabled | `true`  | Central enablement flag for the cache feature of the module - if set to `false`, the module will not replace the default cache factory implementation with its own variant, nor replace / alter the lock store factory and asynchronously refreshed caches to work with Ignite-backed caches |
| aldica.caches.instance.name | `${aldica.core.name}` | The name of the data grid to use for instantiating Ignite caches |
| aldica.caches.remoteSupport.enabled | `false` | Enablement flag for the distributed nature of caches - if set to `true`, this will enable the use of invalidating, partitioned and fully replicated caches, otherwise all caches configured as distributed will automatically be downgraded to the equivalent local cache type |
| aldica.caches.partitionsCount | `32` | The default number of partitions to split partitioned / replicated caches into - should generally be significantly higher than the number of servers in a data grid |
| aldica.caches.ignoreDefaultEvictionConfiguration | `true` | Control flag to determine whether the cache-specific properties relating to on-heap cache behaviour will use / fallback to the default Alfresco cache configuration, or ignore them - defaults to `true` to ignore the default properties in order to provide a default configuration of off-heap caching only, the configuration constellation with the lowest footprint on memory usage |
| aldica.caches.disableAllStatistics | `false` | Control flag to determine whether all Ignite-backed caches should have their statistics collection disabled |
| aldica.webSessionCache.enabled | `false` | Enablement flag for the pre-configured cache to handle HTTP session replication between servers for full failover / high-availability functionality (requires modification of Alfresco Repository web.xml to fully enable) |
| aldica.webSessionCache.gridName | `${aldica.core.name}` | The name of the data grid to use for instantiating the web session cache |
| aldica.webSessionCache.cacheName | `servlet.webSesssionCache` | The unique name / identifier of the cache, which must be identical on all active servers in the data grid |
| aldica.webSessionCache.retriesOnFailure | `2` | The number of retries that should be attempted when retrieving / storing a web session |
| aldica.webSessionCache.retriesTimeout | `5000` | The timeout (in ms) between retries that should be attempted when retrieving / storing a web session |
| aldica.webSessionCache.keepBinary | `true` | Flag to control whether the cache should keep / use the serialised form of the web session across all cache tiers of Ignite (on-heap, off-heap) |
| aldica.webSessionCache.cacheMode | `REPLICATED` | The mode of the web session cache - defaults to `REPLICATED` for the best possible read performance (as HTTP sessions are rarely modified on the Alfresco Repository tier) and least chance for data loss in case of a sudden failure of a data grid member |
| aldica.webSessionCache.backups | `1` | The number of backups to keep for each partition of the cache |
| aldica.webSessionCache.maxSize | `10000` | The maximum amount of session to keep in the on-heap cache | 

### Cache-Specific Properties

The following configuration properties are supported for individual cache instances. The configuration approach is based on the [default Alfresco cache instance configuration properties](https://docs.alfresco.com/6.1/concepts/cache-indsettings.html). The aldica module processes these configuration properties with a basic inheritance scheme using the following precedence order:

1. cache.&lt;name&gt;.ignite.&lt;customPropertyName&gt; (if set)
2. cache.&lt;name&gt;.&lt;customPropertyName&gt; (if set)
3. cache.&lt;name&gt;.&lt;equivalentAlfrescoPropertyName&gt; (if an Alfresco equivalent exists / is allowed to be used)

In an example lookup of the property defining the type for the cache "ticketsCache", the order would be:

1. _cache.ticketsCache.ignite.cache.type_
2. _cache.ticketsCache.cache.type_
3. _cache.ticketsCache.cluster.type_ set? (cluster.type is the default Alfresco-equivalent of cache.type)

In this instance, step no. 1 would find a value as the aldica module provides a custom setting for _cache.ticketsCache.ignite.cache.type_ with the out-of-the-box configuration. For most other caches, step no. 3 would yield the cache / cluster type configured in default Alfresco, unless an administrator provided custom configuration.

The following listing of all supported properties only includes the name of the specific property without the cache specific prefix _cache.&lt;name&gt;._, including the various additional static fragments in the basic inheritance / lookup scheme.

| Property | Alfresco-equivalent | Description |
| --- | --- | --- |
| cache.type | cluster.type | Type of the cache - supported values are the aldica cache types listed in [cache concept page](./Concept-Caches.md) and default Alfresco values are mapped accordingly to types supported by aldica |
| dataRegionName |  | Name of a custom data region (see "Storage Properties") which should hold the off-heap data of this cache |
| heap.maxMemory |  | Maximum amount of memory (in bytes) that on-heap stored cache data is allowed to use before eviction of on-heap data is triggered - defaults to `0` as "not configured" |
| heap.maxItems | maxItems | Maximum number of on-heap stored cache entries that are allowed before eviction of on-heap data is triggered - defaults to `0` as "not configured", unless _aldica.caches.ignoreDefaultEvictionConfiguration_ is set to `false` and a default value is configured using the Alfresco-equivalent property |
| heap.eviction-policy | eviction-policy | Policy to use for the eviction of on-heap data - defaults to `NONE` unless _aldica.caches.ignoreDefaultEvictionConfiguration_ is set to `false` and a default policy is configured using the Alfresco-equivalent property |
| heap.batchEvictionItems |  | Number of on-heap cache entries to evict in a batch when eviction of on-heap data is triggered by exceeding the _heap.maxItems_ limit - defaults to `0` |
| heap.eviction-percentage | eviction-percentage (until Alfresco 5.2) | Percentage of on-heap cache entries to evict in a batch when eviction of on-heap data is triggered by exceeding the _heap.maxItems_ limit - defaults to `0` unless _aldica.caches.ignoreDefaultEvictionConfiguration_ is set to `false` and a default value is configured using the Alfresco-equivalent property |
| timeToLiveSeconds | timeToLiveSeconds | The time-to-live (in s) for an individual cache entry after it has been created or updated - defaults to `0` as "no expiry" unless a default value is configured using the Alfresco-equivalent property |
| maxIdleSeconds | maxIdleSeconds | The time-to-live (in s) for an individual cache entry after it has last been accessed in the cache - defaults to `0` as "no expiry" unless a default value is configured using the Alfresco-equivalent property |
| allowValueSentinels |   | Flag to specify if value sentinels for `null` and `not-found` defined by the `EntityLookupCache` class are allowed to be stored in the cache - defaults to `true` for consistency with default Alfresco cache behaviour |
| forceInvalidateOnPut |   | Flag to specify if a cache put operation in a partitioned cache should always trigger an invalidation message to other data grid members, even if no effective change has occurred (no replacement of data, e.g. only a simple load-from-db operation) - defaults to `true` for consistency with default Alfresco cache behaviour |
| near.maxMemory |   | Maximum amount of memory (in bytes) that on-heap stored cache data in a near cache (for a partitioned cache) is allowed to use before eviction of on-heap data is triggered - defaults to 1/4 the effective value of _heap.maxMemory_ |
| near.maxItems |   | Maximum number of on-heap stored cache entries in a near cache (for a partitioned cache) that are allowed before eviction of on-heap data is triggered - defaults to 1/4 the effective value of _heap.maxItems_ |
| near.eviction-policy |   | Policy to use for the eviction of on-heap data in a near cache (for a partitioned cache) - defaults to the effective value of `heap.evicition-policy` |
| near.batchEvictionItems |   | Number of on-heap cache entries in a near cache (for a partitioned cache) to evict in a batch when eviction of on-heap data is triggered by exceeding the _near.maxItems_ limit - defaults to the effective value of _heap.batchEvictionItems_ |
| near.eviction-percentage |   | Percentage of on-heap cache entries in a near cache (for a partitioned cache) to evict in a batch when eviction of on-heap data is triggered by exceeding the _near.maxItems_ limit - defaults to the effective value of _heap.eviction-percentage_ |

## Web Session Cache

The configuration of the web session cache requires a change to the default Alfresco Repository _web.xml_ file in addition to setting one or more properties in _alfresco-global.properties_. Due to limitations in the Java Servlet specification, it is not possible to provide this feature in a way that does not require this change by the administrator / developer / end-user who wish to use this feature.

### Configuration Properties

| Property | Default Value | Description |
| --- | ---: | --- |
| aldica.webSessionCache.enabled | `false`  | Central enablement flag for the Ignite web session cache - if set to `false` the cache will be inactive regardless of the configuration change made to _web.xml_ |
| aldica.webSessionCache.instanceName | `${aldica.core.name}` | The name of the data grid to use for instantiating the Ignite cache |
| aldica.webSessionCache.cacheName | `servlet.webSessionCache` | The name of the Ignite cache to instantiate for the feature |
| aldica.webSessionCache.retriesOnFailure | `2` | The number of retries that should be attempted when a cache operation affecting a session failed |
| aldica.webSessionCache.retriesTimeout | `5000` | The number of milliseconds before a retry cache operation affecting a session will timeout |
| aldica.webSessionCache.keepBinary | `true` | Technical flag to specify whether the Ignite backed cache should keep the internal binary representation on all internal layers - should never need to be changed |
| aldica.webSessionCache.cacheMode | `REPLICATED` | The mode in which the Ignite cache should operate - no other cache mode makes sense for the use case of a distributed web session cache, so this should never need to be changed |
| aldica.webSessionCache.maxSize | `10000` | The limit of session objects to hold in the on-heap cache |

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
