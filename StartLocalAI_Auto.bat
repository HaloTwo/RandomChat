@echo off
setlocal
cd /d "%~dp0"
title RandomChat Local AI

if not exist "%~dp0OpenCodeWatchdog.ps1" (
    echo [ERROR] OpenCodeWatchdog.ps1 was not found.
    echo Put OpenCodeWatchdog.ps1 in the same folder as this BAT.
    pause
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0OpenCodeWatchdog.ps1" -Mode Launch
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
    echo.
    echo [ERROR] Local AI launcher exited with code %ERR%.
    pause
)

exit /b %ERR%
