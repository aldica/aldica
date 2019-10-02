# Getting started using Tomcat

In the following it is described how to setup an Alfresco Community cluster 
using Tomcat containers. The infomation provided here can be considered only as 
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
try adding a file to `/mnt/nfs/ald_data` from repository 1 and check if 
the file can be deleted again in the same folder from repository 2.


## Tomcat

The final step missing in the cluster setup is the installation of the 
repository servers with the aldica module installed and configured. In 
this guide we will set up Tomcat containers to serve the Alfresco 
application along with the aldica module. First, add a tomcat user and 
group on both of the repository servers:

```
$ sudo adduser --system --disabled-login --disabled-password --group tomcat
```

Then, make sure that Java is installed on the servers:
```
$ sudo apt install openjdk-8-jre
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
Environment='JAVA_OPTS=-Djava.awt.headless=true -Djava.security.egd=file:/dev/./urandom'

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

Make sure to change the environment variables in the above file as needed. Reload the systemd 
configuration with:
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