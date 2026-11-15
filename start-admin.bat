@echo off
rem Do not print each command, only the messages we choose to echo

title Parking Admin Launcher
rem Name shown at the top of this console window

rem ---- Settings, change PORT if you set server.port in application.properties ----
set "PORT=8080"
rem The port the Spring Boot backend listens on, and the dashboard and checks below use it too

set "ADMIN_URL=http://localhost:%PORT%/admin/index.html"
rem The admin page address, also used to test whether the backend is ready

rem ---- Find the project root ----
set "ROOT=%~dp0"
rem %~dp0 is the folder this .bat file lives in, it ends with a backslash

set "ROOT=%ROOT:~0,-1%"
rem Remove that last backslash, so quoting paths later cannot break

rem ---- Safety checks ----
if not exist "%ROOT%\backend\mvnw.cmd" (
    echo ERROR: backend\mvnw.cmd was not found. Put this file in the project root.
    pause
    exit /b 1
)
rem Stop with a message if the backend project is not next to this file

where mvn >nul 2>nul
if errorlevel 1 (
    echo ERROR: Maven was not found in PATH, needed to start the dashboard.
    pause
    exit /b 1
)
rem Stop with a message if the mvn command is not installed, the dashboard needs it

where curl.exe >nul 2>nul
if errorlevel 1 (
    echo ERROR: curl.exe was not found, needed to check that the backend is up.
    pause
    exit /b 1
)
rem Stop with a message if curl is missing, it is included with Windows 10 and 11

rem ---- Step 1: start the Spring Boot backend ----
echo Starting backend in a new window...
rem Tell the user what is happening

start "Parking Backend" /d "%ROOT%\backend" cmd /k ".\mvnw.cmd spring-boot:run"
rem Open a new window named Parking Backend, in the backend folder, running the server, cmd /k keeps it open so you can read the logs

rem ---- Step 2: wait until the backend answers ----
echo Waiting for the backend on port %PORT%...
rem Tell the user what is happening

set /a TRIES=0
rem Counter for the number of checks so far

:wait
rem Label, the loop jumps back here

set /a TRIES+=1
rem Add one to the counter

if %TRIES% gtr 90 goto failed
rem After 90 checks, 3 minutes, give up and go to the error message

timeout /t 2 /nobreak >nul
rem Wait 2 seconds without allowing key presses to skip, and hide the countdown text

curl.exe -s -o nul %ADMIN_URL%
rem Ask the backend for the admin page, -s is silent, -o nul throws the page away, it only fails if nothing is listening

if errorlevel 1 goto wait
rem Not up yet, so check again, any HTTP answer, even a 404, means the server is running

echo Backend is up.
rem The loop ended, the backend answered

rem ---- Step 3: start the JavaFX dashboard ----
echo Starting dashboard in a new window...
rem Tell the user what is happening

start "Parking Dashboard" /d "%ROOT%" cmd /k mvn javafx:run -Djavafx.args=--server=http://localhost:%PORT%/api/frame
rem Open a new window named Parking Dashboard, in the project root, running the dashboard, --server makes it read frames from the backend

rem ---- Step 4: open the admin page ----
echo Opening admin page...
rem Tell the user what is happening

start "" "%ADMIN_URL%"
rem Open the admin page in the default browser, the empty "" is the required window title slot for start

exit
rem Close this launcher window, the backend and dashboard windows stay open

:failed
rem Label, reached only when the backend did not answer in time

echo ERROR: The backend did not start within 3 minutes. Check the Parking Backend window for errors.
rem Tell the user where to look

pause
rem Keep the window open so the message can be read

exit /b 1
rem End with an error code