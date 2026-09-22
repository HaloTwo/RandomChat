# RandomChat 작업 재개 기록

## 2026-09-22 완료한 작업

- `StartRandomChat.bat`은 인자 없이 `ProjectRC/`에서 로컬 Codex OSS + Ollama `ornith-1.5:9b`를 시작한다. `cloud`, `server` 인자로 Cloud와 공기계 서버를 연다.
- 루트와 `ProjectRC`의 AGENTS를 Cloud/Local 공통 재개 규칙으로 맞췄다. `.agents/skills/randomchat-autopilot/SKILL.md`는 HANDOFF 목록에서 최대 세 개의 구현·검증 작업만 연속 처리한다.
- 공개 게시물·스토리·댓글은 실제 계정 ID와 닉네임을 노출하지 않는다. 서버 내부의 익명 식별자는 관리자·API 호환용으로만 유지하고 일반 피드 카드에는 표시하지 않는다.
- 웹은 원형 스토리·제목 중심 피드·하단 게시물/내 대화/설정 탭·플로팅 랜덤 매칭 확인창을 사용한다. 게시물은 오른쪽 아래 `+`에서 작성하며 카드에는 조회 수와 댓글 수를 표시한다.
- 스토리는 전체 화면에서 5초 진행 막대 후 다음 스토리로 자동 전환된다. 게시물 상세는 제목·본문·이미지·댓글·쪽지를 한 화면에 표시한다.
- 메시지는 읽음(`✓`/`✓✓`)과 상대 입력 중 상태를 서버에 저장해 표시한다. 연결된 상대의 프로필 사진 목록을 열면 방문 기록이 남고, 대화방 상단에서 프로필 방문자와 받은 쪽지를 확인한다.
- 서버에 `/api/online`, `/api/inbox`, 게시물 댓글·쪽지 API, 관리자 `/api/admin/social?q=`, `/api/admin/search?q=`를 추가했다. 기존 관리자 대화 조회는 모든 방의 대화 본문을 제공한다.
- Android는 가입 때 성별 선택을 추가했고 공개 라운지에서 제목·본문·조회 수·댓글 수, 메시지 읽음·입력 중 상태를 표시한다. 웹과 동일한 카드·하단 고정 탭·상세 댓글 UI 전환은 다음 작업이다.
- 웹은 이미지가 있는 스토리만 공개하고, 원형 스토리를 누르면 사진·본문·메뉴를 전체 화면으로 본다. 게시물은 제목·본문 미리보기와 상세 댓글 창을 사용한다. 일반 사용자 탭은 `게시물 / 내 대화 / 설정`이다.
- `backup-local.cmd`는 `data/backups/`에 SQLite 스냅샷을 만든다. `StartRandomChat.bat`, `HANDOFF.md`, Git 커밋과 함께 로컬 재개·백업 경로로 사용한다.

## 마지막 검증

- `npm test`: 24개 통과.
- `android/build.ps1`: APK 컴파일·서명 통과. 출력: `android/build/moment-local-debug.apk`.
- 로컬 웹 가입 화면을 브라우저로 확인했다. 실제 공기계에서 새 화면·댓글·쪽지·성별 선택은 아직 확인하지 않았다.

## 다음 작업

1. 공기계에서 새 APK를 설치하고 제목·조회/댓글 수, 읽음·입력 중, 스토리 자동 전환을 실제로 확인한다.
2. Android를 웹과 같은 하단 고정 `게시물 / 내 대화 / 설정` 카드 UI와 게시물 상세·댓글 UI로 전환한다.
3. 관리자 `/review`에서 공개 라운지 조회와 검색 결과를 실제 관리자 토큰으로 확인한다.

## 재개 방법

```powershell
cd "$env:USERPROFILE\Desktop\RandomChat"
.\StartRandomChat.bat
```

`update`는 위 다음 작업의 첫 항목부터 재개한다. `$randomchat-autopilot`은 최대 세 작업을 구현·검증·기록한다. 토큰은 `private/LOCAL_TOKENS.txt`에서만 관리하고 출력·커밋하지 않는다.

## Git

현재 커밋은 작업 완료 뒤 `git log -1 --oneline`으로 확인한다.

원격 push는 사용자가 대상 저장소를 명시한 뒤에만 한다.
