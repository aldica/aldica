# Getting Started: Alfresco Content Services Configuration Scenarios

## Trivial: running on a single node

In the simplest deployment scenario, this module is only installed on a single Alfresco Content Services instance. In this configuration, the module does not require any mandatory configuration and is ready to run out-of-the-box. The following optional, high-level configuration parameters in alfresco-global.properties may still be of interest:

- `aldica.core.enabled`: true/false configuration to globally control the activation of this module's features - defaults to true
- `aldica.caches.enabled`: true/false configuration to globally control the activation of the cache layer feature of this module - defaults to true
- `aldica.core.storage.defaultStorageRegion.initialSize`: the amount of off-heap memory to pre-allocate for storage (caches) - defaults to 1 GiB
- `aldica.core.storage.defaultStorageRegion.maxSize`: the limit to the amount of off-heap memory allowed to be used for storage (caches) - defaults to 16 GiB

**Note**: This module uses memory-mapped files to access off-heap memory for caching (path defined by parameter `aldica.core.storage.defaultStorageRegion.swapPath` - defaults to the Tomcat temporary files folder). This allows the storage to greatly exceed the total amount of physical memory available in the environment. This is why the default limit is set to 16 GiB, even though many small systems may not have that much memory available. By using memory-mapped files for off-heap memory / storage, swap handling is delegated to the operating system. For optimal performance, it is recommended to either have sufficient physical memory available or ensure an SSD devices is used for the swap path.

## Simple: horizontal scaling with automatic registration and discovery

In a simple deployment scenario of multiple Alfresco Content Services instances or when combined with the Repository companion application, little to no configuration is required. This module's extension for Alfresco Content Services includes features for automatic self-registration of instances via the Alfresco AttributeService (stored in the database) and automatic discovery of other, existing / running instances using the same registration data. Provided that there is no network address translation (NAT) or firewall affecting the network connections between the various Alfresco Content Services instances, and all instances access the same, central database instance, these two capabilities enable the module to work with only a minimal amount of mandatory configuration.

The following configuration parameters in alfresco-global.properties must be altered to ensure distributed caching is enabled:

- `aldica.caches.remoteSupport.enabled`: true/false configuration to control the activation of distributed caches - defaults to false

If the Repository companion application is used, it only needs to be configured to reference the repsoitory as an initial member of the data grid in its custom-config.properties file. This can be done using the `aldica.core.initialMembers` parameter, which supports a comma-separated list of addresses, where each individual address can be:

- a simple IP address or host name
- an IP address or host name, and port for Apache Ignite discovery handling
- an IP address or host name, and port range for Apache Ignite discovery handling, e.g. 5.5.5.5:47110..47115

THe following configuration parameters in alfresco-global.properties concern other key options, which should be considered: 

- `aldica.core.enabled`: true/false configuration (default: true) to globally control the activation of this module's features - defaults to true
- `aldica.caches.enabled`: true/false configuration (default: true) to globally control the activation of the cache layer feature of this module - defaults to true
- `aldica.core.name`: the name of the data grid to establish / connect to, which can be used to ensure instances only join the "right" grid - defaults to 'repositoryGrid'
- `aldica.core.login`: the login name required / used to connect to the data grid, which can be used to ensure instances only join the "right" grid - defaults to 'repository'
- `aldica.core.password`: the login password required / used to connect to the data grid, which can be used to ensure instances only join the "right" grid - defaults to 'repositoryGrid-dev'
- `aldica.core.local.id`: a stable / consistent ID for the specific Alfresco Content Services instance, which can help various grid / node failure resolutions on the communication layer - has no fixed default value and will dynamically construct an ID based on network addresses and the bound port, unless an explicit ID is set

## Advanced: running with network address translation (NAT) / in a containerised environment

In any deployment where the network communication between instances of Alfresco Content Services is affected by network address translation, additional configuration properties need to be set (in contrast to a simple automatic registration and discovery scenario) in order to ensure correct communication within the data grid used for distributed caching. Any of the following conditions qualify a deployment for this scenario:

- TCP ports on which Apache Ignite binds are rewritten, e.g. through iptables or Docker port mapping
- (name / address) address as determined by the JVM running Alfresco Content Services cannot be used by other instances to establish a network connection, e.g. when instances are located in different physical / virtual networks
- multiple Alfresco Content Service instances run in the same environment and need to avoid address conflicts

The following configuration parameters in alfresco-global.properties can be set to deal with network address translation scenarios:

- `aldica.core.local.host`: address (IP or host name) on which to bind, e.g. to select a specific network interface - empty (default) to bind to all addresses
- `aldica.core.public.host`: publicly accessible address (IP or host name) to use in exchange with other data grid nodes / automatic registration and discovery - empty (default) if no address translation should be performed
- `aldica.core.local.comm.port`: local TCP port on which to bind the Apache Ignite data grid discovery handling - defaults to 47100
- `aldica.core.local.comm.portRange`: number of alternate TCP ports starting from the `aldica.core.local.comm.port` to try binding to in case the configured port is already in use - defaults to 0 (only configured port is allowed and conflict means instance will fail to start)
- `aldica.core.local.comm.filterReachableAddresses`: true/false configuration (default: true) to determine if data grid node addresses provided during discovery / join events should be checked and filtered for being "accessible" before any attempt to open a connection is made - though checking / filtering addresses introduces an overhead, this typically is more efficient than just attempting a connection and waiting for a socket timeout / connection refused, especially since the checks are performed in parallel while connection attempts cycle through known addresses sequentially
- `aldica.core.public.comm.port`: publicly accessible TCP port for the Apache Ignite data grid core communication handling - empty (default) if no port remapping applies to the Alfresco Content Services instances
- `aldica.core.local.disco.port`: local TCP port on which to bind the Apache Ignite data grid discovery handling - defaults to 47110
- `aldica.core.local.disco.portRange`: number of alternate TCP ports starting from the `aldica.core.local.disco.port` to try binding to in case the configured port is already in use - defaults to 0 (only configured port is allowed and conflict means instance will fail to start)
- `aldica.core.public.disco.port`: publicly accessible TCP port for the Apache Ignite data grid discovery handling - empty (default) if no port remapping applies to the Alfresco Content Services instances 
- `aldica.core.local.time.port`: local TCP port on which to bind the Apache Ignite data grid time server handling - defaults to 47120
- `aldica.core.local.time.portRange`: number of alternate TCP ports starting from the `aldica.core.local.time.port` to try binding to in case the configured port is already in use - defaults to 0 (only configured port is allowed and conflict means instance will fail to start)
- `aldica.core.public.time.port`: publicly accessible TCP port for the Apache Ignite data grid time server handling - empty (default) if no port remapping applies to the Alfresco Content Services instances