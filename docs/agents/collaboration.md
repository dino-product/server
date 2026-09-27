# Codex 협업 설정

역할·모델·로딩·인계 방식을 추가하거나 점검할 때 읽습니다. 일상적인 호출 조건은 [AGENTS.md의 Delegation](../../AGENTS.md#delegation)이 원본이며 이 문서를 매번 선행 조회하지 않습니다.

## 설정과 모델

2026-09-28에 공식 [Subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents), [Models](https://learn.chatgpt.com/docs/models), [설정 우선순위](https://learn.chatgpt.com/docs/config-file/config-basic#configuration-precedence), [설정 참조](https://learn.chatgpt.com/docs/config-file/config-reference)를 확인했습니다. 공식 가이드는 좁은 역할·요약 인계·읽기 중심 병렬 작업을 권하고, 하위 작업도 토큰을 소비한다고 설명합니다. [Astra 프롬프트 운영 조언](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra)의 조건부 문서 조회도 적용합니다.

| 설정 위치 | 프로젝트의 선택 |
| --- | --- |
| [`.codex/config.toml`](../../.codex/config.toml) | `[agents]`에서 위임 활성화, 하위 기본 `gpt-6-sol` / `medium`, 동시 하위 스레드 최대 3개. 메인 세션의 모델·effort는 사용자 선택 유지 |
| [`.codex/agents/`](../../.codex/agents/) | 역할별 `name`, `description`, `developer_instructions`, `model`, `model_reasoning_effort` 지정. 독립 TOML을 자동 발견하므로 config에 중복 등록하지 않음 |
| [`.codex/rules/`](../../.codex/rules/) | 기존 명령 승인 보조 정책. 적용 범위·제약은 [실행 정책](execution-policy.md)이 소유 |

프로젝트 `.codex/` 계층은 신뢰한 프로젝트에서 로딩됩니다. 신뢰·승인·MCP·개인 설정을 이 구성 때문에 변경하지 않습니다. 현재 클라이언트의 역할 목록·유효 설정을 확인하고, 미로딩이면 그 사실을 보고합니다. 새 파일·변경한 역할은 **새 세션에서 확인**하며 이미 실행 중인 세션에 자동 반영됐다고 가정하지 않습니다.

공식 우선순위상 하위 모델·effort는 명시한 spawn 값 → `[agents]` 기본값 → 부모 값으로 먼저 결정되고, 선택한 역할 TOML의 명시 값이 최종적으로 우선합니다. 모델만 지정하면 기존 effort가 남을 수 있어 각 역할에 두 값을 함께 고정했습니다. 프로젝트 `explorer`는 같은 이름의 내장 역할보다 우선합니다. 역할이 없는 일반 작업도 Sol 기본값을 사용해 Astra 상속을 줄입니다.

## 역할 선택과 인계

| 역할 | 모델 / effort | 책임과 결과 |
| --- | --- | --- |
| [`explorer`](../../.codex/agents/explorer.toml) | `gpt-6-luna` / `high` | 경로·심볼·호출부와 적용 지침을 좁혀 보고. 수정·검사 실행 없음 |
| [`implementer`](../../.codex/agents/implementer.toml) | `gpt-6-sol` / `medium` | 지정한 코드·테스트·필수 계약 문서 수정. 검사·포맷 실행은 부모를 통해 verifier에 인계 |
| [`test_verifier`](../../.codex/agents/test_verifier.toml) | `gpt-6-luna` / `high` | 집중·전체 검사와 포맷 실행, 대상 상태·종료 코드·검사 수·건너뛰기·실패 요약. 수동 소스 수정 없음 |
| [`module_reviewer`](../../.codex/agents/module_reviewer.toml) | `gpt-6-sol` / `high` | 변경 후 모듈 책임·공개 계약·계층 의존성과 소비자 영향의 독립 검토 |
| [`verification_reviewer`](../../.codex/agents/verification_reviewer.toml) | `gpt-6-sol` / `high` | 테스트·빌드·CI의 검증 설계와 미검증 요구사항 검토. 검사 실행 없음 |

Luna/high와 Sol/medium은 공식 Subagents의 시작 권고를 채택했습니다. 검토자는 의존성·예외 상황을 추적하므로 Sol/high를 선택했습니다. 이는 이 프로젝트의 초기 운영값이며 측정으로 입증된 최적값이나 절감률은 아닙니다. 실패·계약 모호성은 근거와 함께 주 에이전트로 올리고, 같은 작업을 여러 모델로 반복 배정하지 않습니다. 역할 TOML의 고정값이 우선하므로 일회성 spawn 모델 지정만으로 그 역할이 승격됐다고 가정하지 않습니다.

`explorer`와 두 검토자는 `sandbox_mode = "read-only"`를 지정합니다. 나머지는 부모 권한을 상속합니다. 부모의 런타임 권한 변경이 역할 기본값보다 우선할 수 있어 읽기 전용 역할에는 수정 금지 지시도 둡니다. 테스트·빌드가 만드는 `build/`와 Gradle 캐시, Spotless의 소스 포맷은 쓰기가 필요합니다. verifier가 승인·sandbox 제한을 우회하지 않으며 DB 생성·삭제 검사는 승인된 범위를 인계받은 뒤 실행합니다.

주 에이전트가 작업 단위·승인·커밋을 소유하고, 하위 에이전트에는 다음처럼 필요한 정보만 전달합니다.

```text
목적 / 기대 행동:
담당 역할 / 소유 파일 / 수정 금지 범위:
기준: base/head SHA 또는 현재 diff와 변경 파일
적용 규칙: 필요한 경로#앵커 + 확인한 적용 내용 / 아직 확인할 하위 지침
완료 조건 / 요청한 검사 / 승인된 DB 실행 범위:
기존 검사: 명령·옵션·대상 상태·결과 / 미검증 범위
반환: 변경·발견 요약, 파일:줄 또는 심볼, 실제 검사 결과, 남은 문제
다른 작업자가 함께 작업 중이므로 타인 변경을 보존하고 재위임하지 말 것.
```

- 원본 문서·전체 대화·전체 로그 대신 결정·근거와 필요한 규칙을 인계합니다. 원본이 바뀌지 않은 충분한 적용 내용은 재사용하고, 미확인·충돌·범위 확대 때 해당 원본을 읽습니다. [읽기 경로](../../AGENTS.md#읽기-경로)의 문서 소유권을 역할 프롬프트에 복제하지 않습니다.
- 수정자는 필요한 재현 테스트를 작성하고 부모가 verifier에 기대 실패 확인을 맡긴 뒤 구현을 진행합니다. 집중 검사·필요한 전체 검사는 같은 verifier에 이어 맡기고, 실행 중 파일 변경·다른 Gradle 실행을 겹치지 않습니다. 포맷 후 변경된 상태를 다음 검사·리뷰의 기준으로 전달합니다.
- 검토자는 구현에 참여하지 않은 별도 에이전트로 시작합니다. 검토 중 변경이 생기면 영향 범위만 다시 인계합니다. [검사 근거](../conventions/testing/evidence.md#evidence)에 맞는 결과는 재사용하며 같은 자동 판정을 중복 위임하지 않습니다.
- 동시 3개는 독립 탐색·검토의 상한이며 항상 3개를 띄우라는 뜻이 아닙니다. 같은 목적의 후속 작업은 기존 스레드에서 이어가고, 클라이언트가 지원하면 끝난 스레드를 닫아 슬롯을 반환합니다. 하위 에이전트의 재위임은 금지합니다.
- 역할·모델이 없으면 적용 제한을 보고하고 사용 가능한 동등 역할이나 주 에이전트가 이어갑니다. 독립 검토가 불가능하면 해당 검토가 미완료임을 남깁니다. 도구 권한·필수 전체 검사·DB 승인을 비용 절감을 이유로 생략하지 않습니다.

## 적용 확인과 비용 비교

1. 설치된 CLI와 실제 카탈로그에서 모델·effort 지원을 확인합니다. `codex --version`, `codex debug models --help`로 지원 명령을 확인합니다. 번들 카탈로그의 지원 목록은 계정의 실제 호출 성공을 보장하지 않습니다.
2. TOML 구문·역할 이름·모델·effort와 프로젝트 설정의 로딩을 검사합니다. 새 입력 구성은 [문서 탐색 점검](context.md#이-저장소에서-확인할-것)으로, 실제 spawned 역할의 모델·effort는 새 세션의 하위 스레드 정보로 확인합니다. 파일이 있다는 사실만으로 런타임 적용 완료로 보고하지 않습니다.
3. 같은 요구사항·완료 조건에서 메인/하위의 입력·캐시·출력·추론 토큰, 모델별 사용량과 재작업을 함께 비교합니다. 긴 로그를 메인에 되붙이지 않습니다. 모델 단가·Codex 크레딧·전체 토큰은 다른 지표이므로 가벼운 모델 사용을 총 토큰 절감으로 단정하지 않습니다.

이 설정은 로컬 역할 위임을 다룹니다. PR 자동 리뷰·규약 검사는 기존 [GitHub Actions 운영](../conventions/workflow/review/automation.md#automation)이 소유하며 별도 에이전트 hooks나 저장소 스킬을 추가하지 않습니다.
