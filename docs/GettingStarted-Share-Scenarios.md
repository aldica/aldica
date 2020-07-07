# Getting Started: Alfresco Share Configuration Scenarios

The configuration for Alfresco Share uses the non-standard configuration file share-global.properties provided by the Acosix Utility module. This configuration file can be placed in the *tomcat/shared/classes* directory just like the alfresco-global.properties file for Alfresco Content Services. 

## Trivial: running on a single node

In the simplest deployment scenario, this module is only installed on a single Alfresco Share instance. In this configuration, the module does not require any mandatory configuration and is ready to run out-of-the-box. The following optional, high-level configuration parameters in share-global.properties may still be of interest:

- `aldica.core.enabled`: true/false configuration to globally control the activation of this module's features - defaults to true
- `aldica.core.storage.defaultStorageRegion.initialSize`: the amount of off-heap memory to pre-allocate for storage (caches) - defaults to 32 MiB
- `aldica.core.storage.defaultStorageRegion.maxSize`: the limit to the amount of off-heap memory allowed to be used for storage (caches) - defaults to 128 MiB

**Note**: The Share extension of this project does not provide any alternative caching implementation for Alfresco Share / Alfresco Surf. In the trivial deployment scenario, there will be no functional difference from default Alfresco Share, apart from the fact that an internal data grid is started.

## Simple: grid with explicitly configured initial members

In contrast to Alfresco Content Services, the extension to Alfresco Share of this project cannot provide automatic registration and discovery capabilities since multiple Alfresco Share instances cannot rely on any shared infrastructure for state management, except for Alfresco Content Services, which may not always be available or consistent. As such, running multiple Alfresco Share instances in a grid requires the explicit configuration of "initial members" which should be contacted to join a potentially already existing grid. This can be accomplished by using the parameter `aldica.core.initialMembers` in share-global.properties (an identical option exists also for Alfresco Content Services, but is rarely relevant and acts solely as a backup). This parameter supports a comma-separated list of addresses, where each individual address can be:

- a simple IP address or host name
- an IP address or host name, and port for Apache Ignite discovery handling
- an IP address or host name, and port range for Apache Ignite discovery handling, e.g. 5.5.5.5:47110..47115

If no port / port range is provided in an individual value of the initial members list, then the configured TCP port for Apache Ignite handling discovery of this Alfresco Share instance (`aldica.core.local.disco.port`) is also assumed to be used for the other instance.

THe following configuration parameters in share-global.properties concern other key options, which should be considered: 

- `aldica.core.enabled`: true/false configuration (default: true) to globally control the activation of this module's features - defaults to true
- `aldica.core.name`: the name of the data grid to establish / connect to, which can be used to ensure instances only join the "right" grid - defaults to 'shareGrid'
- `aldica.core.login`: the login name required / used to connect to the data grid, which can be used to ensure instances only join the "right" grid - defaults to 'share'
- `aldica.core.password`: the login password required / used to connect to the data grid, which can be used to ensure instances only join the "right" grid - defaults to 'shareGrid-dev'
- `aldica.core.local.id`: a stable / consistent ID for the specific Alfresco Share instance, which can help various grid / node failure resolutions on the communication layer - has no fixed default value and will dynamically construct an ID based on network addresses and the bound port, unless an explicit ID is set

## Advanced: running with network address translation (NAT) / in a containerised environment

The relevant conditions and configuration options for this deployment scenario are nearly identical to the same scenario for Alfresco Content Services. Please refer to that section above for details.
The following configuration parameters have different default values in Alfresco Share:

- `aldica.core.local.comm.port`: 47130 (vs. 47100 in Alfresco Content Services)
- `aldica.core.local.disco.port`: 47140 (vs. 47110 in Alfresco Content Services)
- `aldica.core.local.time.port`: 47150 (vs. 47120 in Alfresco Content Services)