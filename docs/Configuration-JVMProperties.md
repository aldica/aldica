# Configuration: JVM System Properties

Depending on the Java version used, the aldica module requires that specific properties are set as parameters to the Java Virtual Machine, while other properties are recommended for either best performance or general avoidance of log output noise. These properties can typically be set in the Tomcat startup scripts using the JAVA\_OPTS environment variable, or provided via the same environment variable in Docker configurations, either via Docker Compose or Kubernetes configuration files. The default Docker images for Alfresco Share use the CATALINA\_OPTS environment variable to pass along configuration, but the specific required and recommended settings are the same for Repository and for Share. The Repository companion app requires the parameters to be provided with the command line invocation of the runnable JAR file.

The following parameters are required to be set when Alfresco and the aldica module are run on Java 11 or higher:

```
--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
--add-exports=java.base/sun.nio.ch=ALL-UNNAMED
--add-exports=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED
--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED
--add-exports=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED
--illegal-access=permit
```

This configures the module system of Java to allow access to some of the internal packages to any unnamed module. Alfresco runs within a Tomcat web application and is itself not modularized using the new Java module system. Exposing the internal packages to unnamed modules is the only way to grant the Apache Ignite library access to those functionalities.

In order to achieve best performance of the Apache Ignite components and to avoid communication delays between the servers of the data grid, it is generally recommended to choose a Java garbage collection (GC) mechanism that ensures the “stop the world” pauses are consistently short. At the time of writing, the Garbage First (G1) mechanism should be considered the best fit for that task. Apart from setting the minimum / maximum size of the heap correctly (using `-Xmx` and `-Xms`, it is recommended to set the following parameters to activate G1 and additional GC optimisations:

```
-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:+UseStringDeduplication -XX:+ScavengeBeforeFullGC -XX:+DisableExplicitGC -XX:+AlwaysPreTouch
```

For precise details on the individual parameters it is recommended to check the documentation of the specific JVM used. These GC parameters are applicable to all Java versions from Java 8 onwards, though individual parameters may have become the default in later Java releases.

In order to ensure consistent IO handling across all members of an Ignite data grid, it is recommended to explcitly specify the file encoding and IP version to use:

```
-Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true
```

While both aldica and Ignite support IPv6, Ignite suggests restricting Java to IPv4 unless IPv6 is required to avoid spurious cases of grid nodes becoming detached from the grid.

By default, the Apache Ignite library prints quite verbose log output to the system output stream and regular logs during startup. Some additional verbose log output may also be printed at regular intervals during the runtime of the Alfresco application. The following properties deactivate the most verbose and inconsequential of log output produced by the Apache Ignite library:

```
-DIGNITE_PERFORMANCE_SUGGESTIONS_DISABLED=true -DIGNITE_QUIET=true -DIGNITE_NO_ASCII=true -DIGNITE_UPDATE_NOTIFIER=false -DIGNITE_JVM_PAUSE_DETECTOR_DISABLED=true
```

Due to a [known issue](https://github.com/aldica/aldica/issues/39) in upstream Ignite, the Repository-tier module and companion application require that the configuration consistency check be disabled until the underlying issue has been resolved:

```
-DIGNITE_SKIP_CONFIGURATION_CONSISTENCY_CHECK=true
```