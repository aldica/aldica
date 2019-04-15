# About
This module project provides a distributed caching and data grid module for Alfresco Content Services and Share (Community Edition). It currently uses the [Apache Ignite](https://ignite.apache.org) library as the underlying cache and data grid framework, and enables multiple Alfresco Content Services / Share instances to share cache state and exchange messages about data invalidations. With this module, Alfresco Community Edition may be horizontally scaled by adding multiple instances on different hosts or in multiple containers (e.g. as part of a Kubernetes cluster). Even if horizontal scaling is not required, this module can still be used to improve the overall scalability / performance of a single server installation of Alfresco Content Services, by providing improved implementations for its caching layer, e.g. using off-heap memory and even disk-based swap space to reduce pressure on the Java Garbage Collector while allowing significantly larger amounts of data to be cached, reducing load and latency of database accesses.

## Compatibility

The module of this project is built to be compatible with Alfresco Content Services 6.0.7 GA / Alfresco Share 6.0.c and above. Since the Alfresco core APIs on which this module relies have remained stable in the 6.x release lines, the module should also be compatible with Alfresco Content Services / Alfresco Share 5.2.f.

# Build

This project uses a Maven build using templates from the [Acosix Alfresco Maven](https://github.com/Acosix/alfresco-maven) project and produces module AMPs, regular Java *classes* JARs, JavaDoc and source attachment JARs, as well as installable (Simple Alfresco Module) JAR artifacts for the Alfresco Content Services and Share extensions. If the installable JAR artifacts are used for installing this module, developers / users are advised to consult the 'Dependencies' section of this README.

## Docker-based integration tests

In a default build using ```mvn clean install```, this project will build the extensions for Alfresco Content Services and Share, executing regular unit-tests, without running integration tests. The integration tests of this project are based on Docker and require a Docker engine to run the necessary components (PostgreSQL database as well as Alfresco Content Services / Share). Since a Docker engine may not be available in all environments of interested community members / collaborators, the integration tests have been made optional. A full build, including integration tests, can be run by executing

```
mvn clean install -P run-integration-tests -Ddocker.tests.enabled=true
```

The profile (-P) enables running integration tests in general, and the system property (-D) enables the Docker deployment support (the Acosix ALfresco Maven project includes provisions for more than one deployment variant). Both are required to be set.

## K6 benchmark / load tests

TODO

## Dependencies

This module depends on the following projects / libraries:

- Apache Ignite (Apache License, Version 2.0)
- IntelliJ IDEA Annotations (Apache License, Version 2.0)
- Acosix Alfresco Utility (Apache License, Version 2.0)

The module AMPs produced by the build of this project will include all the necessary components / libraries provided by the Apache Ignite project and IntelliJ IDEA Annotations. The Acosix Alfresco Utility project itself provides its own module AMPs, which need to be installed in Alfresco Content Services / Share before the corresponding AMP of this project can be installed.

When the installable JARs produced by the build of this project are used for installation, the developer / user is responsible to either manually install all the required components / libraries provided by the listed projects, or use a build system to collect all relevant direct / transitive dependencies.
**Note**: The Acosix Alfresco Utility project is also built using templates from the Acosix Alfresco Maven project, and as such produces similar artifacts. Automatic resolution and collection of (transitive) dependencies using Maven / Gradle will resolve the Java *classes* JAR as a dependency, and **not** the installable (Simple Alfresco Module) variant. It is recommended to exclude Acosix Alfresco Utility from transitive resolution and instead include it directly / explicitly.

# Getting Started / Configuration

## JVM parameters

While not technically required, it is recommended to add various JVM parameters to the start script / configuration of the Alfresco Content Services / Alfresco Share processes to adjust for the use of the underlying Apache Ignite library and keep console / log output lean.

### Java Heap and Garbage Collection

In line with [recommendations](https://apacheignite.readme.io/docs/jvm-and-system-tuning) of the Apache Ignite project, we recommend enabling the Garbage First collector and setting some additional parameters to optimize memory handling. Without the parameters for the min/max size of the Java heap, the following parameters should be set (typically as JAVA_OPTS):

```
-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:+UseStringDeduplication -XX:+ScavengeBeforeFullGC -XX:+DisableExplicitGC -XX:+AlwaysPreTouch
```

Please see the documentation for your specific JVM for details of these settings.

### Apache Ignite system properties

Apache Ignite takes some global configuration from system properties, and does not provide APIs for them to be set via its regular configuration mechanisms. As such, these properties typically need to be set via JAVA_OPTS as -D parameters. The following parameters are recommended as a starting point:

```
-DIGNITE_PERFORMANCE_SUGGESTIONS_DISABLED=true -DIGNITE_QUIET=true -DIGNITE_NO_ASCII=true -DIGNITE_UPDATE_NOTIFIER=false -DIGNITE_JVM_PAUSE_DETECTOR_DISABLED=true
```

These parameters primarily reduce the "noise" Apache Ignite creates in the console / log output during startup and continuous operation, and have no impact on the actual operation of the extensions of this project. For details on these parameters, please consult the Apache Ignite [documentation](https://ignite.apache.org/releases/latest/javadoc/org/apache/ignite/IgniteSystemProperties.html).

## Alfresco Content Services

### Trivial: running on a single node

In the simplest deployment scenario, this module is only installed on a single Alfresco Content Services instance. In this configuration, the module does not require any mandatory configuration and is ready to run out-of-the-box. The following optional, high-level configuration parameters in alfresco-global.properties may still be of interest:

- *acog.core.enabled*: true/false configuration to globally control the activation of this module's features - defaults to true
- *acog.caches.enabled*: true/false configuration to globally control the activation of the cache layer feature of this module - defaults to true
- *acog.core.storage.defaultStorageRegion.initialSize*: the amount of off-heap memory to pre-allocate for storage (caches) - defaults to 1 GiB
- *acog.core.storage.defaultStorageRegion.maxSize*: the limit to the amount of off-heap memory allowed to be used for storage (caches) - defaults to 16 GiB

**Note**: This module uses memory-mapped files to access off-heap memory for caching (path defined by parameter *acog.core.storage.defaultStorageRegion.swapPath* - defaults to the Tomcat temporary files folder). This allows the storage to greatly exceed the total amount of physical memory available in the environment. This is why the default limit is set to 16 GiB, even though many small systems may not have that much memory available. By using memory-mapped files for off-heap memory / storage, swap handling is delegated to the operating system. For optimal performance, it is recommended to either have sufficient physical memory available or ensure an SSD devices is used for the swap path.

### Simple: automatic registration and discovery

In a simple deployment scenario of multiple Alfresco Content Services instances using this module, little to no configuration is required. This module's extension for Alfresco Content Services includes features for automatic self-registration of instances via the Alfresco AttributeService (stored in the database) and automatic discovery of other, existing / running instances using the same registration data. Provided that there is no network address translation (NAT) or firewall affecting the network connections between the various Alfresco Content Services instances, and all instances access the same, central database instance, these two capabilities enable the module to work with only a minimal amount of mandatory configuration.

The following configuration parameters in alfresco-global.properties must be altered to ensure distributed caching is enabled:

- *acog.core.caches.enableRemoteSupport*: true/false configuration to control the activation of distributed caches - defaults to false

THe following configuration parameters in alfresco-global.properties concern other key options, which should be considered: 

- *acog.core.enabled*: true/false configuration (default: true) to globally control the activation of this module's features - defaults to true
- *acog.caches.enabled*: true/false configuration (default: true) to globally control the activation of the cache layer feature of this module - defaults to true
- *acog.core.name*: the name of the data grid to establish / connect to, which can be used to ensure instances only join the "right" grid - defaults to 'repositoryGrid'
- *acog.core.login*: the login name required / used to connect to the data grid, which can be used to ensure instances only join the "right" grid - defaults to 'repository'
- *acog.core.password*: the login password required / used to connect to the data grid, which can be used to ensure instances only join the "right" grid - defaults to 'repositoryGrid-dev'
- *acog.core.local.id*: a stable / consistent ID for the specific Alfresco Content Services instance, which can help various grid / node failure resolutions on the communication layer - has no fixed default value and will dynamically construct an ID based on network addresses and the bound port, unless an explicit ID is set

### Advanced: running with network address translation (NAT) / in a containerised environment

In any deployment where the network communication between instances of Alfresco Content Services is affected by network address translation, additional configuration properties need to be set (in contrast to a simple automatic registration and discovery scenario) in order to ensure correct communication within the data grid used for distributed caching. Any of the following conditions qualify a deployment for this scenario:

- TCP ports on which Apache Ignite binds are rewritten, e.g. through iptables or Docker port mapping
- (name / address) address as determined by the JVM running Alfresco Content Services cannot be used by other instances to establish a network connection, e.g. when instances are located in different physical / virtual networks
- multiple Alfresco Content Service instances run in the same environment and need to avoid address conflicts

The following configuration parameters in alfresco-global.properties can be set to deal with network address translation scenarios:

- *acog.core.local.host*: address (IP or host name) on which to bind, e.g. to select a specific network interface - empty (default) to bind to all addresses
- *acog.core.public.host*: publicly accessible address (IP or host name) to use in exchange with other data grid nodes / automatic registration and discovery - empty (default) if no address translation should be performed
- *acog.core.local.comm.port*: local TCP port on which to bind the Apache Ignite data grid discovery handling - defaults to 47100
- *acog.core.local.comm.portRange*: number of alternate TCP ports starting from the *acog.core.local.comm.port* to try binding to in case the configured port is already in use - defaults to 0 (only configured port is allowed and conflict means instance will fail to start)
- *acog.core.local.comm.filterReachableAddresses*: true/false configuration (default: true) to determine if data grid node addresses provided during discovery / join events should be checked and filtered for being "accessible" before any attempt to open a connection is made - though checking / filtering addresses introduces an overhead, this typically is more efficient than just attempting a connection and waiting for a socket timeout / connection refused, especially since the checks are performed in parallel while connection attempts cycle through known addresses sequentially
- *acog.core.public.comm.port*: publicly accessible TCP port for the Apache Ignite data grid core communication handling - empty (default) if no port remapping applies to the Alfresco Content Services instances
- *acog.core.local.disco.port*: local TCP port on which to bind the Apache Ignite data grid discovery handling - defaults to 47110
- *acog.core.local.disco.portRange*: number of alternate TCP ports starting from the *acog.core.local.disco.port* to try binding to in case the configured port is already in use - defaults to 0 (only configured port is allowed and conflict means instance will fail to start)
- *acog.core.public.disco.port*: publicly accessible TCP port for the Apache Ignite data grid discovery handling - empty (default) if no port remapping applies to the Alfresco Content Services instances 
- *acog.core.local.time.port*: local TCP port on which to bind the Apache Ignite data grid time server handling - defaults to 47120
- *acog.core.local.time.portRange*: number of alternate TCP ports starting from the *acog.core.local.time.port* to try binding to in case the configured port is already in use - defaults to 0 (only configured port is allowed and conflict means instance will fail to start)
- *acog.core.public.time.port*: publicly accessible TCP port for the Apache Ignite data grid time server handling - empty (default) if no port remapping applies to the Alfresco Content Services instances

## Alfresco Share

The configuration for Alfresco Share uses the non-standard configuration file share-global.properties provided by the Acosix Utility module. This configuration file can be placed in the *tomcat/shared/classes* directory just like the alfresco-global.properties file for Alfresco Content Services. 

### Trivial: running on a single node

In the simplest deployment scenario, this module is only installed on a single Alfresco Share instance. In this configuration, the module does not require any mandatory configuration and is ready to run out-of-the-box. The following optional, high-level configuration parameters in share-global.properties may still be of interest:

- *acog.core.enabled*: true/false configuration to globally control the activation of this module's features - defaults to true
- *acog.core.storage.defaultStorageRegion.initialSize*: the amount of off-heap memory to pre-allocate for storage (caches) - defaults to 32 MiB
- *acog.core.storage.defaultStorageRegion.maxSize*: the limit to the amount of off-heap memory allowed to be used for storage (caches) - defaults to 128 MiB

**Note**: The Share extension of this project does not provide any alternative caching implementation for Alfresco Share / Alfresco Surf. In the trivial deployment scenario, there will be no functional difference from default Alfresco Share, apart from the fact that an internal data grid is started.

### Simple: grid with explicitly configured initial members

In contrast to Alfresco Content Services, the extension to Alfresco Share of this project cannot provide automatic registration and discovery capabilities since multiple Alfresco Share instances cannot rely on any shared infrastructure for state management, except for Alfresco Content Services, which may not always be available or consistent. As such, running multiple Alfresco Share instances in a grid requires the explicit configuration of "initial members" which should be contacted to join a potentially already existing grid. This can be accomplished by using the parameter *acog.core.initialMembers* in share-global.properties (an identical option exists also for Alfresco Content Services, but is rarely relevant and acts solely as a backup). This parameter supports a comma-separated list of addresses, where each individual address can be:

- a simple IP address or host name
- an IP address or host name, and port for Apache Ignite discovery handling
- an IP address or host name, and port range for Apache Ignite discovery handling, e.g. 5.5.5.5:47110..47115

If no port / port range is provided in an individual value of the initial members list, then the configured TCP port for Apache Ignite handling discovery of this Alfresco Share instance (*acog.core.local.disco.port*) is also assumed to be used for the other instance.

THe following configuration parameters in share-global.properties concern other key options, which should be considered: 

- *acog.core.enabled*: true/false configuration (default: true) to globally control the activation of this module's features - defaults to true
- *acog.core.name*: the name of the data grid to establish / connect to, which can be used to ensure instances only join the "right" grid - defaults to 'shareGrid'
- *acog.core.login*: the login name required / used to connect to the data grid, which can be used to ensure instances only join the "right" grid - defaults to 'share'
- *acog.core.password*: the login password required / used to connect to the data grid, which can be used to ensure instances only join the "right" grid - defaults to 'shareGrid-dev'
- *acog.core.local.id*: a stable / consistent ID for the specific Alfresco Share instance, which can help various grid / node failure resolutions on the communication layer - has no fixed default value and will dynamically construct an ID based on network addresses and the bound port, unless an explicit ID is set

### Advanced: running with network address translation (NAT) / in a containerised environment

The relevant conditions and configuration options for this deployment scenario are nearly identical to the same scenario for Alfresco Content Services. Please refer to that section above for details.
The following configuration parameters have different default values in Alfresco Share:

- *acog.core.local.comm.port*: 47130 (vs. 47100 in Alfresco Content Services)
- *acog.core.local.disco.port*: 47140 (vs. 47110 in Alfresco Content Services)
- *acog.core.local.time.port*: 47150 (vs. 47120 in Alfresco Content Services)