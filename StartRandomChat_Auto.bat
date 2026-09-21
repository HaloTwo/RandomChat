@echo off
setlocal
cd /d "%~dp0"

title RandomChat Local AI

if not exist "%~dp0OpenCodeWatchdog.ps1" (
    echo [ERROR] OpenCodeWatchdog.ps1 was not found.
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

rem Start watchdog first.
start "RandomChat Watchdog" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0OpenCodeWatchdog.ps1"

rem If OpenCode is already running, do not start a second TUI.
powershell.exe -NoProfile -Command "$p = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^opencode(\.exe)?$' -and $_.CommandLine -notmatch '\s(run|serve|service|api|web|debug)\b' }; if (@($p).Count -gt 0) { exit 0 } else { exit 1 }"

if "%ERRORLEVEL%"=="0" (
    echo.
    echo OpenCode is already running.
    echo Watchdog was started only. Existing session was NOT restarted.
    timeout /t 2 >nul
    exit /b 0
)

echo.
echo Starting OpenCode from RandomChat root:
echo   %CD%
echo.
opencode

exit /b %errorlevel%
