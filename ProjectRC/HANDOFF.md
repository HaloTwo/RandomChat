# RandomChat 작업 재개 기록

## 2026-09-22 완료한 작업

- `StartRandomChat.bat`은 인자 없이 `ProjectRC/`에서 로컬 Codex OSS + Ollama `ornith-1.5:9b`를 시작한다. `cloud`, `server` 인자로 Cloud와 공기계 서버를 연다.
- 루트와 `ProjectRC`의 AGENTS를 Cloud/Local 공통 재개 규칙으로 맞췄다. `.agents/skills/randomchat-autopilot/SKILL.md`는 HANDOFF 목록에서 최대 세 개의 구현·검증 작업만 연속 처리한다.
- 공개 게시물·스토리·댓글은 실제 계정 ID와 닉네임을 반환하지 않는다. 콘텐츠별 고정 `익명 #0000`과 가입 때 한번 정한 성별(`male`/`female`)만 반환한다. 기존 계정의 성별은 비어 있어 중립 표시다.
- 웹을 원형 스토리·익명 피드·하단 게시물/접속자/설정 탭·플로팅 랜덤 매칭 확인창으로 개편했다. 게시물 메뉴에서 댓글과 익명 쪽지를 보내며, 받은 쪽지는 설정 탭에서 읽는다.
- 서버에 `/api/online`, `/api/inbox`, 게시물 댓글·쪽지 API, 관리자 `/api/admin/social?q=`, `/api/admin/search?q=`를 추가했다. 기존 관리자 대화 조회는 모든 방의 대화 본문을 제공한다.
- Android는 가입 때 성별 선택을 추가했고 공개 라운지에서 익명 별칭을 표시한다. 랜덤 매칭은 확인창을 거친다. 네이티브 하단 고정 탭과 댓글·쪽지는 아직 웹만 구현됐다.

## 마지막 검증

- `npm test`: 22개 통과.
- `android/build.ps1`: APK 컴파일·서명 통과. 출력: `android/build/moment-local-debug.apk`.
- 로컬 웹 가입 화면을 브라우저로 확인했다. 실제 공기계에서 새 화면·댓글·쪽지·성별 선택은 아직 확인하지 않았다.

## 다음 작업

1. 공기계에서 새 APK를 설치하고 성별 선택, 익명 글, 스토리, 매칭 종료를 실제로 확인한다.
2. Android에 웹과 같은 하단 고정 탭, 현재 접속자, 댓글·익명 쪽지 UI를 추가한다.
3. 관리자 `/review` 화면에 공개 라운지·검색 화면을 연결한다. 현재 API만 준비돼 있다.

## 재개 방법

```powershell
cd "$env:USERPROFILE\Desktop\RandomChat"
.\StartRandomChat.bat
```

`update`는 위 다음 작업의 첫 항목부터 재개한다. `$randomchat-autopilot`은 최대 세 작업을 구현·검증·기록한다. 토큰은 `private/LOCAL_TOKENS.txt`에서만 관리하고 출력·커밋하지 않는다.

## Git

기준 커밋: `2fdb751 Polish Android app and add OpenCode workflow`.

이번 변경은 아직 커밋하지 않았다. 원격 push는 사용자가 대상 저장소를 명시한 뒤에만 한다.
