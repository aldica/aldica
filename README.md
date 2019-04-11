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



## Alfresco Content Services

### Trivial scenario: running on a single node

In the simplest deployment scenario, this module is only installed on a single Alfresco Content Services instance. In this configuration, the module does not require any mandatory configuration and is ready to run out-of-the-box. The following optional, high-level configuration parameters in alfresco-global.properties may still be of interest:

- *acog.core.enabled*: true/false configuration to globally control the enablement of this modules features - defaults to true
- *acog.caches.enabled*: true/false configuration to globally control the enablement of the cache layer feature of this module - defaults to true
- *acog.core.storage.defaultStorageRegion.initialSize*: the amount of off-heap memory to pre-allocate for storage (caches) - defaults to 1 GiB
- *acog.core.storage.defaultStorageRegion.maxSize*: the limit to the amount of off-heap memory allowed to be used for storage (caches) - defaults to 16 GiB

**Note**: This module uses memory-mapped files to access off-heap memory for caching (path defined by parameter *acog.core.storage.defaultStorageRegion.swapPath* - defaults to the Tomcat temporary files folder). This allows the storage to greatly exceed the total amount of physical memory available in the environment. This is why the default limit is set to 16 GiB, even though many small systems may not have that much memory available. By using memory-mapped files for off-heap memory / storage, swap handling is delegated to the operating system. For optimal performance, it is recommended to either have sufficient physical memory available or ensure an SSD devices is used for the swap path.

### Simple scenario: automatic registration and discovery

In a simple deployment scenario of multiple Alfresco Content Services instances using this module, little to no configuration is required. This module's extension for Alfresco Content Services includes features for automatic self-registration of instances via the Alfresco AttributeService (stored in the database) and automatic discovery of other, existing / running instances using the same registration data. Provided that there is no network address translation (NAT) or firewall affecting the network connections between the various Alfresco Content Services instances, and all instances access the same, central database instance, these two capabilities enable the module to work with only a minimal amount of mandatory configuration.

The following configuration parameters in alfresco-global.properties must be altered to ensure distributed caching is enabled:

- *acog.core.caches.enableRemoteSupport*: true/false configuration to control the enablement of distributed caches - defaults to false

THe following configuration parameters in alfresco-global.properties concern other key options, which should be considered: 

- *acog.core.enabled*: true/false configuration (default: true) to globally control the enablement of this modules features - defaults to true
- *acog.caches.enabled*: true/false configuration (default: true)  to globally control the enablement of the cache layer feature of this module - defaults to true
- *acog.core.name*: the name of the data grid to establish / connect to, which can be used to ensure instances only join the "right" grid - defaults to 'repositoryGrid'
- *acog.core.login*: the login name requried / used to connect to the data grid, which can be used to ensure instances only join the "right" grid - defaults to 'repository'
- *acog.core.password*: the login password requried / used to connect to the data grid, which can be used to ensure instances only join the "right" grid - defaults to 'repositoryGrid-dev'
- *acog.core.local.id*: a stable / consistent ID for the specific Alfresco Content Services instance, which can help various grid / node failure resolutions on the communication layer - has no fixed default value and will dynamically construct an ID based on network addresses and the bound port, unless an explicit ID is set


TODO Continue