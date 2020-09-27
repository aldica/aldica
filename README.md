![aldica logo](./logo.png)

## Name

The portmanteau "aldica" can be taken to stand for either

- Alternative Distributed Caching, or
- Alfresco Distributed Caching

The primary / canon long name is Alternative Distributed Caching.

## About

This module project provides a distributed caching and data grid module for Alfresco Content Services and Share (Community Edition). It currently uses the [Apache Ignite](https://ignite.apache.org) library as the underlying cache and data grid framework, and enables multiple Alfresco Content Services / Share instances to share cache state and exchange messages about data invalidations. With this module, Alfresco Community Edition may be horizontally scaled by adding multiple instances on different hosts or in multiple containers (e.g. as part of a Kubernetes cluster). Even if horizontal scaling is not required, this module can still be used to improve the overall scalability / performance of a single server installation of Alfresco Content Services, by providing improved implementations for its caching layer, e.g. using off-heap memory and even disk-based swap space to reduce pressure on the Java Garbage Collector while allowing significantly larger amounts of data to be cached, reducing load and latency of database accesses.

## Compatibility

The module of this project is built to be compatible with Alfresco Content Services 6.0.7 GA / Alfresco Share 6.0.c and above. Since the Alfresco core APIs on which this module relies have remained stable in the 6.x release lines, the module should also be compatible with Alfresco Content Services 5.2.g / Alfresco Share 5.2.f.

**Note**: Alfresco Enterprise Edition already ships with custom cache implementations that are tied directly to its Enterprise clustering feature. Though we have managed to install aldica and use its cache provider on a trial version of Alfresco Enterprise Edition with only a minor bit of tinkering, we cannot generally support Alfresco Enterprise Edition. Its code is not open to us and may change without notice / disclosure in any service pack or hotfix release, so we cannot safely adapt aldica to work without risk to Enterprise features in the range of versions of Alfresco Content Services that we would like to support. Furthermore, Enterprise customers choosing to install aldica would likely be left in a system state that Alfresco Support could rightfully refuse to support when issues are filed for problems remotely related to caching. As an open source project, we cannot take on the warranty / liability of such systems either.

### Known Issue(s) / Limitation(s) with Alfresco 5.2

Due to an issue with transactional resource and cleanup handling (e.g. [SPR-15194](https://jira.spring.io/browse/SPR-15194)) fixed only with an upgraded Spring library in Alfresco 6.x, parts of aldica's serialisation improvements to reduce the memory footprint of cached values cannot be used on Alfresco 5.2. These must be disabled by setting the following Repository-tier global properties:

```
aldica.core.binary.optimisation.useIdsWhenReasonable=false
aldica.core.binary.optimisation.useIdsWhenPossible=false
```

## Published Release / SNAPSHOT Artifacts

All artifacts of released versions of this module will be published on Maven Central, using the group ID _org.aldica_ . SNAPSHOT versions of artifacts will be published on the [Sonatype Open Source Software Repository Hosting](https://oss.sonatype.org) service. In order to reference SNAPSHOT versions in Maven-based projects, an appropriate repository section has to be added to the project's POM file:

```xml
    <repositories>
        <repository>
            <id>ossrh</id>
            <url>https://oss.sonatype.org/content/repositories/snapshots</url>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>
```

## Dependencies

This module depends on the following projects / libraries:

- Apache Ignite (Apache License, Version 2.0)
- IntelliJ IDEA Annotations (Apache License, Version 2.0)
- Acosix Alfresco Utility (Apache License, Version 2.0)

The module AMPs produced by the build of this project will include all the necessary components / libraries provided by the Apache Ignite project and IntelliJ IDEA Annotations. The Acosix Alfresco Utility project itself provides its own module AMPs, which need to be installed in Alfresco Content Services / Share before the corresponding AMP of this project can be installed.

When the installable JARs produced by the build of this project are used for installation, the developer / user is responsible to either manually install all the required components / libraries provided by the listed projects, or use a build system to collect all relevant direct / transitive dependencies.
**Note**: The Acosix Alfresco Utility project is also built using templates from the Acosix Alfresco Maven project, and as such produces similar artifacts. Automatic resolution and collection of (transitive) dependencies using Maven / Gradle will resolve the Java *classes* JAR as a dependency, and **not** the installable (Simple Alfresco Module) variant. It is recommended to exclude Acosix Alfresco Utility from transitive resolution and instead include it directly / explicitly.

## Documentation

The documentation is maintained as part of the source code of the alternative/Alfresco distributed caching (aldica) module. Each Markdown document represents an individual, cohesive section / topic of documentation, though may reference other sections which provide either more detailed information in a sub-topic or aggregate partial information bits from multiple sections (e.g. configuration reference sections).

- [Build](./docs/Build.md)
- Getting Started
    - [ACS configuration scenarios](./docs/GettingStarted-ACS-Scenarios.md)
    - [Share configuration scenarios](./docs/GettingStarted-Share-Scenarios.md)
    - [Repository companion application](./docs/GettingStarted-Companion-App.md) 
    - [Tomcat](./docs/GettingStarted-Tomcat.md) (to be reviewed / updated)
- Installation / Configuration
    - [Install via Alfresco SDK](./docs/Installation-SDK4.md)
    - [Install via Docker Build](./docs/Installation-Docker.md)
    - [Install via Kubernetes](./docs/Installation-Kubernetes.md)
    - [Java Virtual Machine (JVM) Properties](./docs/Configuration-JVMProperties.md)
    - [Repository Configuration Reference](./docs/Configuration-RepoReference.md)
    - [Share Configuration Reference](./docs/Configuration-ShareReference.md)
- Concepts / High-Level
    - [Alfresco/Ignite and On-Heap vs Off-Heap Caches](./docs/Concept-Caches.md)
    - [Grid Member Discovery](./docs/Concept-GridMemberDiscovery.md)
    - [Binary Serialisation Optimisations](./docs/Concept-BinarySerialiser.md)
- Verification / Tests
    - [K6 Benchmark](./docs/Test-K6.md)
    - [Memory Benchmark](./docs/Test-Memory-BM.md)
    - [JMeter Benchmark](./docs/Test-JMeter.md)
    - [Manual Verification](./docs/Test-Manual.md)
