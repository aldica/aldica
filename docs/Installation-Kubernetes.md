# Introduction
The Kubernetes installation utilises the upstream [Alfresco Content Services Community Deployment](https://github.com/Alfresco/acs-community-deployment) [Helm](https://helm.sh/) chart.

# Prerequisites
* Kubernetes cluster with Ingress

# Installation

Several steps are required to get an aldica-enabled Kubernetes installation, namely:

* Building the aldica-enabled docker images
* Pushing the aldica-enabled docker images to a docker registry
* Fetching the upstream Helm chart.
* Patching the upstream Helm chart.
* Installing the Helm chart.

## Building the aldica-enabled docker images
Building the aldica-enabled docker images can be done using the `Dockerfile`s within the `docker` sub-folder of the project root, namely the files:

* Dockerfile.repo
* Dockerfile.share

Note that building these images will take quite a while (~35 minutes), depending on available CPU, IO performance and network resources.

Building can be done either directly using `docker build .. -f Dockerfile.[repo|share]` or indirectly using `docker-compose build [alfresco|share]`.

After this step, it's assumed that the reader has build the docker images, and obtained the IDs for the images.

## Pushing the aldica-enabled docker images
We assume that a registry (`myregistry`) has already been configured.

Now we tag the images produced in the previous step;
```
docker tag {{ repo_id }} {{ myregistry }}/aldica-repo:latest
docker tag {{ share_id }} {{ myregistry }}/aldica-share:latest
```
And push them to `{{ myregistry }}`:
```
docker push {{ myregistry }}/aldica-repo:latest
docker push {{ myregistry }}/aldica-share:latest
```

## Fetching the upstream Helm chart
Fetch the latest [acs-community-deployment](https://github.com/Alfresco/acs-community-deployment/releases/latest) release archive.

Extract the archive and navigate to root folder (the folder containing the LICENSE).

This is the folder we want to work with in the next step.

## Patching the upstream Helm chart
Find the [provided patch file](./0001-Aldica.patch), and apply it to the Helm chart folder, by running:
```
patch -p1 -i docs/0001-Aldica.patch
```
At this point, you should insert your own docker images in the `values.yml` file.

Note: You may have to configure custom registry credentials, assuming your docker registry is not publicly available.

## Installing the Helm chart
At this point, the Helm chart can be installed according to the documentation in the README.md file found within the Helm chart folder, or according to the documentation on the acs-community-deployment repository.
