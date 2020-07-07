# Getting Started: Repository Companion Application

## Use Case

The [Repository-tier aldica companion application](../repository-companion) is a standalone Java application which can be used in conjunction which can be used to extend the distributed Repository-tier Ignite data grid to extend and scale the off-heap cache layer. Any process of this application will spin up a local Ignite instance that connects to existing Repository or other companion grid members, automatically retrieves the low-level Ignite cache configurations and locally re-creates those caches that should be paritioned or replicated across all data grid members. By using the companion application, the caching capacity of Repository-tier servers is no longer limited to the amount of phyiscal memory or disk swap space available to the individual server, and data can be kept alive in the cache layer even when doing full restarts of Repository-tier servers in case of failures or a swap over to a new server instance.

## Usage

The companion application is a standard Java application packaged in an uber JAR containing all required dependencies as well as default configuration. An instance of this application can be started by using the command line

```
java <JAVA_OPTS> -jar aldica-repo-ignite-companion-<version>.jar
```

In order to configure the instance of the companion application, a single configuration file `custom-config.properties` needs to be place in the working directory of the application. The minimal configuration that must be provided within this file is the list of initial data grid members to attempt contacting in order to join a pre-existing data grid.

### JAVA_OPTS

The companion application requires that various JVM parameters be set using the JAVA_OPTS fragment of the command line invocation. The [separate documenation on JVM properties lists all relevant parameters](./Configuration-JVMProperties.md). Though the companion app would be fine to work with a very small heap, Ignite recommends the heap be at least 512 MiB, so it is recommended to use `-Xms512m -Xmx512m` for specifying the heap size - any memory above this amount will not result in any meaningful improvement of performance, and actually take away from the physical memory that could be better utilised handling off-heap caching.

### Configuration Properties

The companion application reuses the [configuration property patterns and semantics defined by the Repository-tier module](./Configuration-RepoReference.md). The following are the most critical individual or groups of configuration properties that need to be considered:

- `aldica.core.initialMembers`: the list of host names (with optional discovery ports) to contact during startup to join a pre-existing data grid
- `aldica.core.name` / `aldica.core.login` / `aldica.core.password`: information used for identification and authentication purposes when joining a pre-existing data grid
- network properties: if the companion app is running in a NATed environment (e.g. in Docker), the relevant host / port mapping properties need to be set to allow the app to provide sufficient address mapping information to other members of the data grid it is joining so that they in turn can properly communicate with the companion
- storage properties: the companion app **MUST** define the same page size and set of storage regions as defined in Repository server instances, although the specific region size and swap path information may differ
- serialisation optimisation properties: the companion app **MUST** use the same high-level optimisation feature enablement flags as set in Repository server instances, though this only extends to the `aldica.core.binary.optimisation.enabled` and `aldica.core.binary.optimisation.XYZ.enabled` flags

### Shutdown / Termination

The companion application can be shut down by sending the proper interrupt to it by either using `Ctrl+C` when run in blocking mode or using a regular (soft) `kill` command against its process ID when run in the background. The application uses Java shutdown hooks to properly disconnect from the distributed grid and shutdown its internal Ignite instance.

## Discovery

Since the companion application does not use an active connection to the Alfresco database, it cannot register itself in the database for other grid members, Repository instances or other companion apps alike, to automatically look up its address details on startup and connect with it. This duty is relegated to one of the Repository instances in the data grid to which the companion app connects to during startup. Using event handling and the address details provided during the join handshake, the Repository instance registers the relevant information in the Alfresco database. Similarily, when a companion app is shut down in an orderly manner, a Repository instance will deregister its details.

More details on grid member discovery can be found in the [separate concept documentation](./Concept-GridMemberDiscovery.md).