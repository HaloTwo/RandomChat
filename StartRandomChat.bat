@echo off
chcp 65001 >nul
setlocal

set "PROJECT_DIR=C:\Users\lsm62\Desktop\RandomChat"
set "MODEL=gemma4:26b"

title RandomChat - OpenCode + Ollama

cd /d "%PROJECT_DIR%" || (
    echo [오류] 프로젝트 폴더를 찾을 수 없습니다.
    echo %PROJECT_DIR%
    pause
    exit /b 1
)

echo ========================================
echo RandomChat 개발환경 시작
echo ========================================
echo 프로젝트: %PROJECT_DIR%
echo 모델: %MODEL%
echo.

if not exist "AGENTS.md" (
    echo [경고] 프로젝트 루트에 AGENTS.md가 없습니다.
    echo OpenCode가 프로젝트 에이전트 규칙을 읽지 못할 수 있습니다.
    echo.
) else (
    echo [확인] AGENTS.md 발견 - 프로젝트 기본 지침으로 사용됩니다.
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
        echo [주의] git pull 실패.
        echo 인터넷 연결이 없거나, 인증/충돌/원격 저장소 문제일 수 있습니다.
        echo 현재 로컬 파일 상태로 계속 실행합니다.
    ) else (
        echo.
        echo [완료] Git 최신화 완료.
    )
)

echo.
echo [3/3] OpenCode를 Ollama 로컬 모델로 실행합니다...
echo 작업 디렉터리: %CD%
echo.

ollama launch opencode --model %MODEL%

echo.
echo OpenCode가 종료되었습니다.
pause
endlocal
