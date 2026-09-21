# ProjectRC 작업 규칙

## 시작 순서

1. `HANDOFF.md`, `README.md`, `git status --short`를 읽고 현재 상태를 확인한다.
2. 사용자가 요청한 기능과 관련된 기존 파일을 먼저 검색한다.
3. 변경 파일과 이유를 짧게 설명한 뒤 작은 단위로 구현한다.
4. 테스트와 빌드 결과를 확인한 뒤 문서를 갱신한다.

## `update` 작업 재개

사용자가 `update`만 입력하면 아래 순서를 따른다.

1. `HANDOFF.md`, `README.md`, `git status --short`, 최근 커밋을 확인한다.
2. 실패한 테스트나 미완료 기기 확인이 있으면 가장 작은 다음 작업을 정하고 이유를 짧게 말한다.
3. 구현 뒤 관련 테스트와 APK 빌드를 실행한다.
4. 실제로 확인한 결과만 `HANDOFF.md`와 관련 문서에 갱신한다.

`update <할 일>`이면 같은 순서로 지정한 일을 구현한다. 요청이 비어 있으면 DB, 토큰, 기존 사용자 데이터를 바꾸지 않는다.

## 구조

- `server.js`: Node.js HTTP API, SQLite 마이그레이션, 파일 제공.
- `public/`: 웹 라운지와 `/review` 관리자 화면.
- `android/src/com/moment/randomchat/MainActivity.java`: Android 네이티브 Java 앱.
- `android/build.ps1`: APK 컴파일과 서명 검증. 결과는 `android/build/moment-local-debug.apk`.
- `data/`: SQLite DB와 백업. 기존 DB를 삭제하거나 초기화하지 않는다.
- `private/`: 로컬 담당자 토큰. Git 제외이며 읽거나 출력하지 않는다.
- `test/`: Node 내장 테스트.

## 실행과 검증

```powershell
npm ci
npm test
& .\android\build.ps1
.\start-phone.cmd
```

- 서버 주소는 공기계 기준 `http://192.168.0.2:3000`이다.
- Android Java·Manifest·리소스를 바꾸면 `android/build.ps1`을 반드시 실행한다.
- 서버·웹 API를 바꾸면 `npm test`를 실행한다.
- 실행 중인 서버는 `/app.apk` 요청 때 빌드 결과를 읽는다. APK 재빌드 뒤 서버 재시작은 필요하지 않다.

## 변경 원칙

- 기존 SQLite 데이터와 `private/` 파일을 건드리지 않는다.
- 토큰 원문, DB 내용, 개인정보를 README·Notion·Git에 넣지 않는다.
- API 변경은 `README.md`, `API_GUIDE.txt`, `HANDOFF.md`, `NOTION_UPDATE.txt` 중 관련 문서를 함께 갱신한다.
- Android는 입력, UI, 네트워크 요청, 서버 응답 반영을 분리한다. 주요 함수에는 역할과 호출 시점을 설명하는 한글 주석을 쓴다.
- 대규모 구조 변경보다 현재 구현을 보완하는 작은 변경을 우선한다.

## Git

- 커밋 전 `git diff --check`, 필요한 테스트, APK 빌드를 실행한다.
- `private/`, `data/`, `node_modules/`, 로그 파일은 스테이징하지 않는다.
- 원격 push는 사용자가 요청했을 때만 실행한다.
