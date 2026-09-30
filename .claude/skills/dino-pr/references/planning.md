<a id="planning"></a>
# PR 계획

PR은 사람이 코드 전체를 읽지 않고 [핵심 다이어그램과 구현 내용 표](kinds.md#kinds)로 수용 여부를 판단하는 단위입니다. 기능 착수 시(첫 편집 전) 노션 기능명세를 기준으로 PR 목록을 정하고, 각 PR 안에 더 작은 [커밋](../../dino-commit/references/commits.md#checkpoints)을 둡니다.

- PR마다 [종류](kinds.md#decide)·노션 기능(절)·대상 모듈·선행 PR·완료 조건·검사와 커밋 순서를 계획 파일(워크트리 루트 `<이슈키>-plan.md`, 커밋하지 않음)에 정합니다. 전체 구현 뒤 분할하지 않습니다.
- 선행 PR·현재 diff·기존 코드만으로 판단·기동·검사할 수 있게 필요한 테스트·설정·계약 문서를 포함합니다. 후속 구현이 필요한 경로는 노출하지 않습니다.
- 기능 활성화를 나누면 안전한 기본 동작·활성화·복구 조건을 명시합니다.
- 첫 PR의 base는 실제 기본 브랜치, 의존하는 PR은 직접 선행 PR의 브랜치입니다. 독립 변경은 기본 브랜치에서 나눕니다. 모든 후속 PR을 기본 브랜치와 비교해 선행 변경을 중복 제출하지 않습니다.
- 작성 요청 시 [PR 작성](writing.md#writing), 선행 PR 변경·반영 시 [stack 관리](#maintenance)를 적용합니다. 계획 자체는 게시·푸시·병합 권한을 늘리지 않습니다.

<a id="unit"></a>
## PR 단위

줄 수가 아니라 **한 PR = 한 종류 = 한 판단**을 기준으로 합니다.

- **유즈케이스는 수직 슬라이스로 완결합니다.** 노션 기능 하나(명령 하나, 또는 같은 화면이 함께 쓰는 조회 묶음)에 필요한 도메인 모델·포트·어댑터·Web·테스트를 한 PR에 넣습니다. 사용처 없는 도메인 모델·공개 API 선언만 먼저 올리거나 테스트를 후속 PR로 미루지 않습니다.
- **종류를 섞지 않습니다.** 기능에 필요한 리팩터링은 `구조 변경` PR로 먼저 분리하고 기능 PR을 그 위에 쌓습니다. 기능 중 발견한 무관한 결함은 별도 `결함 수정` PR로 냅니다.
- **호환성 변경은 단계마다 나눕니다.** expand(새 계약 추가) → migrate(소비자 전환) → contract(이전 계약 제거) 각 단계가 배포 가능한 PR입니다.
- **나눌 신호:** 핵심 다이어그램이 3개를 넘음, 두 종류가 동시에 맞음, 다이어그램·구현 내용 표로 설명되지 않는 변경이 리뷰 포인트 대부분을 차지함.
- **수평 분할은 예외입니다.** 한 유즈케이스를 계층별로 나눌 수밖에 없으면 이유와 나머지 PR 계획을 리뷰 포인트에 적습니다.

예: `develop ← 구조 변경(일정 잠금 추출) ← 유즈케이스(작업 재배정)`. 재배정 PR에는 잠금 추출 이후 차이만 표시합니다.

참고 근거: [Google Small CLs](https://google.github.io/eng-practices/review/developer/small-cls.html), [Parallel Change](https://martinfowler.com/bliki/ParallelChange.html), [GitHub 리뷰하기 쉬운 PR](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/getting-started/helping-others-review-your-changes).

<a id="maintenance"></a>
## Stacked PR 변경과 반영

- 선행 PR 반영 전에도 검토할 수 있으며 반영은 의존 순서대로 합니다. 게시·병합은 [승인 범위](publishing-approvals.md#publishing)를 지킵니다.
- 선행 PR이 수정·병합되면 후속 PR의 base/head SHA·diff·기존 댓글 위치·영향을 확인해 변경 범위를 알리고 필요한 검사·최종 연결 검토를 마칩니다. 정상 검증 뒤 변화가 없으면 반복하지 않습니다.
- 커밋 흐름을 보존하는 merge commit이 기본입니다. squash 등 다른 방식은 사용자 명시 선택을 따릅니다. 선행 PR을 squash했다면 후속 변경의 중복 여부를 확인합니다. 이력 재작성·푸시·병합에는 별도 승인이 필요합니다. [GitHub 병합 방식](https://docs.github.com/en/pull-requests/reference/pull-request-merges)
