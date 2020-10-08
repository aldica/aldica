# Introduction

A `docker-compose.yml` file is provided in the `docker` sub-folder of the project root.
This `docker-compose.yml` file is based upon the upstream 
[Alfresco Content Services Community Deployment](https://github.com/Alfresco/acs-community-deployment) 
`docker-compose.yml` file, but with a few additions. In order to see the distributed nature of 
the caching mechanism in action, a small cluster is started by the `docker-compose.yml`. This means
that two aldica-enabled Alfresco repositories and two aldica-enabled Alfresco Share containers are fired up 
along with a simple Nginx load balancer. 

# Prerequisites
* Docker daemon and client

# Installation
Running `docker-compose up -d --build` within the `docker` sub-folder, will:

1. Build and start the two aldica-enabled Alfresco repositories.
1. Build the aldica-enabled Alfresco Share image and start two Share Docker containers.
1. Build and start the Nginx load balancer
1. Start all other Alfresco accessory containers (PostgreSQL, Solr6,...)

Note that this will take quite a while (~1 hour), depending on available CPU and network resources.

For convenience, you could set up DNS entries in the `/etc/hosts` file on your host machine:
```
127.0.0.1   localhost alfresco1 alfresco2 share1 share2 loadbalancer
```

The aldica-enabled Alfresco stack should now be running and available on 
`http://loadbalancer:8080/`. E.g. so if you want to access Share, go to 
`http://loadbalancer:8080/share/page`.
