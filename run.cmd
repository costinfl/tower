@echo off
rem
rem Runs Tower locally: http://127.0.0.1:8080
rem
rem Why this script exists (issue #9)
rem --------------------------------
rem "mvn -pl tower-api spring-boot:run" resolves tower-domain and the other modules
rem from the *local repository*, not from the reactor. Edit a module and re-run that
rem command without "mvn install", and the application starts against the previously
rem installed jar.
rem
rem That failure is dangerous because it is usually silent. Adding a new class fails
rem loudly with NoClassDefFoundError, which is luck; change the *body* of an existing
rem method and the application boots happily and runs the old logic.
rem
rem So the install always runs. There is deliberately no flag to skip it.
rem
rem Usage:
rem   run.cmd                  build everything, then run
rem   run.cmd --debug          extra arguments are passed to spring-boot:run
rem
setlocal

cd /d "%~dp0"

where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven is not on the PATH. Tower needs Maven and a JDK 21. 1>&2
  exit /b 1
)

echo ==^> Building and installing all modules ^(so spring-boot:run cannot pick up a stale jar^)
call mvn install -DskipTests
if errorlevel 1 exit /b 1

echo.
echo ==^> Starting Tower on http://127.0.0.1:8080
if defined TOWER_HOME (
  echo     Data directory: %TOWER_HOME%
) else (
  echo     Data directory: %USERPROFILE%\.tower
)
echo.
call mvn -pl tower-api spring-boot:run %*
exit /b %errorlevel%
