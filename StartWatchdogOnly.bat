@echo off
setlocal
cd /d "%~dp0"

title RandomChat Watchdog

if not exist "%~dp0OpenCodeWatchdog.ps1" (
    echo [ERROR] OpenCodeWatchdog.ps1 was not found.
    echo Put this BAT and OpenCodeWatchdog.ps1 in the RandomChat repository root.
    pause
    exit /b 1
)

if not exist "%~dp0AGENTS.md" (
    echo [ERROR] AGENTS.md was not found.
    echo This BAT must be in the RandomChat repository root.
    pause
    exit /b 1
)

where opencode >nul 2>nul
if errorlevel 1 (
    echo [ERROR] OpenCode was not found in PATH.
    pause
    exit /b 1
)

echo.
echo Starting watchdog only...
echo OpenCode will NOT be restarted.
echo Repository: %CD%
echo.

start "RandomChat Watchdog" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0OpenCodeWatchdog.ps1"

echo Watchdog started in a minimized PowerShell window.
timeout /t 2 >nul
exit /b 0
