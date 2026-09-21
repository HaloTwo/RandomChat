@echo off
chcp 65001 >nul
setlocal

rem 이 BAT 파일이 있는 폴더를 프로젝트 루트로 사용
set "PROJECT_DIR=%~dp0"
set "MODEL=gemma4:26b"

title RandomChat - OpenCode + Ollama

cd /d "%PROJECT_DIR%" || (
    echo [오류] 프로젝트 폴더로 이동할 수 없습니다.
    pause
    exit /b 1
)

echo ========================================
echo RandomChat 개발환경 시작
echo ========================================
echo 프로젝트: %CD%
echo 모델: %MODEL%
echo.

if not exist "AGENTS.md" (
    echo [경고] 프로젝트 루트에 AGENTS.md가 없습니다.
    echo OpenCode가 프로젝트 지침을 읽지 못할 수 있습니다.
) else (
    echo [확인] AGENTS.md 발견
)

echo.
echo [1/3] Git 저장소 확인...
git rev-parse --is-inside-work-tree >nul 2>&1

if errorlevel 1 (
    echo [건너뜀] 현재 폴더가 Git 저장소가 아닙니다.
) else (
    echo [확인] Git 저장소입니다.
    echo.
    echo [2/3] 원격 저장소 최신화 시도...

    git pull --ff-only

    if errorlevel 1 (
        echo.
        echo [주의] Git 최신화 실패.
        echo 인터넷 연결이 없거나, 인증/충돌/원격 저장소 문제가 있을 수 있습니다.
        echo 현재 로컬 파일 상태로 계속 실행합니다.
    ) else (
        echo.
        echo [완료] Git 최신화 완료.
    )
)

echo.
echo [3/3] OpenCode + Ollama 실행...
echo 작업 디렉터리: %CD%
echo.

ollama launch opencode --model %MODEL%

echo.
echo OpenCode가 종료되었습니다.
pause
endlocal
