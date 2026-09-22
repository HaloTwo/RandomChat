@echo off
setlocal
set "FOUND="

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":3000 .*LISTENING"') do (
    set "FOUND=1"
    echo 포트 3000의 서버(PID %%P)를 종료합니다.
    taskkill /PID %%P /F >nul 2>&1
)

if not defined FOUND (
    echo 포트 3000에서 실행 중인 서버가 없습니다.
) else (
    echo 서버를 종료했습니다.
)

endlocal
