@echo off
setlocal
cd /d "%~dp0"

title RandomChat OpenCode Watchdog

echo.
echo ========================================
echo     RandomChat OpenCode Watchdog
echo ========================================
echo.
echo [1] New monitored OpenCode session
echo [2] Continue last monitored session
echo [3] Attach watchdog to CURRENT unfinished session
echo.
set /p MODE=Choose 1, 2, or 3: 

if "%MODE%"=="1" goto new
if "%MODE%"=="2" goto continue
if "%MODE%"=="3" goto attach

echo Invalid choice.
pause
exit /b 1

:new
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0OpenCodeWatchdog.ps1" -Mode new
goto end

:continue
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0OpenCodeWatchdog.ps1" -Mode continue
goto end

:attach
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0OpenCodeWatchdog.ps1" -Mode attach
goto end

:end
echo.
echo Watchdog stopped.
pause
