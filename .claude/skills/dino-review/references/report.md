<a id="report"></a>
# 리뷰 보고서와 마커

로컬 리뷰의 결과는 PR 댓글이 아니라 개발자가 보는 임시 HTML 보고서입니다. 검토 기준은 [리뷰 절차](procedure.md#procedure)가 소유하고, 이 문서는 결과 형식·보고서·마커를 소유합니다. CI는 리뷰를 실행하지 않고 마커만 확인합니다.

## 지적 분류

등급 아이콘·개수 상한을 두지 않습니다. 개발자가 할 일로 나누고 같은 원인은 하나로 합칩니다.

| 할 일 | 기준 |
| --- | --- |
| `수정 필요` | 코드·테스트·문서·노션 절로 확인한 결함, 규칙 위반, 명세 불일치·미구현 |
| `판단 필요` | 확정하지 못한 가설, 개발자가 고를 설계·테스트 선택, 노션에 없는 제품 판단(기획 결정 요청 대상), 노션 버전 밀림 |

| 관점 | 유형 |
| --- | --- |
| 비즈니스 | `명세 불일치` · `명세 미구현` · `명세 없음` · `버전 밀림` · `차이 장부` |
| 기술 | `결함` · `모듈 계약` · `컨벤션` · `다이어그램` · `테스트` · `문서` · `PR 단위` |

근거 없는 지적, 취향, 자동 검사(Spotless·Checkstyle·테스트·PR 규약 검사)가 이미 판정한 항목은 쓰지 않습니다.

<a id="result"></a>
## 결과 파일

`.claude/reviews/<브랜치의 / 를 - 로>-<head7>.json`(gitignore)에 씁니다. [review_report.py](../scripts/review_report.py) `render`가 형식을 검증하고 같은 이름의 `.html`을 만듭니다.

```json
{
  "base": "origin/develop",
  "head": "<리뷰한 커밋의 40자 SHA>",
  "branch": "HM-243/work-detail",
  "kind": "유즈케이스",
  "business": "checked",
  "skip_reason": "business가 skip일 때만: 노션 명세가 없는 PR 종류 등",
  "spec": [{"doc": "[작업] 정책 v1.1 §10", "url": "https://app.notion.com/p/…", "version": "최신"}],
  "trace": [
    {"rule": "다른 조직 작업은 존재를 숨긴다", "source": "[작업] 정책 §10", "code": "src/main/java/…/GetWorkDetailService.java:42",
     "test": "GetWorkDetailServiceTest#hidesWorkOfOtherOrganization", "result": "일치"}
  ],
  "findings": [
    {"view": "기술", "type": "모듈 계약", "action": "수정 필요", "location": "src/main/java/…/Foo.java:12",
     "title": "한 문장 문제", "detail": "발생 조건과 영향", "basis": "규칙 경로#앵커 또는 노션 절 요지", "request": "한 문장 요청"}
  ],
  "reviewed": ["검토한 커밋·파일 범위"],
  "unreviewed": ["확인하지 못한 범위와 이유"]
}
```

- `location`은 `경로:줄` 또는 `경로:시작-끝`이면 보고서에 코드 발췌가 붙습니다. 위치가 없으면 `PR 본문`처럼 적습니다.
- `trace`는 비즈니스 리뷰의 규칙 추적표입니다. `result`는 `일치`·`불일치`·`미구현`이고, `일치`가 아닌 행은 `findings`에도 남깁니다.

## 보고서 확인

`render` 뒤 HTML 경로를 개발자에게 알리고 `수정 필요`·`판단 필요` 건수와 가장 중요한 지적만 대화에 요약합니다. 개발자가 고칠 항목을 고르면 고친 뒤 커밋하고, 바뀐 head로 다시 리뷰합니다. 리뷰 요청만으로 코드를 고치지 않습니다.

<a id="marker"></a>
## 리뷰 마커

리뷰를 마치면 지적이 남아 있어도 마커를 남깁니다. 남은 지적을 받아들일지는 개발자와 PR 리뷰어가 판단합니다.

```text
<!-- dino-review head=<40자 SHA> fix=<수정 필요 수> decide=<판단 필요 수> business=checked|skip -->
```

- `python3 .claude/skills/dino-review/scripts/review_report.py marker <결과.json> --draft <PR 초안>`이 PR 초안의 `## 검증` 절에 넣거나 교체합니다. 리뷰한 head가 현재 `HEAD`와 다르거나 커밋하지 않은 변경이 있으면 거부합니다.
- 이미 게시한 PR은 사용자가 요청하면 초안을 갱신한 뒤 `gh pr edit <번호> --body-file <(python3 .claude/skills/dino-pr/scripts/pr_draft.py body <초안>)`으로 본문을 바꿉니다.
- [PR 규약 검사](../../../../.github/scripts/check_pr_metadata.py)는 Draft가 아닌 PR에서 마커가 있는지와 마커의 `head`가 PR head SHA와 같은지만 판정합니다. 리뷰 뒤에 커밋을 더하거나 base를 병합하면 다시 리뷰해야 통과합니다.
- 마커는 리뷰를 실행했다는 자기 신고입니다. 리뷰 내용의 품질을 보장하지 않으며, 그 판단은 사람 리뷰어가 PR의 핵심 다이어그램과 구현 내용 표로 합니다.
