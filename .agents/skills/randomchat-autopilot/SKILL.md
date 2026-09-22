---
name: randomchat-autopilot
description: RandomChat의 HANDOFF 목록을 바탕으로 제한된 구현·검증 사이클을 연속 실행한다.
---

# RandomChat 자율 진행

이 스킬은 `ProjectRC/`의 실제 코드 상태를 기준으로 최대 세 개의 작은 작업을 연속 처리한다.

1. `ProjectRC/HANDOFF.md`, `ProjectRC/README.md`, `git status --short`, 최근 커밋을 읽는다.
2. HANDOFF의 `자동 진행 목록`에서 첫 번째 미완료 항목을 고른다. 목록 밖의 기능을 새로 정하지 않는다.
3. 관련 코드를 검색하고 작은 변경 단위로 구현한다.
4. 서버·웹 변경은 `npm test`, Android 변경은 `android/build.ps1`을 실행한다. 실패하면 첫 오류를 고쳐 한 번 더 검증한다.
5. 실제로 끝난 내용, 실패 원인, 다음 한 단계를 HANDOFF에 압축해 기록한다.
6. 세 작업을 끝냈거나 실기기 시험, 외부 계정, 데이터 삭제, 제품 정책 판단, 반복되는 빌드 실패가 필요하면 멈춘다.

토큰, `private/`, SQLite 기존 데이터는 읽어서 출력하거나 삭제하지 않는다. 원격 push와 외부 문서 수정은 사용자의 명시 요청이 있을 때만 한다.
