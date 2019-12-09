# Manual verification tests

This section describes a few of simple manual tests which can be performed in order to verify that the Aldica module is working as expected.

## Infrastructure

The following setup will be needed:

- Two Alfresco Community repositories with the Aldica module installed according to the instructions given above (the repositories will be called `repo1` and `repo2`, respectively, below).
- A common database used by both of the repositories.

## Verifying the distribution of authentication tickets

In this verification test an authentication ticket for a user will be retrieved from `repo1`, and then it will be verified that the same user can use this ticket to authenticate against `repo2`. 
The cache for tickets is set up as a fully replicated cache, so all instances should have the same tickets and be able to validate any tickets created on any other instance. Get a ticket 
from `repo1` for the admin user like this (assuming that the password for the admin user is `admin`):

```
$ curl -i "http://repo1:8080/alfresco/service/api/login?u=admin&pw=admin"
```

This should give a response similar to this (disregarding the header information provided via the `-i` flag):

```
<?xml version="1.0" encoding="UTF-8"?>
<ticket>TICKET_d2d6cd64bf64cd54ddb6bec7f263de3671ff081d</ticket>
```

The obtained ticket can now be used to authenticate the admin user againts `repo2`, e.g. for getting the JSON user object for the admin user itself:

```
$ curl -i "http://repo2:8080/alfresco/service/api/people/admin?alf_ticket=TICKET_d2d6cd64bf64cd54ddb6bec7f263de3671ff081d"
```

which should yield something like:

```
{
	"url": "\/alfresco\/service\/api\/people\/admin",
	"userName": "admin",
	"enabled": true,
	"firstName": "Administrator",
	"lastName": "",
	...
}
```

It can thus be seen that the authentication tickets are distributed across the two instances via the cache holding tickets. The authentication tickets are only held in the in-memory caches and 
they are not stored in the database, i.e. if the ticket obtained from `repo1` is valid against `repo2`, it has been verified that the ticket has distributed between `repo1` and `repo2` via the 
caches.

## Verifying the distributed cache invalidation mechanism

See a description of the nature of the invalidating cache mechanism here: 
[https://docs.alfresco.com/community/concepts/cache-indsettings.html](https://docs.alfresco.com/community/concepts/cache-indsettings.html) (the mechanism for invalidation is described in the 
`cluster.type` bullet under "invalidating").

The mechanism for "remote invalidation" of the caches can be tested in the following way via the v1 ReST API (e.g. via the api-explorer web app or by using `curl` as in the example shown below). 
A specific node can be accessed on both instances (to ensure it is loaded into cache), modify it on one of the instances, and then 
re-access it on both to verify the state is the same (e.g. modification date / value of any modified 
properties). This is relevant because the cache for node identity and DB version is a "remote invalidating" 
cache, and the caches for properties / aspects are local-only caches using the data from the node 
identity and DB version for their lookup keys.

The verification test described above can be carried out as follows. The "Shared" folder will be used as an example as this folder has a "magic" ID for easy access. The node 
corresponding to the Shared folder can be loaded into the caches on both `repo1` and `repo2` like this:

```
$ curl -s -u admin:admin http://repo1:8080/alfresco/api/-default-/public/alfresco/versions/1/nodes/-shared- [| python -m json.tool]
$ curl -s -u admin:admin http://repo2:8080/alfresco/api/-default-/public/alfresco/versions/1/nodes/-shared- [| python -m json.tool]
```

(pipeing the response through e.g. the Python JSON module with `python -m json.tool` will pretty-print the result). The output from on of the `curl` commands will look similar to this:

```
{
    "entry": {
        "aspectNames": [
            "cm:titled",
            "cm:auditable",
            "app:uifacets"
        ],
        "createdAt": "2019-04-25T13:45:22.255+0000",
        "createdByUser": {
            "displayName": "Administrator",
            "id": "admin"
        },
        "id": "25f8fa3b-3d74-44b7-a9ca-6175944fca3d",
        "isFile": false,
        "isFolder": true,
        "modifiedAt": "2019-04-25T14:54:11.548+0000",
        "modifiedByUser": {
            "displayName": "Administrator",
            "id": "admin"
        },
        "name": "Shared",
        "nodeType": "cm:folder",
        "parentId": "18ce0064-3f97-4dac-b5e1-9febec99b6cb",
        "properties": {
            "app:icon": "space-icon-default",
            "cm:description": "Folder to store shared stuff",
            "cm:title": "Shared Folder"
        }
    }
}
```

The node can now be modified on `repo1`, e.g. the `cm:title` property will be changed:

```
$ curl -i -u admin:admin -X PUT -H 'Content-Type: application/json' -d '{"properties":{"cm:title":"New title!"}}' "http://repo1:8080/alfresco/api/-default-/public/alfresco/versions/1/nodes/-shared-"
```

which will return a JSON response reflecting the change. Both of the `curl` commands from above can now be run again, and it can be verified that the responses from these look similar to this:


```
{
    "entry": {
        "aspectNames": [
            "cm:titled",
            "cm:auditable",
            "app:uifacets"
        ],
        "createdAt": "2019-04-25T13:45:22.255+0000",
        "createdByUser": {
            "displayName": "Administrator",
            "id": "admin"
        },
        "id": "25f8fa3b-3d74-44b7-a9ca-6175944fca3d",
        "isFile": false,
        "isFolder": true,
        "modifiedAt": "2019-04-25T15:02:13.142+0000",
        "modifiedByUser": {
            "displayName": "Administrator",
            "id": "admin"
        },
        "name": "Shared",
        "nodeType": "cm:folder",
        "parentId": "18ce0064-3f97-4dac-b5e1-9febec99b6cb",
        "properties": {
            "app:icon": "space-icon-default",
            "cm:description": "Folder to store shared stuff",
            "cm:title": "New title!"
        }
    }
}
```

I.e. it is seen that the `cm:title` property has changed and the same goes for the value of `modifiedAt`. This ensures that the cache has been invalidated correctly, since we would otherwise 
receive the "old" data when running the HTTP GET request against `repo2`.