# Concept: Grid Member Discovery
This documentation section provides high-level concepts / information on how Alfresco Repository and Share instances discovery and connect to other instances to form a fully functioning data grid which supports messaging and distributed caching functionality, as far as it is relevant to the individual tier.

## Alfresco Repository
The aldica module supports auto-discovery and auto-join capabilities for servers on the Repository-tier to automatically form / join into an Apache Ignite data grid. This support is based on the fact that all instances of Alfresco Repository need to be connected to the same central database, and can share identity / location information via that database without having to know each other's addresses beforehand. During startup of the local Ignite grid instance, each Repository instance performs the following steps to either join an existing grid or set itself up as the initial member of a new grid:

1. Parse and set up list of addresses for “initial members” if configured
2. Calculate address translation map for "self" addresses based on configuration of publicly accessible host name / ports, and set up server metadata
3. Determine and register list of “self” addresses in Ignite in-memory state, using address translation if relevant
4. Load all addresses of currently active grid members from the shared database, and register in Ignite in-memory state
5. Sequentially attempt to connect to an initial node using the list of configured "initial members" and non-"self" addresses retrieved from the shared database
6. or 7. Update address translation map from metadata of other servers in the data grid
6. or 7. Update central registration in the shared database to include all addresses of currently active grid members retrieved from respective server medatata

Steps 6 and 7 are not guaranteed to execute in a specific order and are independent of one another.

## Alfresco Share
Alfresco Share lacks a central database that every instance can use without relying on the Repository (which may be unavailable during startup of Share). As a result, there is no auto-discovery support for connecting the various instances of Share into a data grid. It is required that each Share instance is configured with a set of logical network addresses to find at least a common sub-set of “initial members” for the grid. Once such initial members are found during startup, other instances can join the grid without having to know all the members beforehand. During startup of the local Ignite grid instance, each Share instance performs the following steps to either join an existing grid or set itself up as the initial member of a new grid:

1. Parse and set up list of addresses for “initial members” if configured
2. Calculate address translation map for "self" addresses based on configuration of publicly accessible host name / ports
3. Determine and register list of “self” addresses in Ignite in-memory state, using address translation if relevant
4. Sequentially attempt to connect to an initial node using the list of configured "initial members"
5. Update address translation map from metadata of other servers in the data grid

## Address Translation
The “self” addresses of a server are by default determined using the local host name lookup mechanisms of the Java virtual machine. In deployment scenarios which use network address translation or network virtualisation, such as any Docker-based deployments with non-host networks, the automatically determined address of the server may not be publicly accessible to other servers, and a grid may not be formed without additional configuration. For this scenario, the aldica module provides address mapping and resolution functionalities that only require minimal configuration to set up.

Using global configuration it is possible to specify the

- host name
- grid discovery TCP port
- grid communication TCP port
- time server TCP port

that should be used when other servers need to connect to the local instance. With this data, the server constructs an in-memory translation map for itself as part of startup step 2, registers only publicly accessible address information in step 3, and sends the translation map as part of its own metadata in the connect / join attempts of steps 7 (Repository) and 4 (Share) respectively. Other servers use this metadata to update their own address translation maps for other servers whenever new servers join the data grid after their initial start or leave it.