# WebDAV load tests

This document describes a WebDAV load test of an Alfresco/aldica cluster 
performed with the 
[JMeter](https://jmeter.apache.org/) performance test tool. The JMeter 
configuration files (and accessory utilities) needed to perform the 
load test are also provided here.

The test at hand is only testing a very specific use case and thus the results 
obtained here cannot be generalized to overall conclusions about the performance 
of the Alfresco/aldica setup.

## The cluster setup

A cluster of Alfresco Community repositories with the nescessary 
additional required infrastructure was established at Amazon Web Services (AWS). 
The following hardware components were used in the load test:

#### Alfresco Community Repositories
* Number of servers: 1 - 8
* CPU cores: 2
* RAM: 8 GB
* Alfresco version: 6.1.2

#### Database
* Number of servers: 1
* CPU cores: 4 - 8
* RAM: 16 - 32 GB
* PostgreSQL (version 10.6)

#### Load Balancer
* Number of servers: 1
* CPU cores: 4 - 8
* RAM: 16 - 32 GB
* Apache HTTP Server (verison 2.4)

#### File Server
* Number of servers: 1
* CPU cores: 4 - 8
* RAM: 16 - 32 GB
* Apache NFS server (version 1.3.4)

#### JMeter Servers
* Number of servers: 1 - 4
* CPU cores: 8
* RAM: 16 GB
* Apache JMeter (version 4.0)

The repository servers were deliberately chosen to have a limited amount 
of resources, since this made it easier to stress the servers and hence see 
the effects of adding more repository nodes to the cluster.

The figure below shows a diagram of the cluster setup along with the 
JMeter servers used to perform the load test:

![Cluster](img/cluster.png)

The load balancer distributed the incoming traffic to the Alfresco repositories, 
and these repositories were connected to a common database and a common file 
server. The load on the system was performed by one or more JMeter instances.
A guide for setting the cluster just described can be found 
[here](../docs/GettingStarted-Tomcat.md).

## The load test

The load test consisted of 4 different user actions performed by a large 
number of users. Each user in the test performed these WebDAV operations 
successively:
* Create a folder.
* Upload a document (approximately 1 MB) to the folder just created.
* Download the document again.
* Delete the folder created above.

A "think time" of somewhere between 1 and 10 seconds were applied between each of 
the user actions. The name of the folder created was chosen as a random 
string to ensure that the folder did not exist in the system beforehand. The 
number of users in the test varied between 100 and 1000 users, and the test was 
running for 400 seconds. The load balancer was configured to use 
"round-robin" balancing.

## Results

There are a lot of parameters that can be adjusted in setup, and many different 
tests were run. Only a subset of the obtained results will be discussed here.

TO BE CONTINUED...
