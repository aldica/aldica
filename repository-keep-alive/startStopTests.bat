@ECHO OFF

rem AFAIK no Maven plugin or JUnit library to support Docker-based tests supports simple start/stop during an integration test
rem ...so this script performs a general test of the cache keep alive by starting-stopping Repository nodes + keep-alive
rem all while doing basic cURL calls to check authentication tickets

SETLOCAL

CALL mvn clean integration-test -P StartStopTestPreparations

CALL docker-compose -f target\docker\start-stop-test-compose.yml up -d repository01
timeout /T 15 /nobreak

for /f "skip=1 tokens=3 delims=^<^>" %%a in ('curl --max-time 180 "http://localhost:8180/alfresco/service/api/login?u=admin&pw=admin"') do (
   SET "TICKET1=%%a"
)

IF ERRORLEVEL 1 (
   ECHO Error retrieving authentication ticket
   CALL docker-compose -f target\docker\start-stop-test-compose.yml down -v
   EXIT /b 1
)

CALL docker-compose -f target\docker\start-stop-test-compose.yml up -d keepAlive01
timeout /T 10 /nobreak

CALL docker-compose -f target\docker\start-stop-test-compose.yml logs --tail 6 keepAlive01 | find /i "Ignite instance repositoryGrid currently has 2 active nodes"
IF ERRORLEVEL 1 (
   ECHO Repository01 and KeepAlive01 did not connect
   CALL docker-compose -f target\docker\start-stop-test-compose.yml down -v
   EXIT /b 1
)

CALL docker-compose -f target\docker\start-stop-test-compose.yml stop repository01
timeout /T 10 /nobreak

CALL docker-compose -f target\docker\start-stop-test-compose.yml logs --tail 6 keepAlive01 | find /i "Ignite instance repositoryGrid currently has 1 active nodes"
IF ERRORLEVEL 1 (
   ECHO KeepAlive01 did not handle disconnect
   CALL docker-compose -f target\docker\start-stop-test-compose.yml down -v
   EXIT /b 1
)

CALL docker-compose -f target\docker\start-stop-test-compose.yml up -d keepAlive02
timeout /T 10 /nobreak

CALL docker-compose -f target\docker\start-stop-test-compose.yml logs --tail 6 keepAlive01 | find /i "Ignite instance repositoryGrid currently has 2 active nodes"
IF ERRORLEVEL 1 (
   ECHO KeepAlive01 and KeepAlive02 did not connect
   CALL docker-compose -f target\docker\start-stop-test-compose.yml down -v
   EXIT /b 1
)

CALL docker-compose -f target\docker\start-stop-test-compose.yml up -d repository02
timeout /T 15 /nobreak

for /f "skip=1 tokens=3 delims=^<^>" %%a in ('curl --max-time 180 "http://localhost:8280/alfresco/service/api/login?u=admin&pw=admin"') do (
   SET "TICKET2=%%a"
)

IF ERRORLEVEL 1 (
   ECHO Error retrieving authentication ticket
   CALL docker-compose -f target\docker\start-stop-test-compose.yml down -v
   EXIT /b 1
)

CALL docker-compose -f target\docker\start-stop-test-compose.yml logs --tail 6 keepAlive01 | find /i "Ignite instance repositoryGrid currently has 3 active nodes"

IF ERRORLEVEL 1 (
   ECHO Repository02 and KeepAlive nodes did not connect
   CALL docker-compose -f target\docker\start-stop-test-compose.yml down -v
   EXIT /b 1
)

IF NOT "%TICKET1%" EQU "%TICKET2%" (
   ECHO Authentication ticket was not properly kept alive and replicated to Repository02
   ECHO "Original ticket on Repository01: %TICKET1%"
   ECHO "New ticket on Repository02: %TICKET2%"
   CALL docker-compose -f target\docker\start-stop-test-compose.yml down -v
   EXIT /b 1
)

CALL docker-compose -f target\docker\start-stop-test-compose.yml down -v

ECHO Start-Stop tests of KeepAlive and Repository components completed succesfully

ENDLOCAL