<a id="labels"></a>
# 라벨 운영

이슈·PR 공통 조합 규칙입니다. 개별 이름·색상·설명은 [labels.json](../../../.github/labels.json)이 원본입니다.

| 라벨 | 적용 |
| --- | --- |
| `type:*` | 분류 완료 이슈·PR의 대표 목적 1개. 고정 Form은 기본 지정하며 공용 Form·PR·CLI/API는 선택 유형을 직접 지정 |
| `area:*` | 실제 영향 영역만 필요한 만큼. 문서만 바꾸면 `area:docs`, CI 설정은 `area:build` 등 |
| `priority:*` | 담당자가 영향·긴급성 판단 후 최대 1개. 미분류와 낮은 우선순위를 구분하고 PR에 기계적으로 복사하지 않음 |
| `status:needs-triage` | 신규 이슈만 기본 지정. 문제·중복·범위·유형별 입력을 확인하고 대표 유형을 지정한 사람이 제거 |
| `status:blocked` | 막는 이슈·결정과 해제 조건을 본문/댓글에 연결하고 지정. 해소되면 제거 |
| `compatibility:breaking` | 기존 소비자 대응이 필요할 때 지정하고 본문에 영향·전환 방법 설명 |

진행·리뷰·완료는 Issue/PR 상태와 사용하는 Project로 관리하며 같은 의미의 상태 라벨을 추가하지 않습니다. 기존 GitHub 기본 라벨은 삭제할 필요가 없지만 동일 의미의 라벨을 중복 적용하지 않습니다.

<a id="저장소-적용"></a>
## 저장소 적용

GitHub는 `labels.json`을 자동으로 읽지 않으므로 아래 명령으로 라벨을 생성·갱신합니다. 같은 이름은 색상·설명만 갱신하고 다른 라벨은 삭제하지 않아 반복 실행할 수 있습니다. 저장소 쓰기 권한, GitHub CLI 인증, Python 3이 필요합니다. [PR 규약 검사](../../../.github/workflows/pr-conventions.yml)가 `type:*` 라벨을 요구하므로 라벨이 없는 저장소에서는 먼저 적용합니다. [gh label create](https://cli.github.com/manual/gh_label_create)

```bash
gh auth status
GH_REPO='dino-product/server' python3 - <<'PY'
import json
import os
import subprocess
from pathlib import Path

repo = os.environ["GH_REPO"]
labels = json.loads(Path(".github/labels.json").read_text(encoding="utf-8"))
for label in labels:
    subprocess.run([
        "gh", "label", "create", label["name"], "--repo", repo,
        "--color", label["color"], "--description", label["description"], "--force",
    ], check=True)
PY
```

새 모듈마다 라벨을 미리 늘리지 않습니다. 실제 분류에 필요할 때 정의 파일·양식·이 문서를 함께 갱신하고 다시 적용합니다.
