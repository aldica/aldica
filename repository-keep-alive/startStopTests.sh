#!/bin/bash

set -uo pipefail

# AFAIK no Maven plugin or JUnit library to support Docker-based tests supports simple start/stop during an integration test
# ...so this script performs a general test of the cache keep alive by starting-stopping Repository nodes + keep-alive
# all while doing basic cURL calls to check authentication tickets

mvn clean integration-test -P StartStopTestPreparations

docker-compose -f target/docker/start-stop-test-compose.yml up -d repository01
sleep 15

ticket1=$(curl --max-time 180 "http://localhost:8180/alfresco/service/api/login?u=admin&pw=admin" 2>&1 | grep -Eo "TICKET_[^<]+")

if [[ ! $? -eq 0 ]]
then
   echo "Error retrieving authentication ticket"
   docker-compose -f target/docker/start-stop-test-compose.yml down -v
   exit 1
fi

docker-compose -f target/docker/start-stop-test-compose.yml up -d keepAlive01
sleep 10

docker-compose -f target/docker/start-stop-test-compose.yml logs --tail 6 keepAlive01 | grep -o "Ignite instance repositoryGrid currently has 2 active nodes"

if [[ ! $? -eq 0 ]]
then
   echo "Repository01 and KeepAlive01 did not connect"
   docker-compose -f target/docker/start-stop-test-compose.yml down -v
   exit 1
fi

docker-compose -f target/docker/start-stop-test-compose.yml stop repository01
sleep 10

docker-compose -f target/docker/start-stop-test-compose.yml logs --tail 15 keepAlive01 | grep -o "Ignite instance repositoryGrid currently has 1 active nodes"

if [[ ! $? -eq 0 ]]
then
   echo "KeepAlive01 did not handle disconnect"
   docker-compose -f target/docker/start-stop-test-compose.yml down -v
   exit 1
fi

docker-compose -f target/docker/start-stop-test-compose.yml up -d keepAlive02
sleep 10

docker-compose -f target/docker/start-stop-test-compose.yml logs --tail 6 keepAlive01 | grep -o "Ignite instance repositoryGrid currently has 2 active nodes"

if [[ ! $? -eq 0 ]]
then
   echo "KeepAlive01 and KeepAlive02 did not connect"
   docker-compose -f target/docker/start-stop-test-compose.yml down -v
   exit 1
fi

docker-compose -f target/docker/start-stop-test-compose.yml up -d repository02
sleep 15

ticket2=$(curl --max-time 180 "http://localhost:8280/alfresco/service/api/login?u=admin&pw=admin" 2>&1 | grep -Eo "TICKET_[^<]+")

if [[ ! $? -eq 0 ]]
then
   echo "Error retrieving authentication ticket"
   docker-compose -f target/docker/start-stop-test-compose.yml down -v
   exit 1
fi

docker-compose -f target/docker/start-stop-test-compose.yml logs --tail 6 keepAlive01 | grep -o "Ignite instance repositoryGrid currently has 3 active nodes"

if [[ ! $? -eq 0 ]]
then
   echo "Repository02 and KeepAlive nodes did not connect"
   docker-compose -f target/docker/start-stop-test-compose.yml down -v
   exit 1
fi

if [ "$ticket1" != "$ticket2" ]
then
   echo "Authentication ticket was not properly kept alive and replicated to Repository02"
   echo "Original ticket on Repository01: $ticket1"
   echo "New ticket on Repository02: $ticket2"
   docker-compose -f target/docker/start-stop-test-compose.yml down -v
   exit 1
fi

docker-compose -f target/docker/start-stop-test-compose.yml down -v

echo "Start-Stop tests of KeepAlive and Repository components completed successfully"
