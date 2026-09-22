@echo off
@setlocal EnableExtensions
@cd /d "%~dp0ProjectRC"
@cls

@echo.
@echo ========================================
@echo           RandomChat Dev
@echo ========================================
@echo.
@echo   Folder : %CD%
@echo.
@echo   Local model : ornith-1.5:9b
@echo   Run now     : Local Codex
@echo.
@echo   Optional: StartRandomChat.bat cloud
@echo   Optional: StartRandomChat.bat server
@echo.

@set "MODE=2"
@if /i "%~1"=="cloud" set "MODE=1"
@if /i "%~1"=="server" set "MODE=3"

@if "%MODE%"=="1" goto cloud
@if "%MODE%"=="2" goto local
@if "%MODE%"=="3" goto phone

@echo.
@echo [ERROR] Invalid choice: %MODE%
@echo.
@pause
@exit /b 1


:cloud
@where codex >nul 2>&1
@if errorlevel 1 (
    @echo.
    @echo [ERROR] Codex CLI was not found.
    @echo.
    @pause
    @exit /b 1
)

@cls
@echo.
@echo ========================================
@echo           Codex Cloud
@echo ========================================
@echo.
@echo   Folder : %CD%
@echo.
@echo   Type "update" to resume the project.
@echo.

@call codex

@set "CODEX_EXIT=%ERRORLEVEL%"

@echo.
@echo ========================================
@echo   Codex closed.
@echo   Exit code : %CODEX_EXIT%
@echo ========================================
@echo.
@pause
@exit /b %CODEX_EXIT%


:local
@where codex >nul 2>&1
@if errorlevel 1 (
    @echo.
    @echo [ERROR] Codex CLI was not found.
    @echo.
    @pause
    @exit /b 1
)

@where ollama >nul 2>&1
@if errorlevel 1 (
    @echo.
    @echo [ERROR] Ollama was not found.
    @echo.
    @pause
    @exit /b 1
)

@set "LOCAL_MODEL=ornith-1.5:9b"

@ollama list 2>nul | findstr /i /c:"%LOCAL_MODEL%" >nul
@if errorlevel 1 (
    @echo.
    @echo [ERROR] Model not found: %LOCAL_MODEL%
    @echo.
    @echo Install:
    @echo   ollama pull %LOCAL_MODEL%
    @echo.
    @pause
    @exit /b 1
)

@cls
@echo.
@echo ========================================
@echo        Codex Local / Ollama
@echo ========================================
@echo.
@echo   Model  : %LOCAL_MODEL%
@echo   Folder : %CD%
@echo   Mode   : Autonomous
@echo.
@echo   Type "update" to resume the project.
@echo.

@call codex --oss --local-provider ollama -m "%LOCAL_MODEL%" --sandbox workspace-write --ask-for-approval never

@set "CODEX_EXIT=%ERRORLEVEL%"

@echo.
@echo ========================================
@echo   Local Codex closed.
@echo   Exit code : %CODEX_EXIT%
@echo ========================================
@echo.
@pause
@exit /b %CODEX_EXIT%

:phone
@cls
@echo.
@echo ========================================
@echo          Phone server
@echo ========================================
@echo.
@call ..\start-phone.cmd
@exit /b %ERRORLEVEL%
