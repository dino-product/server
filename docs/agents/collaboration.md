# Codex 협업 설정

협업 설정을 추가·변경하거나 독립 검토를 위임할 때 읽습니다. 공식 [프로젝트 설정](https://learn.chatgpt.com/docs/config-file/config-basic), [Subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents), [Rules](https://learn.chatgpt.com/docs/agent-configuration/rules), [Hooks](https://learn.chatgpt.com/docs/hooks)를 2026-09-12에 확인했습니다.

| 파일·기능 | 역할과 로딩 조건 | 이 저장소의 적용 |
| --- | --- | --- |
| `.codex/agents/*.toml` | 선택한 하위 에이전트의 역할 설정. 필수 값은 `name`, `description`, `developer_instructions` | 아래 검토 역할 2개. 모델·추론 강도·권한 설정은 부모 상속 |
| `.codex/config.toml` | 신뢰한 프로젝트의 설정 계층. CLI 옵션이 우선하고, 프로젝트 내부에서는 작업 디렉터리에 가까운 설정이 우선 | 역할 외에 공통으로 덮어쓸 설정이 없어 추가하지 않음. 역할 파일을 별도로 중복 등록하지 않음 |
| `.codex/rules/*.rules` | 신뢰한 프로젝트 계층에서 시작 시 읽는 실험적 명령 실행 정책. `prefix_rule`로 `allow`·`prompt`·`forbidden` 결정 | [프로젝트 실행 규칙](execution-policy.md) 배치. 승인 경계는 AGENTS, [커밋](../conventions/workflow/commits.md#commits)·[승인 요약](../conventions/workflow/approvals/README.md#approvals)이 원본 |
| `.agents/skills/*/SKILL.md` | 특정 반복 작업의 선택형 절차. 메타데이터로 발견한 뒤 본문 로딩 | 현재 리뷰 절차는 기존 가이드와 역할 설정으로 충분하여 별도 스킬 없음 |
| `.codex/hooks.json` | 신뢰한 프로젝트의 수명주기 이벤트에 연결하는 자동 동작 | 반복 전체 검사나 자동 외부 작업을 추가할 근거가 없어 사용하지 않음 |

프로젝트 `.codex/` 계층은 신뢰 상태의 영향을 받습니다. 신뢰되지 않아 설정이 로딩되지 않는 경우 임의로 사용자 신뢰·승인 설정을 바꾸지 않고, 기존 지침으로 작업하고 미적용 사실을 알립니다. 현재 클라이언트의 역할 목록에서 아래 이름이 보이는지 확인합니다. 사용자·프로젝트·관리자 설정의 우선순위와 권한은 공식 가이드에 따릅니다.

## 역할 선택과 인계

현재 역할 목록에서 아래 역할을 사용할 수 있는지 확인합니다. 역할이 없거나 설정 적용을 점검할 때는 [설정·로딩 조건](#codex-협업-설정)을 확인합니다.

| 역할 | 맡길 범위 | 원본 설정 |
| --- | --- | --- |
| `module_reviewer` | 모듈 책임·공개 계약·계층 의존성의 실제 변경과 소비자 영향 | [모듈 검토자](../../.codex/agents/module_reviewer.toml) |
| `verification_reviewer` | 테스트가 변경을 검출하는지, 린트·CI와 실행 보고에 빈틈이 있는지 | [검증 검토자](../../.codex/agents/verification_reviewer.toml) |

두 역할은 검토 업무를 위한 지시문이며, 파일 쓰기를 막는 별도 sandbox 설정은 아닙니다. 실제 권한은 부모 세션을 상속합니다. 기본 `worker`·`explorer` 역할과 함께 선택적으로 사용하고, 단순 수정에 병렬 실행을 강제하지 않습니다.

주 에이전트는 [기존 리뷰 기준](../conventions/workflow/review/procedure.md#procedure)에 따라 같은 변경 기준과 요구사항, 담당 파일·diff 범위, 기대 결과를 전달합니다. 커밋 리뷰는 base/head SHA를, 미커밋 변경은 검토 시작 시 diff 범위를 명시합니다. 검토 중 변경이 생기면 영향받는 범위를 다시 확인합니다. 수정 위임이 필요한 작업은 파일 소유권을 나누고 공유 파일의 동시 수정을 피합니다. 하위 결과를 수집한 뒤 중복 지적·계약 연결·최종 검증은 주 에이전트가 확인합니다.

[검사 근거의 재사용 기준](../conventions/testing/evidence.md#evidence)에 따라 완료한 검사·대상 상태·미검증 범위를 함께 전달하고, 자동 검사가 이미 판정한 동일 조건의 재확인만을 위해 검토를 위임하지 않습니다.

호출 예시:

```text
현재 미커밋 변경을 검토해줘. module_reviewer에게 모듈 계약과 소비자 영향을,
verification_reviewer에게 테스트·CI 검증 공백을 맡겨줘.
두 에이전트에는 각각 담당 diff 범위를 전달하고 파일은 수정하지 않게 해줘.
결과를 모아 파일 위치·영향·근거·미확인 사항을 정리해줘.
```
