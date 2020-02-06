# About
This directory contains a basic load-testing / benchmarking script based on [K6](https://k6.io/) from [LoadImpact](https://loadimpact.com/), which itself is open source under the GNU Affero General Public License v3.0. This load-testing / benchmarking script uses the Alfresco Public ReST API to perform very basic operations on Alfresco Content Service (authenticate, navigate a folder tree, create content, update content, read metadata). Running with multiple, concurrent clients as well as having a dedicated verification use case (randomly executed in 20% of all test iterations) this script can be used to verify that the Alfresco Community Open Grid extension works correctly and keeps the cache of all nodes in the grid consistent, even when under quite heavy load.

### Note on "work-in-progress" state

This script / load-testing setup is still very much a work in progress, as this is the first project any of this project's developers have used it on. As such, there are a long list of potential improvements to be made, and the state of this README may not always perfectly reflect recent changes to the script.

# Test configuration

The script is parameterised by two external JSON files, an exported *options* object at the top of the main *script.js* file and 16 external content files using the name pattern *content%d* (no leading zero / padding in numeric part). The JSON files *hosts.json* and *users.json* are used to configure the addresses of the Alfresco Content Services instances to work with and the credentials of the Alfresco users to use in running the test. Sample files are contained in this directory for both files.

## Hosts

The *hosts.json* file specifies all elements require to construct a valid base URL to an Alfresco Content Services instance, specifically the URL protocol (http vs https), host name / address, port and root context. The "default" configuration is currently used by the load-testing script for all implemented use cases except for the dedicated data grid verification use case. That use case uses the "fineGrained" configurations for specific hosts. These configuration blocks automatically inherit from "default" and only need to provide the attributes which are different.

## Users

The *users.json* file specifies the user credentials to use when connecting to Alfresco Content Services. It can contain configurations for multiple users, keyed by their user ID / name. Each user has a set of "default" credentials and can optionally have different "fineGrained" sets for specific hosts in the *hosts.json* file. Currently, the *script.js* will randomly select a user from this configuration file on every test iteration. Since the purpose of this script is the verification of the Alfresco Community Open Grid, specifying fine grained credentials is not relevant.

## Options

The exported *options* object in *script.js* specifies various thresholds for the test to be considered a success / failure, as well as the various stages of concurrency for the execution. Some of the thresholds may abort a test run within 5-15 seconds if their metric does not improve to fall in the acceptable range. Currently, the test uses the following thresholds:

- Failed logins required to be below 1% of all attempts (script currently only logs in to obtain a ticket for data grid verification use case) - will abort test run if above threshold for 5 seconds
- Bad requests (HTTP 400 errors) required to be below 1% of all requests - will abort test run if above threshold for 15 seconds
- Node not found (HTTP 404 errors) for ID-based accesses required to be below 5% of all relevant requests - will abort test run if above threshold for 15 seconds
- Permission denied (HTTP 403 errors) required to be below 1% of all requests
- Name conflicts (HTTP 409 errors on node update / creation) required to be below 1% of all relevant requests
- Data integrity violations (HTTP 422 errors on node update / creation) required to be below 1% of all relevant requests
- Non-specific failures (HTTP 500 or any unrecognised errors) required to be below 1% of all requests

Currently, the test is run in three stages:

- stage 1: ramp up to 40 concurrent clients for duration of 2 minutes
- stage 2: run 40 concurrent clients for duration of 26 minutes
- stage 3: ramp down from 40 concurrent clients for duration of 2 minutes

## External content files

The 16 external content files are named *content1* through *content16* and should be provided on an individual basis as long as no safe, reusable corpus of test files exists in this directory.

# Running

The test can be run with a local install of K6 or within a Docker container. Please see the K6 documentation on the [various options to run it](https://docs.k6.io/docs/running-k6). The simplest form of invocation for this test script from within this directory is:

```
k6 run ./script.js
```

This will output any logging from the script on the console and print a summary of all metrics of the test at the end. More details / insight can be gained by creating an additional JSON output of the low-level metrics, which can then be view & analysed e.g. in Grafana or other tools. This additional output can be obtained by invoking the test script with the following command:

```
k6 run --out json=./results.json ./script.js
```

The specific contents of the JSON output may vary as *script.js* evolves. The overall structure and some examples of processing it can be found in the K6 documentation on [result output](https://docs.k6.io/docs/results-output). 