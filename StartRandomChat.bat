@echo off
cd /d "%~dp0"
title RandomChat

echo.
echo ================================
echo        RandomChat Start
echo ================================
echo.

echo [1] Current folder
echo %CD%
echo.

echo [2] Checking Git...
where git >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Git not found
    pause
    exit /b 1
)
echo [OK] Git

echo.
echo [3] Checking Ollama...
where ollama >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Ollama not found
    pause
    exit /b 1
)
echo [OK] Ollama

echo.
echo [4] Updating repository...
git pull --ff-only
if errorlevel 1 (
    echo.
    echo [WARN] Git pull failed. Continue with local files.
)

echo.
echo [5] Starting OpenCode...
echo Model: qwen3.5:9b
echo.

ollama launch opencode --model qwen3.5:9b

echo.
echo [ERROR] OpenCode exited or failed to start.
pause