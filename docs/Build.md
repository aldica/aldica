# Build

The aldica module project is based on a common Maven-based setup used by Acosix for Alfresco extension projects. The project is **not** using the Alfresco SDK for its various shortcomings, among them its extremely tight coupling with specific release versions of Alfresco, inflexibility with regards to Maven build lifecycles and combination with other plugins for pre-/post-processing, and favouritism concerning AMP vs. JAR packaging.
 
### General Build and Toolchains

As with any Maven-based project, a general build can be performed by executing ``mvn clean install`` either in the top-level project or any sub-module.

By inheritance from the Acosix Alfresco Maven framework, this project uses the [Maven Toolchains plugin](http://maven.apache.org/plugins/maven-toolchains-plugin) to allow potential cross-compilation against different Java versions. This plugin is used to avoid inconsistent compiler and library versions compared to when only the source/target compiler options of the Maven Compiler plugin are set, which - as an example - has caused issues with some Alfresco releases in the past when Alfresco compiled for Java 7 using the Java 8 libraries, producing a build that was apparently compatible with Java 7, but would fail in random situations due to API incompatibilities.

In order to build the project it is necessary to provide a basic toolchain configuration via the user specific Maven configuration home (usually ~/.m2/). That file (toolchains.xml) only needs to list the path to a compatible JDK for the Java version required by this project. The following is a sample file defining a Java 7 and 8 development kit. The aldica module currently only requires the availability of a Java 8 development kit, but may in the future upgrade to Java 11.

```xml
<?xml version='1.0' encoding='UTF-8'?>
<toolchains xmlns="http://maven.apache.org/TOOLCHAINS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/TOOLCHAINS/1.1.0 http://maven.apache.org/xsd/toolchains-1.1.0.xsd">
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.8</version>
      <vendor>oracle</vendor>
    </provides>
    <configuration>
      <jdkHome>C:\Program Files\Java\jdk1.8.0_112</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.7</version>
      <vendor>oracle</vendor>
    </provides>
    <configuration>
      <jdkHome>C:\Program Files\Java\jdk1.7.0_80</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```
Ubuntu example:
```
<?xml version='1.0' encoding='UTF-8'?>
<toolchains xmlns="http://maven.apache.org/TOOLCHAINS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/TOOLCHAINS/1.1.0 http://maven.apache.org/xsd/toolchains-1.1.0.xsd">
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.8</version>
      <vendor>oracle</vendor>
    </provides>
    <configuration>
      <jdkHome>/usr/lib/jvm/java-1.8.0-openjdk-amd64/</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```
The build process will produce two amp files, namely:
```
./repository/target/aldica-repo-ignite-1.0.0.0-SNAPSHOT.amp
./share/target/aldica-share-ignite-1.0.0.0-SNAPSHOT.amp
```

### Docker-based Integration Tests

In a default build using “mvn clean install”, this project will build the extensions for Alfresco Content Services and Share, executing regular unit tests, but not run integration tests. The integration tests of this project are based on Docker and require a Docker engine to run the necessary components (PostgreSQL database as well as Alfresco Content Services / Share). Since a Docker engine may not be available in all environments of interested community members / collaborators, the integration tests have been made optional. A full build, including integration tests, can be run by executing ``mvn clean install -P run-integration-tests -Ddocker.tests.enabled=true``. The profile (``-P``) enables running integration tests in general, and the system property (``-D``) enables the Docker deployment support (the Acosix Alfresco Maven framework includes provisions for more than one deployment variant). Both are required to be set.

This project uses the [fabric8io Docker Maven plugin](http://dmp.fabric8.io) to build the relevant test images, set up custom volumes and networks, and start / stop the application containers. The Repository-tier integration tests are simple JUnit tests using the Resteasy library to perform invocations of the Alfresco v1 ReST API to perform the various verifications. All integration tests are contained in specific Java packages which include an “integration” name fragment used to select which tests to run in the integration test phase.

The Docker-based integration tests will create images named ``aldica-repository-test`` and ``aldica-share-test``. Upon successful completion of the build, these images should always be removed, along with any containers, volumes and custom networks, leaving the Docker engine in the same state as it was before running the build. But when the build fails, some of these resources may be left alive, depending on the type of failure, though by using the Maven Failsafe plugin for running integration tests we try to minimize this as best as possible. In any case, these resources should be automatically removed after the next successful build, but may be removed manually using the appropriate Docker commands.

The Docker containers for Alfresco Repository and Share are started in such a way that the log files created in ``/usr/local/tomcat/logs/`` are written to the ``./target/docker/{containerAlias}-logs/`` directory. In case of a test failure, this allows the application logs to be inspected even though the Docker container has already been stopped and removed. The Log4J configuration files for the integration tests can be found in ``./src/test/docker/``. 

### Expensive Unit Tests

Some of the sub-modules of this project (only the repository sub-module at the time of writing) may contain "expensive" unit tests, which perform longer-than-usual running tests to verify some performance aspects or replay complex usage patterns. Since these tests go beyond what regular unit tests should entail, they are inactive by default. This is controlled by the Maven profile ``expensiveTestSuppression`` and can be disabled using the ``-P`` flag in the Maven command. A build including these "expensive" unit tests can be run by executing ``mvn clean install -P !expensiveTestSuppresion``. If combined with other profiles, e.g. to enable Docker-based integration tests, the command would be ``mvn clean install -P run-integration-tests,!expensiveTestSuppression -Ddocker.tests.enabled=true``.
