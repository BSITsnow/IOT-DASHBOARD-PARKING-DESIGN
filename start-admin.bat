@echo off
title Parking Admin Launcher

rem Go to the backend folder, relative to where this .bat file lives
cd /d "%~dp0backend"

echo Starting backend in a new window...
start "Parking Backend" cmd /k ".\mvnw.cmd spring-boot:run"

echo Waiting for the server to be ready...
:wait
timeout /t 2 /nobreak >nul
curl.exe -s -o nul http://localhost:8080/login.html
if errorlevel 1 goto wait

echo Server is up. Opening login page...
start "" http://localhost:8080/login.html
exit