# 에이전트 문서 탐색

Codex의 자동 로딩·입력 크기를 점검할 때만 읽습니다. 작업별 계약은 [읽기 경로](../../AGENTS.md#읽기-경로), 역할 절차는 [프로젝트 역할](../../.codex/agents/)이 소유합니다.

## Codex의 공식 동작

- 시작 시 전역 `CODEX_HOME`(기본 `~/.codex`)과 저장소 루트부터 작업 디렉터리까지의 지침 체인을 구성합니다. 프로젝트를 찾지 못하면 현재 디렉터리만 확인합니다.
- 디렉터리마다 비어 있지 않은 `AGENTS.override.md` → `AGENTS.md` → 설정된 fallback 중 하나를 선택합니다. 가까운 지침이 우선하며 프로젝트 합산 한도는 `project_doc_max_bytes`(기본 32 KiB)입니다. 같은 경로에 파일을 나눠도 합산 크기는 줄지 않습니다.
- 연결된 Markdown 본문과 시작 디렉터리 아래의 모든 지침이 자동 로딩되는 것은 아닙니다. 대상 모듈·테스트 지침은 작업 담당자가 확인합니다. 테스트 경로에서도 상위가 아닌 소스 모듈 지침을 별도로 적용합니다.
- 로컬 스킬은 현재 디렉터리부터 저장소 루트까지의 `.agents/skills`에서 발견합니다. 처음에는 이름·설명을, 선택 후에는 본문·필요한 참조를 읽습니다. 참고 계약을 일괄 스킬로 옮기지 않으며 저장소에는 별도 스킬을 두지 않습니다.

근거: [AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md)·[Skills](https://learn.chatgpt.com/docs/build-skills) 공식 문서. 설치 버전과 실제 설정을 함께 확인합니다.

## 이 저장소에서 확인할 것

`CLAUDE.md`·`GEMINI.md`·Copilot 지침은 공통 원본을 가리키는 포인터이며 Codex의 기본 탐색 파일로 가정하지 않습니다. 역할·모델 설정은 [협업 설정](collaboration.md)이 소유합니다.

지침 변경 뒤 `codex --version`, `codex debug prompt-input --help`로 지원을 확인한 다음 `codex --cd . debug prompt-input`과 `codex --cd src/test/java/com/orbit debug prompt-input`의 출처·범위를 대조합니다. 이 명령은 모델 호출 없이 새 입력을 구성하며 실행 중 세션을 다시 로딩하지 않습니다. 미지원 클라이언트는 새 세션의 실제 입력·로그로 확인하고, 파일 존재나 모델의 추정을 자동 로딩 증거로 쓰지 않습니다.

개인 스킬·플러그인 목록과 프로젝트 문서는 별도 입력입니다. 사용하지 않는 목록도 포함될 수 있지만 프로젝트 작업을 이유로 개인 설정을 임의 변경하지 않습니다. 반복 누락·출력 잘림·효과 측정은 [진단 기준](../troubleshooting/agent-context-verification.md)을 따릅니다.
