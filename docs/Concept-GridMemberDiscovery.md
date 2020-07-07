# Concept: Grid Member Discovery

This documentation section provides high-level concepts / information on how Alfresco Repository and Share instances discovery and connect to other instances to form a fully functioning data grid which supports messaging and distributed caching functionality, as far as it is relevant to the individual tier.

## Alfresco Repository

The aldica module supports auto-discovery and auto-join capabilities for servers on the Repository-tier to automatically form / join into an Apache Ignite data grid. This support is based on the fact that all instances of Alfresco Repository need to be connected to the same central database, and can share identity / location information via that database without having to know each other's addresses beforehand. During startup of the local Ignite grid instance, each Repository instance performs the following steps to either join an existing grid or set itself up as the initial member of a new grid:

1. Parse and set up list of addresses for “initial members” if configured
2. Calculate address translation map for "self" addresses based on configuration of publicly accessible host name / ports, and set up server metadata
3. Determine and register list of “self” addresses in Ignite in-memory state, using address translation if relevant
4. Load all addresses of currently active grid members from the shared database, and register in Ignite in-memory state
5. Sequentially attempt to connect to an initial node using the list of configured "initial members" and non-"self" addresses retrieved from the shared database
6. Update address translation map from metadata of other servers in the data grid
7. Update central registration in the shared database to include all addresses of currently active grid members retrieved from respective server medatata

Once active, each Repository instance runs a scheduled job (configured via `${moduleId}.core.local.disco.registrationRefresh.cron`) to regularly update the central registration with all addresses of currently active grid members, remove registration details for failed node that have not reconnected, and add details for any grid member that has joined without performing self-registration or having a Repository instance perform registration on its behalf. 

## Repository Companion Application

The Repository companion application lacks access to the central Alfresco database used on the Repository-tier and can thus neither actively register its own address details for other companion applications or Repository instances to look up, nor look up details of those other instances for the initial attempt to join an existing data grid. Instead, it requires that the `aldica.core.initialMembers` configuration property is set to at least one Repository or companion application instance which is guaranteed to be active and can let the new companion application instance join an existing grid, and in doing so, exchange the necessary details about all other grid members. Upon joining a data grid, any active Repository instance on that grid will register the address details of the new companion application instance on its behalf. Use of the Alfresco job locking mechanism will ensure that only one Repository instance will register the details.

In short, during startup of the local Ignite grid instance, each companion application instance performs the following steps to join an existing grid:

1. Parse and set up list of addresses for “initial members” (**must be configured**, otherwise instance will be a no-op instance)
2. Calculate address translation map for "self" addresses based on configuration of publicly accessible host name / ports, and set up server metadata
3. Determine and register list of “self” addresses in Ignite in-memory state, using address translation if relevant
4. Sequentially attempt to connect to an initial node using the list of configured "initial members"
5. or 6. Update address translation map from metadata of other servers in the data grid
5. or 6. (Repository on behalf of companion) Update central registration in the shared database to include address of newly joined companion application instance

Steps 5 and 6 may occur in any order or even at the same time since these would be executed by different grid members.

If multiple Repository companion applications form a data grid without an active Repository instance in it, the update of the central registration in the shared database will be performed as soon as the first Repository instance joins, being handled by step no. 7 in the Alfresco Repository startup discovery handling.

## Alfresco Share

Alfresco Share lacks a central database that every instance can use without relying on the Repository (which may be unavailable during startup of Share). As a result, there is no auto-discovery support for connecting the various instances of Share into a data grid. It is required that each Share instance is configured with a set of logical network addresses to find at least a common sub-set of “initial members” for the grid. Once such initial members are found during startup, other instances can join the grid without having to know all the members beforehand. During startup of the local Ignite grid instance, each Share instance performs the following steps to either join an existing grid or set itself up as the initial member of a new grid:

1. Parse and set up list of addresses for “initial members” if configured
2. Calculate address translation map for "self" addresses based on configuration of publicly accessible host name / ports
3. Determine and register list of “self” addresses in Ignite in-memory state, using address translation if relevant
4. Sequentially attempt to connect to an initial node using the list of configured "initial members"
5. Update address translation map from metadata of other servers in the data grid

## Address Translation

The “self” addresses of a grid member are by default determined using the local host name lookup mechanisms of the Java virtual machine. In deployment scenarios which use network address translation or network virtualisation, such as any Docker-based deployments with non-host networks, the automatically determined address of the server may not be publicly accessible to other servers, and a grid may not be formed without additional configuration. For this scenario, the aldica module provides address mapping and resolution functionalities that only require minimal configuration to set up.

Using global configuration it is possible to specify the

- host name
- grid discovery TCP port
- grid communication TCP port
- time server TCP port

that should be used when other servers need to connect to the local instance. With this data, the server constructs an in-memory translation map for itself as part of startup step 2, registers only publicly accessible address information in step 3, and sends the translation map as part of its own metadata in the connect / join attempts of steps 7 (Repository) and 4 (Share) respectively. Other servers use this metadata to update their own address translation maps for other servers whenever new servers join the data grid after their initial start or leave it.