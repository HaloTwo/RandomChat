@echo off
cd /d "%~dp0"
if not exist "node_modules\sharp\package.json" (
  call npm ci
  if errorlevel 1 (
    echo 패키지 설치에 실패했습니다.
    pause
    exit /b 1
  )
)
node server.js
pause
