<a id="writing"></a>
# PR 작성

PR 초안·작성 요청 시 [종류](kinds.md#kinds)·[유형](../../dino-commit/references/change-types.md#change-types)·[라벨](../../dino-issue/references/labels.md#labels)·[단위](planning.md#unit)를 적용합니다. 게시 권한·대상 확인은 [승인 정책](publishing-approvals.md#publishing)을 따릅니다. Claude Code에서는 `dino-pr` 스킬이 이 절차를 수행합니다.

- 제목은 `type(scope): 최종 변경 결과`입니다. scope는 실제 모듈·영역이며 생략할 수 있습니다. Jira 키가 있으면 끝에 `(HM-123)`로 붙입니다. 호환성 파괴는 `!`·`compatibility:breaking` 라벨·전환 설명을 함께 적습니다.
- 대표 라벨은 제목 유형과 일치시킵니다(`fix(user)` → `type:fix`). 제목 유형은 [PR 종류가 허용하는 type](kinds.md#kinds) 중에서 고릅니다.
- [PR 양식](../../../../.github/pull_request_template.md) 순서를 따르고 안내 주석을 제거합니다. 절별 내용은 [종류별 작성 기준](kinds.md#절별-작성-기준)을 따릅니다. 관련 이슈가 없으면 `없음`으로 적고 형식 때문에 이슈를 만들지 않습니다.
- 구현·필수 검증이 미완료면 Draft로 게시하고 남은 내용을 본문에 표시합니다.
- 본문은 리뷰어가 다이어그램과 표만으로 몇 분 안에 판단할 분량으로 씁니다. 코드에서 바로 보이는 내용(필드 목록, 변경 파일 설명)을 반복하지 않습니다.

## 검증 절

- 대상 head SHA·실제 명령·정상/거부/경계 조건·결과를 적고 mock/실제 통합 검사를 구분합니다. 집중 검사·최종 CI 통과를 모든 중간 커밋의 통과로 쓰지 않습니다. 필수 검사와 문서 예외는 [루트 검증](../../../../AGENTS.md#검증과-완료)을 따릅니다.
- 구현 내용 표의 근거 테스트는 실제로 실행해 통과한 것만 적습니다.
- 마지막 줄에 `base…head · 파일 N · +A/−D · 커밋 N`을 한 줄로 적습니다. 통계는 품질 점수가 아닙니다.
- 그 아래에 PR head로 로컬 리뷰를 마쳤다는 [리뷰 마커](../../dino-review/references/report.md#marker)를 둡니다. 스크립트가 넣으며 손으로 쓰지 않습니다. 리뷰 뒤에 커밋을 더하면 다시 리뷰해 마커를 갱신합니다.

## 연결

- stacked PR은 리뷰 포인트에 선행 PR·이번 차이·존재하는 후속 PR을 연결합니다.
- 모든 이슈 완료 조건을 해결하면 `Closes #123`, 부분 작업·참고는 `Refs #123`을 씁니다. 번호의 실제 존재·관계를 확인합니다. 자동 종료는 기본 브랜치 대상 PR의 병합에 적용되며 다른 base에서는 보장하지 않습니다. [GitHub 이슈 연결](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)
