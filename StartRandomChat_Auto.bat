@echo off
setlocal
cd /d "%~dp0"

title RandomChat OpenCode + Watchdog

if not exist "%~dp0AGENTS.md" (
    echo [ERROR] AGENTS.md was not found.
    echo Put this BAT in the RandomChat repository root.
    pause
    exit /b 1
)

if not exist "%~dp0OpenCodeWatchdog.ps1" (
    echo [ERROR] OpenCodeWatchdog.ps1 was not found.
    pause
    exit /b 1
)

where opencode >nul 2>nul
if errorlevel 1 (
    echo [ERROR] OpenCode was not found in PATH.
    pause
    exit /b 1
)

rem Start watchdog minimized from RandomChat root.
start "RandomChat Watchdog" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0OpenCodeWatchdog.ps1"

rem Start OpenCode itself from RandomChat root.
echo.
echo Starting OpenCode in:
echo   %CD%
echo.
opencode

exit /b %errorlevel%
