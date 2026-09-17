# GitHub 초기 설정

새 저장소에 이슈·PR 양식과 라벨을 적용할 때 읽습니다. 일상 작업은 [GitHub 작업 가이드](../conventions/workflow/README.md#workflow)에서 선택합니다.

## 구성과 선택 이유

GitHub는 작고 목적이 분명한 PR, 변경 배경과 결과, 작성자의 사전 diff 검토를 권장합니다. 이를 기준으로 이슈는 요구와 완료 조건, PR은 실제 변경과 검증 근거를 담습니다. [GitHub PR 권장사항](https://docs.github.com/en/pull-requests/concepts/helping-others-review-your-changes)

작업 유형은 11개로 구분하고, 이슈는 필요한 정보에 따라 6종의 YAML Form을 사용합니다. PR은 자동으로 채워지는 공통 Markdown 양식 하나를 사용하고 유형별 설명을 달리합니다. 여러 PR 양식은 `template` 쿼리로 선택해야 하므로 이 템플릿에서는 선택 절차와 공통 항목 중복을 줄입니다. [이슈 양식 문법](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms), [PR 양식 설정](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/creating-a-pull-request-template-for-your-repository)

분류 어휘는 Conventional Commits를 참고합니다. 규격이 의미를 정한 `feat`·`fix` 외의 유형은 프로젝트에서 선택할 수 있으며, `investigation`은 조사·논의를 위한 자체 유형입니다. 작업 유형의 수와 입력 양식의 수를 일치시킬 필요는 없습니다. [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)

입력 필드 원본은 [이슈 양식](../../.github/ISSUE_TEMPLATE/)과 [PR 양식](../../.github/pull_request_template.md), 라벨 이름·색상·설명 원본은 [labels.json](../../.github/labels.json)입니다. 양식을 바꿀 때는 [유형 선택](../conventions/workflow/change-types.md#change-types)·[양식 선택 기준](../conventions/workflow/issues/forms.md#forms)도 함께 맞춥니다. 조직별 Issue Type·Project·담당자는 미리 지정하지 않아 개인·조직 저장소에서 재사용할 수 있습니다.

## 새 저장소에 적용

1. `.github` 파일을 새 저장소에 포함하고 Issues 기능을 활성화합니다. 이슈·PR 양식은 기본 브랜치에 반영되어야 표시됩니다. [이슈 양식 설정](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository), [PR 양식 설정](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/creating-a-pull-request-template-for-your-repository)
2. 아래 명령으로 라벨을 생성·갱신합니다. JSON 파일 자체는 GitHub가 자동으로 가져오지 않으며, Form의 자동 라벨은 해당 저장소에 라벨이 먼저 있어야 적용됩니다. [이슈 Form 라벨](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms)
3. 새 이슈 화면의 6개 양식과 PR 본문 자동 입력을 확인합니다. 공용 양식은 작업 유형 선택과 분류 시 라벨 지정을 확인합니다. [config.yml](../../.github/ISSUE_TEMPLATE/config.yml)은 일반 기여자의 빈 이슈 선택을 숨깁니다. 쓰기 권한 사용자의 빈 이슈나 CLI/API까지 차단하는 검증 장치는 아닙니다. [선택 화면 설정](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository)

저장소 루트에서 GitHub CLI 인증과 Python 3을 준비한 뒤 `OWNER/REPO`를 대상 저장소로 바꿔 실행합니다. 대상 저장소의 쓰기 권한이 필요합니다. 같은 이름의 색상·설명만 정의에 맞춰 갱신하고 다른 라벨은 삭제하지 않으므로 반복 적용할 수 있습니다. 실패하면 원인을 해결한 뒤 다시 실행합니다. [라벨 권한](https://docs.github.com/en/issues/using-labels-and-milestones-to-track-work/managing-labels), [gh label create](https://cli.github.com/manual/gh_label_create)

```bash
gh auth status
GH_REPO='OWNER/REPO' python3 - <<'PY'
import json
import os
import subprocess
from pathlib import Path

repo = os.environ["GH_REPO"]
if repo == "OWNER/REPO":
    raise SystemExit("GH_REPO를 실제 대상 저장소로 바꿔 주세요.")

labels = json.loads(Path(".github/labels.json").read_text(encoding="utf-8"))
for label in labels:
    subprocess.run([
        "gh", "label", "create", label["name"], "--repo", repo,
        "--color", label["color"], "--description", label["description"], "--force",
    ], check=True)
PY
```

새 모듈 이름마다 라벨을 미리 늘리지 않습니다. 실제 분류에 필요할 때 정의 파일·양식·[라벨 운영](../conventions/workflow/labels.md#labels)를 함께 갱신하고 대상 저장소에 다시 적용합니다.
