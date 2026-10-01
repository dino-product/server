# ADR-002: 에이전틱 코딩 규칙

- 상태: Accepted
- 기준일: 2026-10-01
- 범위: 지침·스킬·역할의 소유권, PR 단위, 변경·검증·승인·협업. 기술 결정은 [ADR-001](001-backend-architecture.md), 기획 원본은 [ADR-003](003-planning-source-of-truth.md)이 소유합니다.

## 배경과 목적

모듈리스·헥사고널·DDD로 추상화 수준이 높아 PR마다 코드가 많고, 사람이 PR 코드 전체를 읽는 리뷰가 불가능해졌습니다. 규칙을 문서로만 두면 개발자와 에이전트가 매번 찾아 읽어야 하고, 작업마다 기능 설명 문서를 갱신하는 비용도 쌓입니다. 팀은 Claude Code를 공통 도구로 쓰기로 했습니다. 목표는 세 가지입니다. 규칙이 작업 시점에 자동 적용되고, 사람은 다이어그램으로 PR을 판단하며, 레포는 코드 기준 문서만 유지하는 것입니다.

## 결정

- **소유권:** `AGENTS.md`는 Claude Code와 Codex가 함께 읽는 공통 제약·도구별 진입점입니다. 여러 역할·사람·CI가 쓰는 공통 계약은 사용 시점별 Claude 스킬의 `.claude/skills/*/references/`에 한 번만 둡니다. `SKILL.md`는 절차와 진입점만, `.codex/agents/`는 Codex 역할 절차만 소유합니다. 문서 통합 시 이전 본문·읽기 지시를 제거하고 실제 소비자의 참조를 갱신합니다.
- **자동 적용과 강제:** 스킬은 설명에 적힌 작업 시점에 모델 판단으로 트리거되므로 보조 수단입니다. 반드시 지켜야 하는 조건은 결정적 수단으로 강제합니다. 승인 대상 명령은 `.claude/settings.json`의 확인·차단 규칙, 커밋 제목은 PreToolUse hook, PR 양식·종류·다이어그램 존재는 CI 규약 검사가 맡습니다. 모듈 경계 변경의 독립 검토는 서브에이전트(`module-reviewer`)로 유지합니다.
- **기획 원본:** 노션을 원본으로 두고 계획·PR에는 링크와 절만 첨부합니다. 원본 범위·동기화·차이 관리·기획 결정 요청은 [ADR-003](003-planning-source-of-truth.md)이 소유합니다.
- **PR 단위:** 줄 수 대신 [PR 종류](../../.claude/skills/dino-pr/references/kinds.md#kinds)(유즈케이스·모듈 연동·호환성 변경·구조 변경·결함 수정·유지보수)로 나누고, 한 PR에는 한 종류만 둡니다. 사람은 종류별 핵심 다이어그램과 규칙↔테스트 표로 판단하며, 리뷰는 그 둘이 코드와 일치하는지를 가장 먼저 확인합니다. 유즈케이스는 수직 슬라이스로 완결하고, 리팩터링과 호환성 변경은 별도 PR로 나눕니다. 근거는 Google Small CLs, Vertical Slice, Parallel Change입니다.
- **변경 단위:** 한 이유의 변경을 검사한 직후 커밋해 검증 상태와 이력을 일치시킵니다. 중간 상태의 실행 가능성·집중 검사와 최종 전체 검증을 구분합니다. 기준은 [커밋 계약](../../.claude/skills/dino-commit/references/commits.md#checkpoints)과 [PR 계획](../../.claude/skills/dino-pr/references/planning.md#planning)에 있습니다.
- **검증:** 기계적 판정은 실제 검사 결과를 재사용하고, 요구사항·계약 의미·검증 설계는 별도로 판단합니다. 기준은 [검사 보장 범위](../../.claude/skills/dino-testing/references/verification.md#evidence)와 [전체 검증](../../AGENTS.md#검증과-완료)입니다. 문서 정리를 이유로 백엔드·CI 검사를 약화하지 않습니다.
- <a id="승인과-외부-작업"></a> **승인:** 자율 완료는 데이터·Git 이력·외부 환경을 바꿀 권한을 늘리지 않습니다. 로컬·테스트 DB의 생성·삭제도 사전 승인 대상입니다. 허용 범위와 추가 확인 대상은 [작업 원칙](../../AGENTS.md#작업-원칙)·[실행 전 승인](../../AGENTS.md#approvals)이 소유합니다. 권한 규칙(`.claude/settings.json`, `.codex/rules`)은 명령 접두사 기반의 보조 정책이라 SQL·프로필·의도를 판별하지 못합니다.
- **외부 자동화:** PR 자동 리뷰는 로컬 에이전트와 분리된 [CI 계약](../../.claude/skills/dino-review/references/automation.md#automation)을 유지합니다. 명시 위임한 요약·인라인 게시만 허용하며 승인·변경 요청·수정·병합은 포함하지 않습니다. 승인 불가 모드의 충돌은 [실행 정책](../agents/execution-policy.md)에 따라 보고하고 우회하지 않습니다.
- **현재판:** 상세 규칙을 ADR에 반복 전재하지 않습니다. [문서 관리](../agents/documents/maintenance.md#maintenance)에 따라 현재 선택과 이유만 유지하고 이전 결정은 Git 이력에 보존합니다.

## 영향과 검증

- 컨벤션 문서 34개가 스킬 references 17개로 합쳐졌고, 노션과 중복된 기획 초안과 `CLAUDE.md` 포인터는 삭제했습니다. 팀원은 레포를 pull하면 같은 스킬·권한·hook을 받습니다. 개인 설정은 `.claude/settings.local.json`에 둡니다.
- 스킬 트리거는 보장이 아니므로 새 세션의 실제 호출 로그로 확인합니다. 강제 조건은 hook·권한·CI 회귀 테스트(`.github/scripts/tests`)로 확인합니다. 파일 크기 감소만으로 실행 토큰·비용 절감을 주장하지 않으며, 실제 비교는 [진단 기준](../troubleshooting/agent-context-verification.md#개선-효과를-비교할-때)을 따릅니다.
- Codex는 `.claude/skills`를 스킬로 발견하지 않고 [읽기 경로](../../AGENTS.md#읽기-경로) 표로 같은 references에 진입합니다.
