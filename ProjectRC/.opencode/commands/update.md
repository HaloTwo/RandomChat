---
description: 현재 상태를 확인하고 요청한 기능을 구현·검증·문서화한다
---

ProjectRC의 업데이트 작업을 끝까지 수행한다. 요청: $ARGUMENTS

1. `AGENTS.md`, `HANDOFF.md`, `README.md`, `git status --short`를 읽는다.
2. 요청이 비어 있으면 현재 미완료 항목과 실패한 검증을 확인해 가장 작은 다음 작업을 제안하고, 이미 안전한 검증은 실행한다.
3. 요청이 있으면 관련 구현을 검색하고 기존 구조를 유지하며 수정한다. 토큰, DB, 기존 사용자 데이터는 출력·삭제·초기화하지 않는다.
4. 서버 또는 웹 변경 뒤 `npm test`를 실행한다. Android 변경 뒤 `& .\android\build.ps1`을 실행한다.
5. 변경한 기능의 실행 방법과 확인 결과를 `HANDOFF.md`에 최신 상태로 적고, 사용자 문서가 달라졌다면 `README.md`, `API_GUIDE.txt`, `NOTION_UPDATE.txt`도 갱신한다.
6. 마지막에 변경 파일, 테스트 결과, 공기계에서 확인할 다음 한 단계를 간결하게 보고한다.

사용자가 명시적으로 요청하지 않으면 Git commit, push, Notion 수정, 데이터 삭제, 토큰 발급을 실행하지 않는다.
