# AGENTS.md

# RandomChat 프로젝트 기본 정보

현재 프로젝트는 `RandomChat`이다.

현재 Git 저장소 루트를 프로젝트 루트로 사용한다.
Windows 절대경로를 코드나 지침에 하드코딩하지 않는다.

프로젝트 구조는 다음과 같다.

- 프로젝트 규칙
  - `AGENTS.md`

- OpenCode 프로젝트 설정
  - `opencode.jsonc`

- 개발 실행 스크립트
  - `StartRandomChat.bat`

- 실제 애플리케이션
  - `ProjectRC/`

- 현재까지의 개발 진행 내역
  - `ProjectRC/README.md`

- 작업 인수인계 및 복구 체크포인트
  - `ProjectRC/HANDOFF.md`

- 로컬 지식 저장소
  - `knowledge/`

- Notion 로컬 캐시
  - `knowledge/notion/`

- 외부 개발 문서 캐시
  - `knowledge/docs/`

중요:

`AGENTS.md`, `opencode.jsonc`, `StartRandomChat.bat`, `knowledge/`는
`ProjectRC/` 내부에 있지 않고 Git 저장소 루트에 있다.

따라서 다음과 같은 잘못된 경로를 사용하지 않는다.

- `ProjectRC/AGENTS.md`
- `ProjectRC/opencode.jsonc`
- `ProjectRC/StartRandomChat.bat`
- `ProjectRC/knowledge/`
- `ProjectRC/ReadMe`

현재 개발 진행 내역은 정확히 다음 파일을 사용한다.

`ProjectRC/README.md`

현재 작업 복구 상태는 정확히 다음 파일을 사용한다.

`ProjectRC/HANDOFF.md`

파일이나 디렉터리 경로를 임의로 추측하지 않는다.

필요한 경우 실제 파일 탐색 도구를 사용하여
존재 여부와 실제 경로를 먼저 확인한다.


# AI 실행 환경

현재 프로젝트는 다음 두 개발 모드를 사용한다.

## Codex Cloud

`StartRandomChat.bat`의 Codex 항목을 통해 실행한다.

클라우드 모델을 사용하는 주 개발 환경이다.

## Local AI

로컬 작업은 다음 환경을 사용한다.

- Ollama
- OpenCode
- 기본 로컬 모델: `ornith-1.5:9b`

Local AI는 Codex 사용량이 부족하거나,
오프라인 작업이 필요하거나,
빠른 로컬 반복 작업이 필요한 경우 사용할 수 있다.

로컬 AI 실행에 Codex CLI의 `--oss` 방식을 사용하지 않는다.

로컬 AI 실행에 `opencode --model ...`을 사용하지 않는다.

OpenCode의 기본 모델은 `opencode.jsonc`에서 관리한다.

OpenCode에서 현재 세션에 다른 모델이 명시적으로 선택되어 있다면
에이전트가 임의로 모델을 변경하지 않는다.

에이전트는 스스로 Cloud/Local 실행 모드를 전환하지 않는다.


# StartRandomChat 실행 구조

`StartRandomChat.bat`는 `ProjectRC/`를 실제 작업 디렉터리로 사용한다.

권장 메뉴 구조는 다음과 같다.

- `[1] Codex`
- `[2] Local AI - New`
- `[3] Local AI - Continue`
- `[4] Phone server`

`Local AI - New`는 새 OpenCode 작업을 시작한다.

`Local AI - Continue`는 가능한 경우
`opencode --continue`로 마지막 OpenCode 세션을 이어간다.

Local AI 실행 전에는 다음을 확인한다.

- Ollama 설치 여부
- OpenCode 설치 여부
- `ornith-1.5:9b` 설치 여부

모델이 없다면 임의로 다른 모델을 선택하지 않는다.

사용자에게 다음 설치 명령을 안내한다.

`ollama pull ornith-1.5:9b`


# 공통 작업 재개

Codex와 Local AI는 대화 기록 자체를 공유한다고 가정하지 않는다.

연속 작업의 기준은 다음이다.

1. 실제 Git 작업 트리
2. `ProjectRC/HANDOFF.md`
3. `ProjectRC/README.md`
4. 실제 `ProjectRC/` 코드

사용자가 `update`만 입력하면 먼저 다음을 확인한다.

1. `ProjectRC/HANDOFF.md`
2. `ProjectRC/README.md`
3. `git status --short`
4. 현재 branch
5. 최근 commit
6. 필요한 경우 실제 `ProjectRC/` 코드

그 다음 HANDOFF에 기록된
현재 미완료 작업의 다음 단계부터 바로 이어서 작업한다.

이미 끝난 분석을 처음부터 반복하지 않는다.

`update <할 일>`은 같은 절차로 지정한 작업을 수행한다.

토큰, DB, 기존 사용자 데이터는
요청 없이 출력·삭제·초기화하지 않는다.


# HANDOFF 체크포인트 규칙

`ProjectRC/HANDOFF.md`는
장시간 작업의 복구 체크포인트다.

다음 상황에서는 HANDOFF를 현재 실제 상태에 맞게 갱신한다.

- 의미 있는 구현 단계가 완료된 경우
- 여러 파일에 걸친 수정이 완료된 경우
- 테스트 또는 빌드를 수행한 경우
- 오류 원인을 파악한 경우
- 오래 걸리는 다음 단계로 넘어가기 전
- Git commit 직전
- 현재 세션이 길어져 중단 가능성이 높아진 경우

단순 파일 하나를 읽거나
tool call 하나를 실행할 때마다 HANDOFF를 갱신하지 않는다.

HANDOFF에는 최소한 다음 내용을 기록한다.

- 현재 작업 목표
- 완료한 작업
- 현재 미완료 작업
- 수정한 주요 파일
- 마지막 테스트 또는 빌드 명령
- 마지막 검증 결과
- 현재 해결 중인 오류
- 다음에 바로 수행해야 할 한 단계
- commit 여부
- commit hash가 있다면 해당 hash

HANDOFF는 단순 작업 일지처럼 끝없이 추가하지 않는다.

새로운 세션이 읽었을 때
현재 상태와 다음 행동을 빠르게 이해할 수 있도록
최신 상태 중심으로 정리한다.

기능 단위 진행 상황이 변경되었다면
`ProjectRC/README.md`도 함께 갱신한다.


# Windows Shell 최우선 규칙

현재 개발 환경은 Windows이며
shell은 Windows PowerShell 5.1일 수 있다.

따라서 Bash 문법을 가정하지 않는다.

다음 문법을 사용하지 않는다.

- `&&`
- `||`
- Bash 전용 pipe 처리
- Bash 전용 `grep`
- Bash 전용 `sed`
- Bash 전용 `awk`
- Bash 전용 path 문법

여러 명령을 실행할 때는
가능하면 각각 별도의 shell tool call로 실행한다.

반드시 한 번에 실행해야 한다면
PowerShell 5.1 호환 `;`를 사용한다.

잘못된 예:

`git status --short && git log -n 3 && cat package.json`

권장 방식:

`git status --short`

그 다음 별도 호출:

`git log -n 3`

그 다음 별도 호출:

`Get-Content package.json -Encoding UTF8`

필요한 경우 한 호출에서 다음처럼 사용할 수 있다.

`git status --short; git log -n 3; Get-Content package.json -Encoding UTF8`

Windows PowerShell에서는 가능하면 다음 명령을 사용한다.

- 파일 목록: `Get-ChildItem`
- 파일 읽기: `Get-Content`
- 파일 존재 확인: `Test-Path`
- 현재 경로: `Get-Location`

현재 작업 디렉터리가 이미 `ProjectRC/`라면
불필요하게 절대경로 `cd`를 반복하지 않는다.

명령이 실패했다면
동일한 실패 명령을 그대로 반복하지 않는다.

오류 메시지를 읽고
Windows PowerShell 호환 방식으로 수정한다.


# Windows 파일 인코딩 규칙

이 프로젝트의 텍스트 파일은 UTF-8을 사용한다.

PowerShell에서 한국어가 포함된 파일을 읽을 때는
가능하면 UTF-8 인코딩을 명시한다.

예:

`Get-Content .\README.md -Encoding UTF8`

`Get-Content .\HANDOFF.md -Encoding UTF8`

저장소 루트의 파일을 읽는 경우
현재 작업 위치를 먼저 확인하고 올바른 상대경로를 사용한다.

한글이 깨져 보이면 내용을 추측하지 않는다.

UTF-8 방식으로 다시 읽거나
OpenCode의 파일 읽기 도구를 사용한다.

인코딩 출력이 이상하다는 이유만으로
같은 `git status` 명령을 반복하지 않는다.


# 최우선 응답 규칙

사용자에게 하는 모든 설명과 최종 응답은
반드시 한국어로 작성한다.

사용자가 영어로 입력해도 한국어로 답한다.

다음 항목은 필요한 경우 영어 원문을 유지할 수 있다.

- 코드
- 변수명
- 함수명
- 클래스명
- 파일명
- 명령어
- 로그 원문
- 라이브러리명
- API명

일반적인 설명,
작업 결과,
오류 설명,
질문,
상태 안내는 한국어로 작성한다.

사용자에게 보이는 진행 메시지에서
이유 없이 중국어, 일본어 또는 다른 언어를 섞지 않는다.


# 자율 작업 규칙

사용자가 작업 목표와 요구사항을 충분히 제공했다면
구현 계획이나 세부 구현 결정을 다시 사용자에게 요청하지 않는다.

현재 코드,
README,
HANDOFF,
knowledge,
Notion 등에서 확인 가능한 사항은
직접 확인하고 합리적으로 판단하여 계속 진행한다.

다음과 같은 일반적인 개발 결정은
특별한 위험이 없다면 에이전트가 직접 판단한다.

- 수정할 파일 선택
- 기존 코드 스타일에 맞는 구현 방식
- UI의 세부 여백과 정렬
- 기존 API 호출부 정합성 수정
- 테스트 순서
- 작은 리팩터링
- 명확한 버그 수정
- 기존 기능을 유지하기 위한 호환 처리

다음 경우에만 사용자에게 질문하고 작업을 멈춘다.

- 기존 데이터 손실 가능성이 있는 경우
- 해결할 수 없는 Git 충돌이 있는 경우
- 비밀정보 또는 보안 위험이 있는 경우
- 요구사항끼리 직접 충돌하는 경우
- 프로젝트 자료만으로 결정할 수 없는 중요한 제품 정책 결정
- 외부 계정 또는 사용자 승인이 반드시 필요한 작업

단순한 구현 선택 때문에
사용자에게 구현 계획을 다시 묻지 않는다.

"어떻게 할까요?"
"계획을 정해주세요."
같은 질문을 불필요하게 하지 않는다.


# 지속 작업 규칙

작업을 시작한 뒤
계획만 설명하고 턴을 종료하지 않는다.

다음과 같은 상태 설명만 반복하고 종료하지 않는다.

- "확인하겠습니다"
- "읽겠습니다"
- "진행하겠습니다"
- "프로젝트 구조를 확인하겠습니다"
- "git status를 다시 확인하겠습니다"

필요한 파일을 실제로 읽고
수정하고
검증한다.

오류가 발생하면
오류 메시지를 읽고 원인을 분석한 뒤
수정하고 다시 테스트한다.

요청한 작업의 완료 조건이 충족될 때까지
다음 사이클을 계속한다.

분석
→ 구현
→ 검증
→ 오류 확인
→ 수정
→ 재검증

중간 단계가 끝났다는 이유만으로
Done 처리하지 않는다.

실제 구현 또는 검증까지 끝난 뒤에만
최종 응답을 한다.


# 반복 분석 방지 규칙

같은 사실을 반복해서 분석하지 않는다.

같은 명령을 이유 없이 반복 실행하지 않는다.

이미 확인한 사실은 현재 세션에서 사실로 유지한다.
새로운 증거가 있을 때만 다시 확인한다.

예:

`git status --short`에서
`opencode.jsonc` 하나만 modified라는 사실을 확인했다면
특별한 변경이 발생하기 전까지 같은 상태 확인을 반복하지 않는다.

같은 명령 또는 같은 분석을
두 번 이상 반복하려는 경우
반복하기 전에 다음을 판단한다.

1. 이전 시도가 왜 실패했는가?
2. 이번 시도에서 무엇을 다르게 하는가?
3. 실제로 다시 실행할 필요가 있는가?

위 질문에 명확한 답이 없다면
반복하지 않고 다음 작업 단계로 넘어간다.


# Reasoning Loop 방지 규칙

같은 생각이나 같은 문장을 반복하기 시작하면
현재 reasoning을 중단하고
이미 확인된 사실을 짧게 정리한 뒤
다음 구체적인 tool action으로 이동한다.

예를 들어 다음과 같은 문장을
반복 생성하지 않는다.

- "프로젝트 구조를 확인하겠습니다."
- "git status를 확인하겠습니다."
- "각각 확인하겠습니다."
- "다시 확인하겠습니다."

파일을 읽어야 한다면 실제 Read 도구를 호출한다.

파일을 수정해야 한다면 실제 Edit 도구를 호출한다.

명령을 실행해야 한다면 실제 Shell 도구를 호출한다.

분석만 반복하면서 시간을 소비하지 않는다.


# 새 세션 시작 시 초기화

새 세션에서 첫 사용자 요청을 받으면
바로 답변부터 하지 말고
RandomChat 프로젝트 상태를 한 번 확인한다.

이 초기화는 세션당 한 번만 수행한다.

같은 세션에서 매 요청마다 반복하지 않는다.

다음 순서로 수행한다.

1. `ProjectRC/HANDOFF.md`를 읽는다.

2. `ProjectRC/README.md`를 읽는다.

3. Git 상태를 한 번 확인한다.

4. `knowledge/notion/` 디렉터리의 실제 파일 목록을 확인한다.

5. 기존 Notion 캐시가 있으면 필요한 범위만 확인한다.

6. Notion MCP를 사용할 수 있고
   현재 작업에 최신 기획이 실제로 필요하다면
   지정된 RandomChat Notion 페이지를 직접 읽는다.

7. 사용자의 요청에 실제 구현 상태 확인이 필요하면
   `ProjectRC/`의 실제 소스코드를 탐색한다.

8. 위 확인이 끝나면
   분석만 반복하지 말고 사용자의 실제 작업을 수행한다.

프로젝트 파일이나 Notion에서 직접 확인할 수 있는 정보를
사용자에게 다시 설명해달라고 요구하지 않는다.

다음과 같은 질문을 받으면
프로젝트 자료부터 직접 확인한다.

- "내가 어디까지 진행했지?"
- "현재 진행 상황 알려줘"
- "다음 작업 뭐야?"
- "뭐까지 구현했어?"
- "이 기능 구현되어 있어?"
- "다음에 뭘 하면 돼?"

"이전 대화를 기억하지 못합니다"라는 이유로
작업을 포기하지 않는다.

RandomChat 프로젝트 진행 상황은
대화 기억이 아니라 다음 자료를 기준으로 판단한다.

1. 실제 `ProjectRC/` 코드
2. `ProjectRC/HANDOFF.md`
3. `ProjectRC/README.md`
4. 최신 Notion 정보
5. `knowledge/`의 로컬 캐시


# RandomChat Notion

RandomChat 프로젝트의 원격 기획 및 정리 페이지는 다음과 같다.

`https://app.notion.com/p/04-3dd4a21a8b6781da8601ce1b9e3789fe`

Notion 페이지를 읽을 때
Notion 전체 검색을 먼저 수행하지 않는다.

`ProjectRC`,
`welcome`,
`RandomChat`
등의 임의 검색어를 사용하여
다른 Notion 페이지를 찾으려고 하지 않는다.

먼저 지정된 페이지를 직접 읽는다.


# Notion fetch 사용 규칙

RandomChat Notion 페이지를 읽을 때
`notion-fetch`를 사용한다.

반드시 `id` 인자에 다음 전체 URL 문자열을 전달한다.

`https://app.notion.com/p/04-3dd4a21a8b6781da8601ce1b9e3789fe`

올바른 형태:

`id = "https://app.notion.com/p/04-3dd4a21a8b6781da8601ce1b9e3789fe"`

다음과 같은 존재하지 않는 인자를 사용하지 않는다.

- `url`
- `page_url`

`id` 인자를 사용한다.

Notion fetch가 실패했다고 해서
임의로 전혀 관계없는 Notion 페이지를 검색하지 않는다.

Notion fetch가 실패해도
현재 작업이 로컬 자료만으로 가능하다면
작업을 중단하지 않는다.


# Notion MCP 사용 제한

RandomChat 프로젝트 정보를 확인할 때
Notion의 페이지 검색/읽기 기능만 사용한다.

Notion의 Custom Agent 또는 Session 기능을 사용하지 않는다.

다음 종류의 기능은
RandomChat 프로젝트 확인에 사용하지 않는다.

- search-agents
- spawn-session
- send-message-to-session
- get-session-status
- wait-session
- search-sessions
- query-sessions
- stop-session
- get-tool-access

Notion 내부의 다른 AI Agent에게 작업을 위임하지 않는다.


# Notion 로컬 캐시

Notion에서 확인한 RandomChat 핵심 정보는 다음 파일에 저장한다.

`knowledge/notion/randomchat.md`

존재하지 않는 다음과 같은 파일명을 임의로 추측하지 않는다.

- `cache.json`
- `notion.json`
- `project.json`
- 기타 임의 파일명

`knowledge/notion/`을 사용할 때는
먼저 실제 존재하는 파일 목록을 확인한다.

`randomchat.md`에는 가능하면 다음 정보를 저장한다.

- 원본 Notion URL
- 페이지 제목
- 마지막 동기화 시각
- 프로젝트 목적
- 프로젝트 기획
- 기능 요구사항
- 예정 기능
- 개발 방향
- 개발 메모
- 기타 개발에 필요한 핵심 정보

기존 캐시와 원격 내용이 동일하다면
불필요하게 파일을 다시 작성하지 않는다.


# 온라인 상태에서 작업

인터넷 및 Notion MCP를 사용할 수 있어도
모든 작업에서 Notion을 반복 호출하지 않는다.

다음 경우에만 Notion을 확인한다.

- 사용자가 Notion 최신 내용 반영을 요청한 경우
- 기획이 변경되었을 가능성이 있는 경우
- 현재 로컬 자료만으로 요구사항을 판단할 수 없는 경우
- 구현 방향에 최신 기획이 실제로 필요한 경우

Notion에서 최신 정보를 확인했다면
중요한 내용을
`knowledge/notion/randomchat.md`에 반영한다.


# 오프라인 상태에서 작업

인터넷 또는 Notion MCP를 사용할 수 없어도
작업을 중단하지 않는다.

다음 순서로 로컬 정보를 사용한다.

1. `ProjectRC/HANDOFF.md`
2. `ProjectRC/README.md`
3. `knowledge/notion/`의 마지막 Notion 캐시
4. `knowledge/docs/`의 관련 개발 문서 캐시
5. 실제 `ProjectRC/` 코드

Notion 캐시가 없다면
HANDOFF,
README,
실제 코드를 사용해서 작업을 계속한다.

인터넷 연결이 없다는 이유만으로
사용자에게 프로젝트 정보를 다시 설명해달라고 요구하지 않는다.

최신 원격 정보가 반드시 필요한 작업이라면
현재 로컬 자료 기준으로 작업 중이라는 사실만 알려준다.


# 정보별 역할

## 실제 ProjectRC 코드

현재 실제로 무엇이 구현되어 있는지 판단하는 최종 기준이다.

README나 Notion에 구현되었다고 적혀 있어도
실제 코드에 없다면 구현된 것으로 단정하지 않는다.


## ProjectRC/HANDOFF.md

현재 진행 중이거나
직전 세션에서 미완료된 작업을 빠르게 복구하기 위한 체크포인트다.

새 세션에서 같은 작업을 처음부터 분석하는 대신
HANDOFF의 현재 상태와 다음 작업을 우선 사용한다.

HANDOFF 내용과 실제 Git/코드 상태가 다르면
실제 Git과 코드를 우선한다.


## ProjectRC/README.md

현재까지 완료된 개발 내역을 빠르게 파악하는 기본 자료다.

README 내용과 실제 코드가 다르면
실제 코드를 우선한다.

차이가 중요하다면 사용자에게 알려준다.


## Notion

다음 정보를 파악하는 주요 기준이다.

- 프로젝트 기획
- 앞으로 구현할 기능
- 기능 요구사항
- 개발 방향
- 사용자가 정리한 아이디어
- 향후 계획

Notion에 적혀 있다는 이유만으로
이미 구현된 기능이라고 판단하지 않는다.


## knowledge/notion/

마지막으로 동기화한 Notion 정보의 로컬 복사본이다.

인터넷이 없거나
Notion MCP를 사용할 수 없을 때 사용한다.


## knowledge/docs/

Context7 등을 통해 확인한
프레임워크,
라이브러리,
SDK,
API 관련 개발 정보를 저장한다.


# 프로젝트 진행 상황 질문 처리

사용자가 현재 진행 상황을 물으면
최소한 `ProjectRC/HANDOFF.md`와
`ProjectRC/README.md`를 실제로 읽은 뒤 답한다.

필요한 경우 실제 코드도 확인한다.

최종 답변은 다음 기준으로 간결하게 정리한다.

- 완료된 작업
- 현재 구현 상태
- 아직 남은 작업
- 다음으로 진행할 작업

사용자에게 이미 존재하는 프로젝트 정보를 다시 요구하지 않는다.


# Context7 및 최신 개발 정보

최신 라이브러리,
프레임워크,
SDK,
API 정보가 필요한 경우
Context7 MCP를 우선 사용한다.

Context7는 모든 작업마다 호출하지 않는다.

다음 경우에 사용한다.

- 최신 API 사용법이 필요한 경우
- 사용 중인 라이브러리의 현재 버전 문서가 필요한 경우
- 모델 자체 지식이 오래되었을 가능성이 있는 경우
- 공식 사용 방법을 확인해야 하는 경우

Context7에서 확인한 중요한 정보는
오프라인에서도 다시 사용할 수 있도록
`knowledge/docs/` 아래 Markdown 파일로 저장하거나 갱신한다.

가능하면 다음 정보를 기록한다.

- 라이브러리 또는 프레임워크 이름
- 버전
- 확인 날짜
- 핵심 사용법
- RandomChat에서 사용하는 이유
- 실제 적용 위치

Context7를 사용할 수 없다면
`knowledge/docs/`의 마지막 로컬 캐시와
실제 프로젝트 코드를 사용한다.


# 프로젝트 작업 규칙

작업 전에 기존 구현을 필요한 범위에서 먼저 확인한다.

이미 구현된 기능을 불필요하게 다시 만들지 않는다.

기존 RandomChat 프로젝트의 구조와 코드 스타일을 최대한 유지한다.

기존 구조를 전혀 파악하지 않은 상태에서
대규모 수정이나 리팩터링을 수행하지 않는다.

사용자가 요청하지 않은 파일이나 구조를
불필요하게 추가하지 않는다.

요구사항을 임의로 크게 확장하지 않는다.

다만 프로젝트 파일,
README,
HANDOFF,
Notion에서 확인 가능한 내용이라면
사용자에게 묻기 전에 직접 확인한다.

필요한 확인이 끝났으면
계속 탐색만 하지 말고 실제 구현으로 넘어간다.


# 검증 규칙

코드를 수정한 뒤
가능한 경우 실제 검증을 수행한다.

명령 이름을 추측하지 않는다.

먼저 `package.json`,
프로젝트 설정,
기존 스크립트 등에서
실제로 존재하는 명령을 확인한다.

가능한 범위에서 다음을 수행한다.

- 테스트
- lint
- type check
- build
- 관련 통합 테스트
- 필요한 경우 Android/APK 빌드

실패하면 로그를 읽고 원인을 분석한다.

같은 실패 명령을 아무 변경 없이 반복하지 않는다.

원인을 수정한 뒤 다시 검증한다.

외부 기기,
외부 계정,
사용자 직접 조작이 필요한 검증만
미검증 상태로 남길 수 있다.


# 보안

다음 정보는
`knowledge/`,
README,
HANDOFF,
Git으로 추적되는 파일에 저장하지 않는다.

- API Key
- Access Token
- Refresh Token
- 비밀번호
- OAuth Client Secret
- 인증 토큰
- `.env`의 비밀값
- 기타 민감한 인증 정보

프로젝트에서 비밀정보를 발견해도
다른 문서나 캐시 파일에 복사하지 않는다.

DB의 기존 사용자 데이터를
요청 없이 출력하거나 삭제하거나 초기화하지 않는다.


# Git

프로젝트 실행 BAT 파일에서
안전한 경우 `git pull --ff-only`를 사용할 수 있다.

에이전트 자체는
사용자가 명시적으로 요청하지 않는 한
다음 작업을 하지 않는다.

- `git commit`
- `git push`
- 브랜치 생성
- 브랜치 삭제
- 강제 병합
- force push
- history rewrite
- 원격 저장소 설정 변경
- 기존 변경의 reset
- 기존 변경의 checkout
- 기존 변경의 restore

사용자가 해당 작업에서
commit 또는 push를 명시적으로 허용했다면
그 작업 범위에서는 정상적인 commit/push를 수행할 수 있다.

기존 미커밋 변경이 발견되면
자기 작업이라고 가정하지 않는다.

먼저 필요한 범위의 diff를 확인하고
사용자 작업을 보존한다.

기존 변경을 임의로 버리지 않는다.

원격과 로컬 양쪽에 변경사항이 있어
충돌 가능성이 있다면
강제로 덮어쓰거나 force push하지 않는다.

충돌을 안전하게 자동 해결할 수 없는 경우에만
사용자에게 알린다.

commit 전에는 가능한 경우 다음을 확인한다.

- `git status --short`
- `git diff`
- `git diff --check`

비밀정보,
불필요한 생성파일,
DB 개인정보 등이
commit 대상에 들어가지 않았는지 확인한다.


# 정보 신뢰 우선순위

현재 구현 여부를 판단할 때 기본 우선순위는 다음과 같다.

1. 실제 `ProjectRC/` 코드
2. 실제 Git 작업 트리
3. `ProjectRC/HANDOFF.md`
4. `ProjectRC/README.md`
5. 최신 Notion 정보
6. `knowledge/`의 마지막 로컬 캐시
7. 모델 자체 지식

앞으로 구현해야 할 기능과 프로젝트 기획 의도는
최신 Notion 정보를 주요 기준으로 삼는다.

다만 Notion을 사용할 수 없다는 이유만으로
이미 충분히 정의된 작업을 중단하지 않는다.


# 완료 조건

에이전트는 다음 조건을 만족하기 전에는
작업을 완료했다고 보고하지 않는다.

- 요청한 핵심 구현이 실제 파일에 반영됨
- 관련 기존 기능의 영향을 확인함
- 가능한 테스트 또는 빌드를 수행함
- 발생한 오류를 가능한 범위에서 수정함
- 필요한 문서를 실제 구현 상태에 맞게 갱신함
- Git 작업을 요청받았다면 Git 검증까지 완료함

모든 작업이 끝난 뒤
최종 결과를 한국어로 간결하게 보고한다.

최종 보고에는 필요한 경우 다음을 포함한다.

- 수정한 주요 파일
- 실제 변경 내용
- 실행한 테스트 또는 빌드
- 검증 결과
- 해결하지 못한 문제
- Git 상태
- commit hash
- push 결과

OpenCode Watchdog 작업 상태 규칙

이 프로젝트는 ProjectRC/.agent-watchdog.json을
OpenCode watchdog용 작업 상태 파일로 사용한다.

이 파일은 실행 상태 전달용이며 Git에 커밋하지 않는다.
가능하면 .gitignore에 다음을 추가한다.

ProjectRC/.agent-watchdog.json

장기/다단계 작업 시작

사용자가 구현, 수정, 테스트, 리팩터링 등
여러 단계가 필요한 작업을 요청하면 실제 작업을 시작하기 전에
ProjectRC/.agent-watchdog.json을 다음 형식으로 생성 또는 갱신한다.

{
  "status": "running",
  "task": "현재 작업을 한 문장으로 요약",
  "updated_at": "현재 시각 ISO 8601",
  "next": "다음에 수행할 구체적인 한 단계"
}

status는 작업이 실제로 완료되기 전까지 반드시 running으로 유지한다.

의미 있는 체크포인트마다 updated_at과 next를 갱신한다.

중간에 멈추지 않기

status가 running인 동안에는
단순히 사용자 입력을 기다리기 위해 작업을 종료하지 않는다.

프로젝트 내부 자료로 판단 가능한 내용은 직접 판단하고 계속 진행한다.

"계속", "계속 진행", "다음으로 진행" 같은 사용자 입력이 없어도
현재 요청의 완료 조건까지 작업을 이어간다.

다음 경우에만 사용자 입력을 기다릴 수 있다.

데이터 손실 위험

해결 불가능한 Git 충돌

비밀정보/보안 위험

서로 충돌하는 요구사항

외부 승인이나 계정 조작이 반드시 필요한 경우

작업 완료

요청한 구현과 가능한 검증이 실제로 끝난 뒤,
최종 응답 직전에 상태를 다음처럼 변경한다.

{
  "status": "done",
  "task": "완료한 작업",
  "updated_at": "현재 시각 ISO 8601",
  "next": ""
}

작업이 실패하여 사람 판단이 반드시 필요한 경우에는:

{
  "status": "blocked",
  "task": "현재 작업",
  "updated_at": "현재 시각 ISO 8601",
  "next": "사용자에게 필요한 판단 또는 조치"
}

done이나 blocked로 바꾸지 않은 채
작업 턴을 임의로 종료하지 않는다.

Watchdog 동작

watchdog는 status=running인데 OpenCode 세션이 idle이 되면
잠시 대기한 뒤 같은 세션에 자동으로 계속 진행 프롬프트를 보낸다.

따라서 정상적으로 작업을 끝냈다면
반드시 최종 응답 전에 status=done으로 바꾼다.

긴 Thinking 자체는 실패로 간주하지 않는다.
ACTIVE 상태이며 Ollama/OpenCode 프로세스가 실제 계산 중이면 기다린다.