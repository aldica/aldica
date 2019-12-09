# Introduction

A `docker-compose.yml` file is provided in the `docker` sub-folder of the project root.
This `docker-compose.yml` file is based upon the upstream [Alfresco Content Services Community Deployment](https://github.com/Alfresco/acs-community-deployment) `docker-compose.yml` file.

# Prerequisites
* Docker daemon and client

# Installation
Running `docker-compose up` within the `docker` sub-folder, will:

1. Download and build the [Acosix Alfresco Utility Core](https://github.com/Acosix/alfresco-utility) AMP.
2. Build the aldica-ignite AMP, according to the documentation in the [build](./Build.md) section.
3. Build an aldica-enabled Alfresco Repository and Share docker image.
4. Start-up a docker-compose based Alfresco installation.

Note that this will take quite a while (~1 hour), depending on available CPU and network resources.

At which point an aldica-enabled Alfresco stack should be running, and be available on `http://localhost:8080/`
