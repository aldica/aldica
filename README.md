![aldica logo](./logo.png)

## Name

The portmanteau "aldica" can be taken to stand for either

- Alternative Distributed Caching, or
- Alfresco Distributed Caching

The primary / canon long name is Alternative Distributed Caching.

## About

This module project provides a distributed caching and data grid module for Alfresco Content Services and Share (Community Edition). It currently uses the [Apache Ignite](https://ignite.apache.org) library as the underlying cache and data grid framework, and enables multiple Alfresco Content Services / Share instances to share cache state and exchange messages about data invalidations. With this module, Alfresco Community Edition may be horizontally scaled by adding multiple instances on different hosts or in multiple containers (e.g. as part of a Kubernetes cluster). Even if horizontal scaling is not required, this module can still be used to improve the overall scalability / performance of a single server installation of Alfresco Content Services, by providing improved implementations for its caching layer, e.g. using off-heap memory and even disk-based swap space to reduce pressure on the Java Garbage Collector while allowing significantly larger amounts of data to be cached, reducing load and latency of database accesses.

## Compatibility

The module of this project is built to be compatible with Alfresco Content Services 7.0 / Alfresco Share 7.0 and above. The change in the minimal supported version compared to earlier versions of this module has been dictated by an update of the Apache Ignite library, requiring use of Java 11 at minimum. While some 6.x versions already used Java 11 in the default Docker images, most were still compiled against Java 8 and Java 8 listed in the supported platforms.

## Published Release / SNAPSHOT Artifacts

All artifacts of released versions and SNAPSHOTS of this module will be published on Maven Central, using the group ID _org.aldica_ . In order to use Snapshots from Maven Central, an explicit repository has to be added in POMs since Maven by default only uses Maven Central to lookup release artifacts.

```xml
<repositories>
    <repository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
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
- Installation / Configuration
    - [Install via Docker Build](./docs/Installation-Docker.md)
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
