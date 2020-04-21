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
patch -p1 -i /path/to/this/repo/docs/0001-Aldica.patch
```
At this point, you should insert your own docker images in the `values.yml` file.

Note: You may have to configure custom registry credentials, assuming your docker registry is not publicly available.

## Installing the Helm chart
At this point, the Helm chart can be installed according to the documentation in the README.md 
file found within the Helm chart folder, or according to the documentation on the 
acs-community-deployment repository.

Currently, the Helm chart only works with Kubernetes version 1.15, so adjustments will be needed 
for later versions of Kubernetes. Below, an example using 
[Minikube](https://kubernetes.io/docs/setup/learning-environment/minikube/) and Kubernetes version 
1.15.0 is given.

## Example using Minikube

This example shows how to get an aldica cluster up and running in Kubernetes by using Minikube.

### Prerequisites

In the following, it will be assumed that Minikube has already been installed using the 
instructions given [here](https://kubernetes.io/docs/tasks/tools/install-minikube/) and also that 
Helm has been installed. See the installation instructions for Helm 
[here](https://helm.sh/docs/intro/install/).

### Preparing Minikube

Start a new Minikube cluster running Kubernetes 1.15.0 in e.g. VirtualBox with an appropriate amount 
of CPUs and memomy (if an existing Minikube cluster is already running you may need to delete 
this first):
```
$ minikube start --driver=virtualbox --cpus=6 --memory=24000 --kubernetes-version=v1.15.0
```

Run a few `kubectl` maintenance commands as described in the upstream 
[acs-community-deployment](https://github.com/Alfresco/acs-community-deployment) documentation:
```
$ kubectl create serviceaccount --namespace kube-system tiller
$ kubectl create clusterrolebinding tiller-cluster-rule --clusterrole=cluster-admin --serviceaccount=kube-system:tiller
$ helm init --service-account tiller
$ helm repo update
$ helm repo list
$ helm install stable/nginx-ingress --set rbac.create=true
```

Install the required dependencies with Helm:
```
$ cd /path/to/acs-community-deployment/helm/alfresco-content-services-community
$ helm dependencies update
```

### Install the cluster

The Helm chart can be installed with:
```
$ helm install .
```
A number of pods and services are now fired up. These can be inspected with: 
```
$ kubectl get pods
$ kubectl get services
```
which should result in output similar to the below once everything is up and running.
```
$ kubectl get pods
NAME                                                            READY   STATUS    RESTARTS   AGE
melting-alpaca-activemq-6f7bb7d865-xwjw2                        1/1     Running   0          94m
melting-alpaca-alfresco-cs-ce-imagemagick-6679c56fd-8v5nr       1/1     Running   0          94m
melting-alpaca-alfresco-cs-ce-libreoffice-86cf4866fc-889z9      1/1     Running   0          94m
melting-alpaca-alfresco-cs-ce-pdfrenderer-585c658875-4mspc      1/1     Running   0          94m
melting-alpaca-alfresco-cs-ce-repository-0                      1/1     Running   0          94m
melting-alpaca-alfresco-cs-ce-repository-1                      1/1     Running   0          89m
melting-alpaca-alfresco-cs-ce-share-67c644d9cd-tkstc            1/1     Running   1          94m
melting-alpaca-alfresco-cs-ce-tika-5996946446-hwf9v             1/1     Running   0          94m
melting-alpaca-alfresco-cs-ce-transform-misc-5fb6cfb65d-dkmrv   1/1     Running   0          94m
melting-alpaca-alfresco-search-solr-5b56488db9-lcvbg            1/1     Running   0          94m
melting-alpaca-keycl-0                                          1/1     Running   0          94m
melting-alpaca-nginx-ingress-controller-6bbb7dff44-pp8w4        1/1     Running   0          94m
melting-alpaca-nginx-ingress-default-backend-69776bf6dd-ndhdp   1/1     Running   0          94m
melting-alpaca-postgresql-acs-99776fd5-mb5pj                    1/1     Running   0          94m
melting-alpaca-postgresql-id-6b799ddffb-m2d7d                   1/1     Running   0          94m
mewing-guppy-nginx-ingress-controller-74b9c9bbdb-v6fmq          1/1     Running   0          95m
mewing-guppy-nginx-ingress-default-backend-7656bd9457-rg9tf     1/1     Running   0          95m

$ kubectl get services
NAME                                                TYPE           CLUSTER-IP       EXTERNAL-IP   PORT(S)                        AGE
kubernetes                                          ClusterIP      10.96.0.1        <none>        443/TCP                        110m
melting-alpaca-activemq-broker                      ClusterIP      10.102.144.199   <none>        61613/TCP,61616/TCP,5672/TCP   95m
melting-alpaca-activemq-web-console                 NodePort       10.102.224.82    <none>        8161:32087/TCP                 95m
melting-alpaca-alfresco-cs-ce-imagemagick           ClusterIP      10.111.86.218    <none>        80/TCP                         95m
melting-alpaca-alfresco-cs-ce-libreoffice           ClusterIP      10.110.57.194    <none>        80/TCP                         95m
melting-alpaca-alfresco-cs-ce-pdfrenderer           ClusterIP      10.111.189.23    <none>        80/TCP                         95m
melting-alpaca-alfresco-cs-ce-repository            ClusterIP      10.96.152.212    <none>        80/TCP                         95m
melting-alpaca-alfresco-cs-ce-repository-headless   ClusterIP      None             <none>        80/TCP                         95m
melting-alpaca-alfresco-cs-ce-share                 ClusterIP      10.99.175.101    <none>        80/TCP                         95m
melting-alpaca-alfresco-cs-ce-tika                  ClusterIP      10.104.61.91     <none>        80/TCP                         95m
melting-alpaca-alfresco-cs-ce-transform-misc        ClusterIP      10.106.58.79     <none>        80/TCP                         95m
melting-alpaca-alfresco-search-solr                 ClusterIP      10.102.37.178    <none>        80/TCP                         95m
melting-alpaca-keycl-headless                       ClusterIP      None             <none>        80/TCP                         95m
melting-alpaca-keycl-http                           ClusterIP      10.98.231.253    <none>        80/TCP                         95m
melting-alpaca-nginx-ingress-controller             LoadBalancer   10.103.219.102   <pending>     80:31543/TCP,443:31676/TCP     95m
melting-alpaca-nginx-ingress-default-backend        ClusterIP      10.103.93.115    <none>        80/TCP                         95m
melting-alpaca-postgresql-acs                       ClusterIP      10.109.115.2     <none>        5432/TCP                       95m
melting-alpaca-postgresql-id                        ClusterIP      10.107.101.58    <none>        5432/TCP                       95m
mewing-guppy-nginx-ingress-controller               LoadBalancer   10.103.234.4     <pending>     80:32328/TCP,443:31813/TCP     96m
mewing-guppy-nginx-ingress-default-backend          ClusterIP      10.103.208.191   <none>        80/TCP                         96m
```
