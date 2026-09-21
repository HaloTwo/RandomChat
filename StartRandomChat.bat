@echo off
setlocal
cd /d "%~dp0ProjectRC"
cls

echo.
echo ========================================
echo           RandomChat Dev
echo ========================================
echo.
echo   Folder : %CD%
echo   Shared rules : AGENTS.md + HANDOFF.md
echo.
echo   [1] Codex       - main cloud work session
echo   [2] Local Qwen  - continue when Codex is unavailable
echo   [3] Phone server - run Android test server
echo.
set /p MODE=Choose 1, 2, or 3: 

if "%MODE%"=="1" goto codex
if "%MODE%"=="2" goto local
if "%MODE%"=="3" goto server

echo Invalid choice.
pause
exit /b 1

:codex
where codex >nul 2>nul
if errorlevel 1 (
    echo Codex CLI was not found in PATH.
    echo Install and sign in to Codex CLI, then run this file again.
    pause
    exit /b 1
)
echo.
echo Codex started. Type update to resume the project.
codex
exit /b %errorlevel%

:local
where ollama >nul 2>nul
if errorlevel 1 (
    echo Ollama was not found in PATH.
    echo Install Ollama and pull a Qwen model on this laptop first.
    pause
    exit /b 1
)
where opencode >nul 2>nul
if errorlevel 1 (
    echo OpenCode was not found in PATH.
    echo Install OpenCode on this laptop first.
    pause
    exit /b 1
)
set "LOCAL_MODEL="
for /f "skip=1 tokens=1" %%M in ('ollama list ^| findstr /i "qwen3.5 qwen"') do if not defined LOCAL_MODEL set "LOCAL_MODEL=%%M"
if not defined LOCAL_MODEL (
    echo No Qwen model was found in Ollama.
    echo Pull the Qwen3.5 model you want, then run this file again.
    pause
    exit /b 1
)
echo.
echo Local Qwen started: %LOCAL_MODEL%
echo Type update to resume the project.
opencode --model ollama/%LOCAL_MODEL%
exit /b %errorlevel%

:server
call start-phone.cmd
exit /b %errorlevel%
