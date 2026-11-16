@echo off
rem Do not print each command, only the messages we choose to echo

title Parking Dashboard Launcher
rem Name shown at the top of this console window

rem ---- Settings ----
set "PORT=8080"
rem The backend's port, only used when you pass an address to this file

rem ---- Find the project root ----
set "ROOT=%~dp0"
rem %~dp0 is the folder this .bat file lives in, it ends with a backslash

set "ROOT=%ROOT:~0,-1%"
rem Remove that last backslash, so quoting paths later cannot break

rem ---- Choose how the dashboard finds the backend ----
if "%~1"=="" (
    set "SERVER_ARG=auto"
) else (
    set "SERVER_ARG=http://%~1:%PORT%/api/frame"
)
rem No argument means search for the backend automatically, an argument such as 10.140.60.55 or localhost builds that address

rem ---- Safety checks ----
if not exist "%ROOT%\pom.xml" (
    echo ERROR: pom.xml was not found. Put this file in the project root, next to pom.xml.
    pause
    exit /b 1
)
rem Stop with a message if this is not the dashboard project folder

where mvn >nul 2>nul
if errorlevel 1 (
    echo ERROR: Maven was not found in PATH, needed to start the dashboard.
    pause
    exit /b 1
)
rem Stop with a message if the mvn command is not installed

rem ---- Start the dashboard ----
echo Starting dashboard in a new window, backend: %SERVER_ARG%
rem Tell the user what is happening

start "Parking Dashboard" /d "%ROOT%" cmd /k mvn javafx:run -Djavafx.args=--server=%SERVER_ARG%
rem Open a new window named Parking Dashboard, in the project root, running the dashboard, --server tells it where to read frames from

exit
rem Close this launcher window, the dashboard window stays open