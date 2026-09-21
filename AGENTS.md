# AGENTS.md

## General Rules for Agents

1. 사용자에게 하는 모든 설명과 응답은 반드시 한국어로 한다.
2. 작업 전에 `knowledge/` 폴더의 관련 로컬 문서를 먼저 확인한다.
3. Notion MCP를 사용할 수 있으면 RandomChat 관련 페이지를 찾아 최신 내용을 `knowledge/notion/` 아래 Markdown으로 저장하거나 갱신한다.
4. Notion MCP를 사용할 수 없으면 `knowledge/notion/`의 마지막 로컬 캐시를 사용한다.
5. 최신 라이브러리나 프레임워크 정보가 필요하면 Context7 MCP를 우선 사용한다.
6. Context7에서 확인한 중요한 내용은 `knowledge/docs/` 아래 Markdown으로 저장한다.
7. Context7를 사용할 수 없으면 `knowledge/docs/`의 마지막 캐시를 사용한다.
8. 원격과 로컬 양쪽이 변경되어 충돌 가능성이 있으면 임의로 덮어쓰지 말고 사용자에게 알린다.
9. API Key, 비밀번호, 인증 토큰, .env 비밀값은 `knowledge/`에 저장하지 않는다.
10. 사용자가 요청하지 않는 한 클라우드 AI 모델을 사용하지 않는다.
11. Git commit이나 push는 사용자가 요청했을 때만 한다.
