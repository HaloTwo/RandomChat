# AGENTS.md

# RandomChat 프로젝트 기본 정보

현재 프로젝트는 `RandomChat`이다.

현재 Git 저장소 루트를 프로젝트 루트로 사용한다.
Windows 절대경로를 코드나 지침에 하드코딩하지 않는다.

프로젝트 구조는 다음과 같다.

- 프로젝트 규칙
  - `AGENTS.md`

- 실제 애플리케이션
  - `ProjectRC/`

- 현재까지의 개발 진행 내역
  - `ProjectRC/README.md`

- 로컬 지식 저장소
  - `knowledge/`

- Notion 로컬 캐시
  - `knowledge/notion/`

- 외부 개발 문서 캐시
  - `knowledge/docs/`

중요:

`AGENTS.md`와 `knowledge/`는 `ProjectRC/` 내부에 있지 않다.

따라서 다음과 같은 잘못된 경로를 사용하지 않는다.

- `ProjectRC/AGENTS.md`
- `ProjectRC/knowledge/`
- `ProjectRC/ReadMe`

현재 개발 진행 내역은 정확히 다음 파일을 사용한다.

`ProjectRC/README.md`

파일이나 디렉터리 경로를 임의로 추측하지 않는다.
필요한 경우 `glob`, `read` 등 실제 파일 탐색 도구를 사용하여 존재 여부를 먼저 확인한다.

## Codex와 로컬 Qwen 공통 작업 재개

`StartRandomChat.bat`는 Codex와 로컬 Qwen OpenCode를 모두 `ProjectRC/`에서 시작한다.
두 실행기는 `ProjectRC/AGENTS.md`, `ProjectRC/HANDOFF.md`, `ProjectRC/README.md`를 같은 작업 기준으로 사용한다.

사용자가 `update`만 입력하면 먼저 `ProjectRC/HANDOFF.md`, `ProjectRC/README.md`, `git status --short`, 최근 커밋을 확인한다. 그 다음 실패한 검증 또는 미완료 기기 확인에서 가장 작은 다음 작업을 고르고, 구현 뒤 필요한 테스트·APK 빌드·문서 갱신을 수행한다.

`update <할 일>`은 같은 절차로 지정한 일을 수행한다. 토큰, DB, 기존 사용자 데이터는 요청 없이 출력·삭제·초기화하지 않는다.


# 최우선 응답 규칙

사용자에게 하는 모든 설명과 응답은 반드시 한국어로 작성한다.

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

일반적인 설명, 작업 결과, 오류 설명, 질문, 안내는 반드시 한국어로 작성한다.


# 새 세션 시작 시 초기화

새 OpenCode 세션에서 첫 사용자 요청을 받으면
바로 답변부터 하지 말고 먼저 RandomChat 프로젝트 상태를 확인한다.

이 초기화는 세션당 한 번만 수행한다.
같은 세션에서 매 요청마다 반복하지 않는다.

다음 순서로 수행한다.

1. `ProjectRC/README.md`를 읽는다.

2. `knowledge/notion/` 디렉터리의 실제 파일 목록을 확인한다.

3. 기존 Notion 캐시가 있으면 내용을 확인한다.

4. Notion MCP를 사용할 수 있다면
   아래에 지정된 RandomChat Notion 페이지를 직접 읽는다.

5. Notion의 최신 내용이 기존 로컬 캐시와 다르다면
   `knowledge/notion/randomchat.md`를 최신 내용으로 갱신한다.

6. 사용자의 요청에 실제 구현 상태 확인이 필요하다면
   `ProjectRC/`의 실제 소스코드를 탐색한다.

7. 위 확인이 끝난 후 사용자의 요청에 답한다.

프로젝트 파일이나 Notion에서 직접 확인할 수 있는 정보를
사용자에게 다시 설명해달라고 요구하지 않는다.

다음과 같은 질문을 받으면 반드시 프로젝트 자료부터 직접 확인한다.

- "내가 어디까지 진행했지?"
- "현재 진행 상황 알려줘"
- "다음 작업 뭐야?"
- "뭐까지 구현했어?"
- "이 기능 구현되어 있어?"
- "다음에 뭘 하면 돼?"

"이전 대화를 기억하지 못합니다"라는 이유로 답변을 포기하지 않는다.

RandomChat 프로젝트 진행 상황은
대화 기억이 아니라 다음 자료를 기준으로 판단한다.

1. 실제 `ProjectRC/` 코드
2. `ProjectRC/README.md`
3. 최신 Notion 정보
4. `knowledge/`의 로컬 캐시


# RandomChat Notion

RandomChat 프로젝트의 원격 기획 및 정리 페이지는 다음과 같다.

`https://app.notion.com/p/04-3dd4a21a8b6781da8601ce1b9e3789fe`

Notion 페이지를 읽을 때
Notion 전체 검색을 먼저 수행하지 않는다.

`ProjectRC`, `welcome`, `RandomChat` 등의 임의 검색어를 사용하여
다른 Notion 페이지를 찾으려고 하지 않는다.

먼저 지정된 페이지를 직접 읽는다.


# Notion fetch 사용 규칙

RandomChat Notion 페이지를 읽을 때 `notion-fetch`를 사용한다.

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


# Notion MCP 사용 제한

RandomChat 프로젝트 정보를 확인할 때
Notion의 페이지 검색/읽기 기능만 사용한다.

Notion의 Custom Agent 또는 Session 기능을 사용하지 않는다.

다음 종류의 기능은 RandomChat 프로젝트 확인에 사용하지 않는다.

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

인터넷 및 Notion MCP를 사용할 수 있다면
새 세션 시작 시 Notion의 최신 내용을 확인하고
로컬 캐시를 최신 상태로 만든다.

일반적인 작업마다 Notion을 반복해서 호출하지 않는다.

다음 경우에만 다시 Notion을 확인한다.

- 사용자가 Notion 최신 내용 반영을 요청한 경우
- 기획이 변경되었을 가능성이 있는 경우
- 현재 캐시만으로 요구사항을 판단할 수 없는 경우
- 구현 방향을 결정하기 위해 최신 기획 확인이 필요한 경우

Notion에서 최신 정보를 확인했다면
중요한 내용을 `knowledge/notion/randomchat.md`에 반영한다.


# 오프라인 상태에서 작업

인터넷 또는 Notion MCP를 사용할 수 없어도 작업을 중단하지 않는다.

다음 순서로 로컬 정보를 사용한다.

1. `ProjectRC/README.md`
2. `knowledge/notion/`의 마지막 Notion 캐시
3. `knowledge/docs/`의 관련 개발 문서 캐시
4. 실제 `ProjectRC/` 코드

Notion 캐시가 없다면
`ProjectRC/README.md`와 실제 코드만 사용해서 작업을 계속한다.

인터넷 연결이 없다는 이유만으로
사용자에게 프로젝트 정보를 다시 설명해달라고 요구하지 않는다.

최신 원격 정보가 꼭 필요한 작업이라면
현재 로컬 캐시 기준으로 작업 중이라는 사실만 사용자에게 알려준다.


# 정보별 역할

## 실제 ProjectRC 코드

현재 실제로 무엇이 구현되어 있는지 판단하는 최종 기준이다.

README나 Notion에 구현되었다고 적혀 있어도
실제 코드에 없다면 구현된 것으로 단정하지 않는다.


## ProjectRC/README.md

현재까지 진행된 개발 내역을 빠르게 파악하는 기본 자료다.

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

인터넷이 없거나 Notion MCP를 사용할 수 없을 때 사용한다.


## knowledge/docs/

Context7 등을 통해 확인한
프레임워크, 라이브러리, SDK, API 관련 개발 정보를 저장한다.


# 프로젝트 진행 상황 질문 처리

사용자가 현재 진행 상황을 물으면
최소한 `ProjectRC/README.md`를 실제로 읽은 뒤 답한다.

가능하면 최신 Notion 정보와 실제 코드도 함께 확인한다.

최종 답변은 다음 기준으로 간결하게 정리한다.

- 완료된 작업
- 현재 구현 상태
- 아직 남은 작업
- 다음으로 진행할 작업

사용자에게 이미 존재하는 프로젝트 정보를 다시 요구하지 않는다.


# Context7 및 최신 개발 정보

최신 라이브러리, 프레임워크, SDK, API 정보가 필요한 경우
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
`knowledge/docs/`의 마지막 로컬 캐시와 실제 프로젝트 코드를 사용한다.


# 프로젝트 작업 규칙

작업 전에 기존 구현을 먼저 확인한다.

이미 구현된 기능을 불필요하게 다시 만들지 않는다.

기존 RandomChat 프로젝트의 구조와 코드 스타일을 최대한 유지한다.

기존 구조를 파악하지 않은 상태에서
대규모 수정이나 리팩터링을 수행하지 않는다.

사용자가 요청하지 않은 파일이나 구조를 불필요하게 추가하지 않는다.

확실하지 않은 요구사항을 임의로 추측하지 않는다.

다만 프로젝트 파일이나 Notion에서 확인 가능한 내용이라면
사용자에게 묻기 전에 직접 확인한다.


# 보안

다음 정보는 `knowledge/`, README 또는 Git으로 추적되는 파일에 저장하지 않는다.

- API Key
- Access Token
- Refresh Token
- 비밀번호
- OAuth Client Secret
- 인증 토큰
- `.env`의 비밀값
- 기타 민감한 인증 정보

프로젝트에서 비밀정보를 발견하더라도
다른 문서나 캐시 파일에 복사하지 않는다.


# AI 모델

현재 프로젝트는 Ollama의 로컬 모델을 기본으로 사용한다.

사용자가 명시적으로 요청하지 않는 한
클라우드 AI 모델로 임의 전환하지 않는다.


# Git

프로젝트 실행 BAT 파일에서
가능한 경우 `git pull --ff-only`를 통해 최신 원격 상태를 확인한다.

에이전트 자체는 사용자가 명시적으로 요청하지 않는 한
다음 작업을 하지 않는다.

- `git commit`
- `git push`
- 브랜치 생성
- 브랜치 삭제
- 강제 병합
- 강제 push
- 원격 저장소 설정 변경

원격과 로컬 양쪽에 변경사항이 있어 충돌 가능성이 있다면
임의로 한쪽을 덮어쓰거나 해결하지 말고 사용자에게 알려준다.


# 정보 신뢰 우선순위

현재 구현 여부를 판단할 때 기본 우선순위는 다음과 같다.

1. 실제 `ProjectRC/` 코드
2. `ProjectRC/README.md`
3. 최신 Notion 정보
4. `knowledge/`의 마지막 로컬 캐시
5. 모델 자체 지식

앞으로 구현해야 할 기능과 프로젝트 기획 의도는
최신 Notion 정보를 주요 기준으로 삼는다.
