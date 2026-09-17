<a id="forms"></a>
# 이슈 양식

선택한 [작업 유형](../change-types.md#change-types)에 맞는 양식을 사용합니다. 유형 라벨은 `type:<유형>`, 이름·색상·설명의 원본은 [labels.json](../../../../.github/labels.json)입니다.

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
