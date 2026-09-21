@echo off
cd /d "%~dp0"
cls

set "MODEL=qwen3-coder:30b"
set "AGENT=randomchat"

echo.
echo ========================================
echo            RandomChat Dev
echo ========================================
echo.
echo   Agent : %AGENT%
echo   Model : %MODEL%
echo.

echo   Checking project...

if exist ".opencode\agents\randomchat.md" (
    echo   Agent : Ready
) else (
    echo   Agent : Not found
)

if exist "AGENTS.md" (
    echo   Rules : Ready
) else (
    echo   Rules : Not found
)

if exist "knowledge" (
    echo   Cache : Ready
) else (
    echo   Cache : Not found
)

echo.
echo   Syncing Git...

git pull --ff-only >nul 2>&1

if errorlevel 1 (
    echo   Git   : Local mode
) else (
    echo   Git   : Updated
)

echo.
echo ----------------------------------------
echo   Starting OpenCode in 2 seconds...
echo ----------------------------------------
echo.

timeout /t 2 /nobreak >nul

ollama launch opencode --model %MODEL%