# Getting started using Tomcat

In the following it is described how to setup an Alfresco Community cluster 
using Tomcat containers. The information provided here can be considered only as 
a "getting started guide" and should not be considered as a complete 
reference describing all the steps required for setting up a cluster solution ready for production use.

_Note: this also means that appropriate security precautions need to be taken before 
trying this in production._ For production use, it is probably more convenient to use 
the Docker setup found [elsewhere](link to docker docs) in the aldica documentation.


The Alfresco Community cluster setup will consist of the following components:

* A simple Apache load balancer
* A PostgreSQL database
* An NFS-server
* Two (or more) Alfreco Community 6.1.2 repositories.


## Prerequisites

This guide assumes that the servers in the cluster solution are running 
Ubuntu 18.04 (or similar), but other Linux distributions can of course be used  as well 
with appropriate changes to the distribution specific commands outlined below. 
Make sure that the servers used have a sufficient amount of resources in 
terms of CPUs, memory, harddisk space/speed and bandwidth.


## Load balancing

A simple load balancer can be setup using an 
[Apache HTTP Server](https://httpd.apache.org/). The load balancer can be 
installed and configured as described below.

### Installing Apache

Install the Apache HTTP Server:
```
$ sudo apt install apache2
```

Enable these Apache modules:
```
$ sudo a2enmod headers proxy proxy_http proxy_balancer lbmethod_byrequests slotmem_shm
```

### Sticky sessions

Create the file 
`/etc/apache2/sites-available/lb_sticky.conf` with the content shown below in order to set up a load balancer using sticky sessions. In this case all traffic from a given 
client is passed on the same Alfresco repository each time a request hits the load balancer.

```
<VirtualHost *:80>

    ServerName <load balancer hostname>

    ProxyRequests off

    Header add Set-Cookie "ROUTEID=.%{BALANCER_WORKER_ROUTE}e; path=/" env=BALANCER_ROUTE_CHANGED
    <Proxy "balancer://mycluster">
        BalancerMember "http://<alfresco repo 1 hostname>:8080" route=1
        BalancerMember "http://<alfresco repo 2 hostname>:8080" route=2
        
        ProxySet stickysession=ROUTEID
    </Proxy>

    ProxyPass        "/" "balancer://mycluster/"
    ProxyPassReverse "/" "balancer://mycluster/"

</VirtualHost>
```

TODO: Test the above configuration (the `Header` line is maybe not needed)

### Round-robin

Another option is to use a load balancer using a round-robin method to balance 
the incoming traffic, i.e. using the example given here, the first incoming request will 
be proxied on to the first Alfresco repository in the cluster, and the next 
request will be passed on to the second repository and so on.

Create the file `/etc/apache2/sites-available/lb_byrequests.conf` containing these lines in order to set up a load balancer using the round-robin method:

```
<VirtualHost *:80>

    ServerName <load balancer hostname>

    ProxyRequests off

    <Proxy "balancer://mycluster">
        BalancerMember "http://<alfresco repo 1 hostname>:8080"
        BalancerMember "http://<alfresco repo 2 hostname>:8080"
        
        ProxySet lbmethod=byrequests
    </Proxy>

    ProxyPass        "/" "balancer://mycluster/"
    ProxyPassReverse "/" "balancer://mycluster/"

</VirtualHost>
```

Make sure that the hostnames of the load balancer and the Alfresco 
repositories are 
substituted into `<load balancer hostname>` and `<alfresco repo n hostname>`, 
respectively. Please see the 
[official Apache documentation](https://httpd.apache.org/docs/2.4/mod/mod_proxy_balancer.html) to get more info on how to configure load 
balancers with Apache.

### Enabling the load balancer

Apache is shipped with a pre-installed site configured in the file 
`/etc/apache2/sites-available/000-default.conf`. This site can be disabled 
with:
```
$ sudo a2dissite 000-default
```
The load balancer can be enabled with on of the commands

```
$ sudo a2ensite lb_sticky
```
or
```
$ sudo a2ensite lb_byrequests
```
All the configurations discussed above will take effect once the Apache service 
has been restarted:
```
$ sudo systemctl restart apache2
```

Do not forget to configure the firewall as needed.


## Database setup

A PostgreSQL database will be used in this cluster example, but it is of 
course also possible to use another relational database supported by 
Alfresco Community. The PostgreSQL server can be installed with:
```
$ sudo apt install postgresql
```

Log in to the database and create the Alfresco database and the Alfresco 
database user:
```
$ sudo -u postgres psql
```
Type the following commands in the PostgreSQL terminal:
```
postgres=# CREATE DATABASE alfresco;
postgres=# CREATE USER alfresco WITH ENCRYPTED PASSWORD 'topsecret';
postgres=# ALTER DATABASE alfresco OWNER TO alfresco;
postgres=# \q
```

In `/etc/postgresql/10/main/pg_hba.conf`, change the line
```
local   all   all   peer
```
to
```
local   all   all   md5
```
and add the lines
```
host   alfresco   alfresco <alfresco repo 1 IP>/32   md5
host   alfresco   alfresco <alfresco repo 2 IP>/32   md5
```
This will 
allow the alfresco user to login to the alfresco database from 
the database server itself and from the Alfresco repositories. The final 
configuration that needs to be done is changing the `listen_address` in 
`/etc/postgresql/10/main/postgresql.conf`. Set this to e.g.
```
listen_address = '*'
```
or restrict the list of listening addresses as needed. Restart the 
database service in order for the changes to take effect:
```
$ sudo systemctl restart postgresql
```

It should now be possible to log in to the database from one of the 
Alfresco repository servers with:
```
$ psql -h <database hostname> -U alfresco -W alfresco
```

If the PostgreSQL client is not already installed, it can be installed with:
```
$ sudo apt install postgresql-client-common postgresql-client-10
```

Please see the 
[official PostgreSQL documentation](https://www.postgresql.org/) for further 
details.

## NFS

The two Alfresco repositories will need a common filesystem to store the 
Alfresco content. A simple solution is to install an NFS-server. NFS is simple 
to set up, but it may not be sufficient for large clusters. In this case 
alternative solutions like e.g. [Gluster](https://www.gluster.org/) can be 
used.

### Setting up the NFS server

The NFS-server can be installed with:
```
$ sudo apt install nfs-kernel-server
```

Next, a folder for storing the Alfresco content must be provided. In this 
example, the folder `/var/nfs/alf_data` will be used:
```
$ sudo mkdir -p /var/nfs/alf_data
```

Set the following permissions on the folder:
```
$ sudo chown nobody:nogroup /var/nfs/alf_data
$ sudo chmod 755 /var/nfs/alf_data
```

The folder above now needs to be exported, so the repository servers can 
mount it. This is done in `/etc/exports`. Add the following lines to this 
file:
```
/var/nfs/alf_data <alfresco repo1 hostname>(rw,sync,no_subtree_check)
/var/nfs/alf_data <alfresco repo2 hostname>(rw,sync,no_subtree_check)
```

Finally, restart the NFS server:
```
$ sudo systemctl restart nfs-kernel-server
```

### Setting up NFS on the clients

In order for the repositories to use the NFS server, the NFS client 
software needs to be installed and the remote filesystem has to be 
mounted. Install the following package in both of the repository 
servers:
```
$ sudo apt install nfs-common
```

Create a folder which can serve as the target for mounting the remote 
folder and set the permissions on this folder (you will probably 
need to adjust these according to your security requirements):
```
$ sudo mkdir -p /mnt/nfs/alf_data
$ sudo chmod 777 /mnt/nfs/alf_data
```

Then mount the remote filesystem. This can be done either by mounting 
from the command line or by adding a mount point to the `/etc/fstab`.

#### Mounting from the command line

Use the following command to mount the remote folder locally:
```
$ sudo mount -t nfs <NFS server hostname>:/var/nfs/alf_data /mnt/nfs/alf_data
```

Perform the above mount from both of the repository servers. 

#### Mounting via the filesystem table

Another option is to add the mount point to the `/etc/fstab`. Do this 
by adding this line to the file on both repositories
```
<NFS server IP>:/var/nfs/alf_data /mnt/nfs/alf_data nfs defaults 0 2
```

The remote filesystem can then be mounted with
```
$ sudo mount -a
```

### Testing NFS

Now is a good 
time to test that the filesystem has been mounted correctly, e.g. 
try adding a file to `/mnt/nfs/alf_data` from repository 1 and check if 
the file can be deleted again in the same folder from repository 2.


## Java

Before proceeding, make sure that Java is installed on the servers:
```
$ sudo apt install openjdk-8-jre
```

_Note: If Java 11 is used, a number of extra flags are needed when 
configuring the JVM. Please see [this](Configuration-JVMProperties.md) 
for more details._


## Tomcat

The final step missing in the cluster setup is the installation of the 
repository servers with the aldica module installed and configured. In 
this guide we will set up Tomcat containers to serve the Alfresco 
application along with the aldica module.

### Installing Tomcat

First, add a tomcat user and 
group on both of the repository servers:

```
$ sudo adduser --system --disabled-login --disabled-password --group tomcat
```

Next, download Tomcat from the [Apache Tomcat website](https://tomcat.apache.org/) 
and extract into the `/opt` folder. In this guide, Tomcat version 8.5.34 is used:
```
$ cd /opt
$ sudo wget http://mirrors.dotsrc.org/apache/tomcat/tomcat-8/v8.5.46/bin/apache-tomcat-8.5.46.tar.gz
$ sudo tar xvzf /path/to/apache-tomcat-8.5.34.tar.gz
```

This will result in the folder `/opt/apache-tomcat-8.5.34` containing the Tomcat 
container required to serve Alfresco. For convenience, create a symlink to the tomcat 
folder:
```
$ sudo ln -s /opt/apache-tomcat-8.5.34 tomcat
```

A couple of permissions need to be set on the Tomcat folders:
```
$ sudo chgrp -R tomcat /opt/apache-tomcat-8.5.34
$ sudo chmod -R g=r /opt/apache-tomcat-8.5.34/conf
$ sudo chmod g=rx /opt/apache-tomcat-8.5.34/conf
$ sudo chown -R tomcat /opt/apache-tomcat-8.5.34/webapps
$ sudo chown -R tomcat /opt/apache-tomcat-8.5.34/work
$ sudo chown -R tomcat /opt/apache-tomcat-8.5.34/temp
$ sudo chown -R tomcat /opt/apache-tomcat-8.5.34/logs
```

Finally, a configuration file for systemd should be added for easing the starting and 
stopping of the Tomcat service. Add the file `/etc/systemd/system/tomcat.service` with 
the content below to the repository servers:

```
# Systemd unit file for tomcat

[Unit]
Description=Apache Tomcat Web Application Container
After=syslog.target network.target

[Service]
Type=forking

Environment=CATALINA_PID=/opt/tomcat/temp/tomcat.pid
Environment=CATALINA_HOME=/opt/tomcat
Environment=CATALINA_BASE=/opt/tomcat
Environment='CATALINA_OPTS=-Xms512M -Xmx6G -server'
Environment='JAVA_OPTS=-Djava.awt.headless=true -Djava.security.egd=file:/dev/./urandom \
-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:+UseStringDeduplication \
-XX:+ScavengeBeforeFullGC -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
-DIGNITE_PERFORMANCE_SUGGESTIONS_DISABLED=true -DIGNITE_QUIET=true \
-DIGNITE_NO_ASCII=true -DIGNITE_UPDATE_NOTIFIER=false \
-DIGNITE_JVM_PAUSE_DETECTOR_DISABLED=true'

ExecStart=/opt/tomcat/bin/startup.sh
ExecStop=/bin/kill -15 $MAINPID

User=tomcat
Group=tomcat
UMask=0007
RestartSec=10
Restart=always

[Install]
WantedBy=multi-user.target
```

The `JAVA_OPTS` line contains a number of extra options which are needed by aldica.

_Note: If Java 11 is used, a number of extra flags are needed when 
configuring the JVM. Please see [this](Configuration-JVMProperties.md) 
for more details._

Make sure to change the environment variables (e.g. the `-Xmx` setting) in the above 
file as needed. Reload the systemd configuration with:
```
$ sudo systemctl daemon-reload
```

It should now be possible to start and stop (and enable) the Tomcat service with the commands:
```
$ sudo systemctl enable tomcat
$ sudo systemctl start tomcat
$ sudo systemctl stop tomcat
```

Start Tomcat and check that it is running on e.g. `http://<alfresco repo1 hostname>:8080`.

### Configuring Tomcat to Use Alfresco and aldica

Tomcat must be prepared for the Alfresco and aldica specific files that are required for 
the system to run. First, create the folders `/opt/tomcat/shared/classes` and 
`/opt/tomcat/shared/lib` and set the right permissions and ownerships on these:
```
$ sudo mkdir -p /opt/tomcat/shared/classes /opt/tomcat/shared/lib
$ sudo chown -R tomcat. /opt/tomcat/shared
$ sudo chmod -R 755 /opt/tomcat/shared
```

Set the `shared.loader` property in the file `/opt/tomcat/conf/catalina.properties` to:
```
shared.loader=${catalina.base}/shared/classes
```

A few changes are also required in the `/opt/tomcat/conf/server.xml`. Find the 
`<Connector>` elements with the attributes `port=8080` and `port=8009`, respectively, 
and add the attribute `URIEncoding="UTF-8"` to these two elements. Set the permissions 
of the file to 640:
```
$ sudo chmod 640 /opt/tomcat/conf/server.xml
```

### Downloading and installing the Alfresco files

Download the Alfresco distribution-zip, extract it and set permissions to the 
tomcat user and group:
```
$ cd /opt
$ sudo wget https://download.alfresco.com/cloudfront/release/community/201901-GA-build-205/alfresco-content-services-community-distribution-6.1.2-ga.zip
$ sudo unzip alfresco-content-services-community-distribution-6.1.2-ga.zip
($ sudo chown -R tomcat. alfresco-content-services-community-distribution-6.1.2-ga)
```

For convenience, make a symlink to the newly extracted Alfresco folder:
```
$ sudo ln -s /opt/alfresco-content-services-community-distribution-6.1.2-ga /opt/alfresco
```

Stop the Tomcat service, if it is running. Delete the content of the folder 
`/opt/tomcat/webapps`:
```
$ sudo rm -rf /opt/tomcat/webapps/*
```

Copy the `alfresco.war`, the`ROOT.war` and the `_vti_.war` files 
to `/opt/tomcat/webapps` and set ownership and permissions:
```
$ sudo cp /opt/alfresco/web-server/webapps/alfresco.war /opt/tomcat/webapps
$ sudo cp /opt/alfresco/web-server/ROOT.war /opt/tomcat/webapps
$ sudo cp /opt/alfresco/web-server/_vti_.war /opt/tomcat/webapps

(this should already be set)
$ sudo chown tomcat. /opt/tomcat/webapps/*

$ sudo chmod 644 /opt/tomcat/webapps/*
```

Copy the contents of `/opt/alfresco/web-server/lib` to `/opt/tomcat/lib`:
```
$ sudo cp /opt/alfresco/web-server/lib/* /opt/tomcat/lib
```

Currently, the `lib` folder only contains the JDBC driver for PostgreSQL, so if 
MySQL or MariaDB is used instead, a driver for these also have to be placed in 
the folder.

Copy the `alfresco.xml` to `/opt/tomcat/conf`:
```
$ sudo cp /opt/alfresco/web-server/conf/Catalina/localhost/alfresco.xml /opt/tomcat/conf/alfresco.xml
```

Copy the folder `/opt/alfresco/alf_data/keystore` to `/opt/tomcat`:
```
$ sudo cp -a /opt/alfresco/alf_data/keystore /opt/tomcat
$ sudo chmod 640 /opt/tomcat/keystore/*
$ sudo chmod 750 /opt/tomcat/keystore
```

Finally, create the `alfresco-global.properties` file in 
`/opt/tomcat/shared/classes` with content similar to this (adjust as needed):
```
###############################
## Common Alfresco Properties #
###############################

#
# Custom content and index data location
#
dir.root=/mnt/nfs/alf_data
dir.keystore=/opt/tomcat/keystore

#
# Database connection properties
#
db.username=<db_username>
db.password=<db_password>
db.schema.update=true
db.driver=org.postgresql.Driver
db.url=jdbc:postgresql://<db hostname>:5432/alfresco

#
# URL Generation Parameters (The ${localname} token is replaced by the local server name)
#
alfresco.context=alfresco
alfresco.host=${localname}
alfresco.port=8080
alfresco.protocol=http

# Default value of alfresco.rmi.services.host is 0.0.0.0 which means 'listen on all adapters'.
# This allows connections to JMX both remotely and locally.
#
alfresco.rmi.services.host=0.0.0.0

#
# Smart Folders Config Properties
#
smart.folders.enabled=false
smart.folders.model=alfresco/model/smartfolder-model.xml
smart.folders.model.labels=alfresco/messages/smartfolder-model

#
# Message broker config
#
messaging.subsystem.autoStart=false

#
# Clustering
#
aldica.core.enabled=true
aldica.caches.enabled=true
aldica.caches.remoteSupport.enabled=true
#aldica.core.name=
#aldica.core.login=
#aldica.core.password=
aldica.core.local.id=<alfresco repo hostname>
aldica.core.local.host=<alfresco repo hostname>
aldica.core.public.host=<alfresco repo ip>
```

Set the ownership and permissions of the `alfresco-global.properties`:
```
$ sudo chown tomcat. /opt/tomcat/shared/classes/alfresco-global.properties
$ sudo chmod 640 /opt/tomcat/shared/classes/alfresco-global.properties
```

### Installing aldica

The final step in setting up the cluster concerns the installation of the aldica 
AMP along with the supporting Acosix Alfresco Utility AMP.

#### Building the Acosix Alfresco Utility AMP

The GitHub page for the Acosix Alfresco Utility project can be found 
[here](https://github.com/Acosix/alfresco-utility).  In order to 
build the AMP, a toolchain configuration needs to 
be provided. The instructions for setting this up can be found in the 
[build](https://github.com/Acosix/alfresco-utility#build) section of the project 
documentation. Once the toolchain has been setup, the AMP(s) can be build with
```
$ mvn clean package
```

run from the root of the project. If the build was successful a message like
```
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for Acosix Alfresco Utility - Parent 1.0.7.0:
[INFO]
[INFO] Acosix Alfresco Utility - Parent ................... SUCCESS [  0.865 s]
[INFO] Acosix Alfresco Utility - Core Parent .............. SUCCESS [  0.008 s]
[INFO] Acosix Alfresco Utility - Core Common Library ...... SUCCESS [  6.986 s]
[INFO] Acosix Alfresco Utility - Core Repository Quartz 1.x Library SUCCESS [  4.541 s]
[INFO] Acosix Alfresco Utility - Core Repository Quartz 2.x Library SUCCESS [  1.256 s]
[INFO] Acosix Alfresco Utility - Core Repository Module ... SUCCESS [ 43.227 s]
[INFO] Acosix Alfresco Utility - Core Share Module ........ SUCCESS [ 14.789 s]
[INFO] Acosix Alfresco Utility - Full Parent .............. SUCCESS [  0.004 s]
[INFO] Acosix Alfresco Utility - Full Repository Module ... SUCCESS [  6.080 s]
[INFO] Acosix Alfresco Utility - Full Share Module ........ SUCCESS [  4.459 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:22 min
[INFO] Finished at: 2019-10-03T09:34:38+02:00
[INFO] ------------------------------------------------------------------------
```

should appear in the terminal. The AMP required by aldica is the file found 
here `repository/target/de.acosix.alfresco.utility.repo-1.0.3.0-SNAPSHOT.amp`.

#### Building the aldica AMP

As for the Acosix Alfresco Utility AMP, a toolchain configuration is required 
(see the section above or the documentation found 
[elsewhere](../README.md) in this project. Once this has been setup, the AMP(s) 
can be build with
```
$ mvn clean package -DskipTests
```

and the resulting aldica AMP can be found here 
`repository/target/aldica-repo-ignite-1.0.0.0-SNAPSHOT.amp`.

#### Deploying the AMPs

The AMPs above must be added to the `alfresco.war` file found in 
`/opt/tomcat/webapps`. For convenience, copy the AMPs to a folder 
within the `tomcat` folder:
```
$ sudo -u tomcat mkdir /opt/tomcat/amps
$ sudo cp /path/to/alfresco-utility/repository/target/de.acosix.alfresco.utility.repo-1.0.3.0-SNAPSHOT.amp /opt/tomcat/amps
$ sudo cp /path/to/aldica/repository/target/aldica-repo-ignite-1.0.0.0-SNAPSHOT.amp /opt/tomcat/amps
$ sudo chown tomcat. /opt/tomcat/amps/*
```

The AMPs can now be installed into the `alfresco.war` file:
```
$ sudo -u tomcat java -jar /opt/alfresco/bin/alfresco-mmt.jar install /opt/tomcat/amps/de.acosix.alfresco.utility.repo-1.0.3.0-SNAPSHOT.amp /opt/tomcat/webapps/alfresco.war
$ sudo -u tomcat java -jar /opt/alfresco/bin/alfresco-mmt.jar install /opt/tomcat/amps/aldica-repo-ignite-1.0.0.0-SNAPSHOT.amp /opt/tomcat/webapps/alfresco.war

```

## Start Tomcat and testing

That's it! The cluster should be ready to start up. Run the following command on each 
of the repository servers one at a time:
```
$ sudo systemctl start tomcat
```

Follow along in the log file `/opt/tomcat/logs/catalina.out` while Tomcat is starting up 
and verify that log entities like these appear:
```
[14:44:04] Ignite node started OK (id=f4bcee41, instance name=repositoryGrid)
[14:44:04] Topology snapshot [ver=1, locNode=f4bcee41, servers=1, clients=0, state=ACTIVE, CPUs=8, offheap=16.0GB, heap=4.0GB]
2019-08-21 14:44:04,742  INFO  [managers.discovery.GridDiscoveryManager] [localhost-startStop-1] Topology snapshot [ver=1, locNode=f4bcee41, servers=1, clients=0, state=ACTIVE, CPUs=8, offheap=16.0GB, heap=4.0GB]
 2019-08-21 14:44:04,742  INFO  [ignite.lifecycle.SpringIgniteLifecycleBean] [localhost-startStop-1] Ignite instance repositoryGrid currently has 1 active nodes on addresses [172.30.0.54]
 201
```

When both repositories have been started up, it can be verified that the clustering 
mechanism is working as described [here](Test-Manual.md).
