<a id="forms"></a>
# 이슈 양식

선택한 [작업 유형](../../dino-commit/references/change-types.md#change-types)에 맞는 양식을 사용합니다. 유형 라벨은 `type:<유형>`, 이름·색상·설명의 원본은 [labels.json](../../../../.github/labels.json)입니다.

| 유형 | 양식 |
| --- | --- |
| `feat` | [기능](../../../../.github/ISSUE_TEMPLATE/02-feature.yml) |
| `fix` | [버그](../../../../.github/ISSUE_TEMPLATE/01-bug.yml) |
| `docs` | [문서](../../../../.github/ISSUE_TEMPLATE/04-docs.yml) |
| `refactor`, `perf` | [구조/성능 개선](../../../../.github/ISSUE_TEMPLATE/03-improvement.yml) |
| `test`, `build`, `ci`, `chore`, `revert` | [일반 작업](../../../../.github/ISSUE_TEMPLATE/05-task.yml) |
| `investigation` | [조사](../../../../.github/ISSUE_TEMPLATE/06-investigation.yml) |

구조/성능 개선·일반 작업 Form은 `작업 유형` 선택이 필수이며 처음에는 `status:needs-triage`만 붙입니다. 선택값이 라벨로 자동 변환되지 않으므로 라벨 변경 권한이 있는 작성자·분류 담당자가 정확한 `type:*` 하나를 붙입니다. 다른 네 Form은 고정 유형을 기본 지정합니다.

CLI/API 작성은 웹 Form과 별개입니다. 입력 필드의 `label`을 Markdown 소제목으로 쓰고 필수 내용·공용 Form의 선택 유형·`perf`/`revert` 추가 입력을 채웁니다. `markdown` 안내·빈 선택 필드는 제외하고 기본 라벨과 정확한 유형 라벨 하나를 직접 전달합니다. 웹의 필수 입력·기본 라벨이 자동 적용된다고 가정하지 않습니다.

<a id="writing"></a>
## 이슈 작성

이슈 제목은 대상·문제 또는 원하는 결과를 한 문장으로 적습니다. 유형은 라벨로 구분하고 제목 접두사를 강제하지 않습니다. 작성 전 관련 이슈를 확인해 같은 문제면 기존 이슈에 근거를 보탭니다. 접근 실패 시 중복 확인 완료로 보고하지 않습니다.

- 관찰 사실·원인 가설을 구분하고 완료 조건은 결과로 확인할 수 있게 씁니다. 조사는 답할 질문·종료 조건을 둡니다.
- `perf`는 기준선·측정 조건, `revert`는 대상 PR/커밋·복구 조건을 적습니다. 공용 Form은 유형별 필수를 강제하지 않으므로 분류 때 확인합니다. 미측정이면 수치 대신 그 사실·계획을 적습니다.
- 필수 항목을 채우고 무관한 선택 항목은 비웁니다. 모르는 사실은 `미확인`으로 표시합니다. 로그·요청·화면은 필요한 부분만 첨부하고 실제 비밀값·개인정보를 제거합니다.

조사 결론은 이슈에, 확정된 구조·정책은 관련 컨벤션·해당 ADR에 반영합니다. 독립 주제 ADR은 [문서 관리](../../../../docs/maintenance.md#maintenance)를 따르고 [ADR 색인](../../../../docs/adr/README.md)도 맞춥니다. 코드·상시 문서 변경이 없으면 PR 없이 이슈에서 마칩니다.
