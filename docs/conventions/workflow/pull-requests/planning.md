<a id="planning"></a>
# 기능 작업의 PR 계획

기능 작업의 단위 계획·첫 편집 전에 사람이 수용 여부를 판단할 단위로 stacked PR을 계획하고, 각 PR 안에 더 작은 [커밋](../commits.md#checkpoints)을 둡니다. 기능 일부도 기존 동작·검증을 유지하면 별도 검토할 수 있습니다. 작은 기능은 하나로 완결하며 개수를 맞추려 빈 PR을 만들지 않습니다.

- PR마다 판단할 내용·대상 범위·선행 PR·완료 조건·검사와 커밋 순서를 대화나 임시 메모에 정합니다. 전체 구현 뒤 분할하지 않습니다.
- [PR 크기](#size)를 적용합니다. 선행 PR·현재 diff·기존 코드만으로 판단·기동·검사할 수 있게 필요한 테스트·설정·계약 문서를 포함합니다. 후속 구현이 필요한 경로는 노출하지 않습니다.
- 공개 API 선언만 떼어 사용 맥락을 없애거나 테스트를 후속 PR로 미루지 않습니다. 기능 활성화를 나누면 안전한 기본 동작·활성화·복구 조건을 명시합니다.
- 첫 PR의 base는 실제 기본 브랜치, 의존하는 PR은 직접 선행 PR의 브랜치입니다. 독립 변경은 기본 브랜치에서 나눕니다. 모든 후속 PR을 기본 브랜치와 비교해 선행 변경을 중복 제출하지 않습니다.
- 작성 요청 시 [PR 작성](writing.md#writing), 선행 PR 변경·반영 시 [stack 관리](#maintenance)를 적용합니다. 계획 자체는 게시·푸시·병합 권한을 늘리지 않습니다.

예: `main ← 계정 연결 PR ← 로그인 조립 PR`. 로그인 PR에는 계정 연결 이후 차이만 표시합니다.

참고 근거: [Google Small CLs](https://google.github.io/eng-practices/review/developer/small-cls.html), [GitHub PR 안내](https://docs.github.com/en/pull-requests/get-started/about-pull-requests).

<a id="size"></a>
## PR 크기

사람에게 제출하는 PR 하나의 base/head diff에서 추가·삭제된 코드·테스트·설정·문서 텍스트를 합쳐 약 500줄 이내로 유지합니다. 문맥 줄은 제외하며 이 수치를 커밋 크기나 AI 읽기 묶음에는 적용하지 않습니다. 작은 PR을 채우지 않습니다. 예상 범위가 넘으면 구현 전 결합된 계약을 보존할 분할 경계를 검토하고, 불가피한 초과는 이유·검토 범위를 밝힙니다. 파일 분포·정책 복잡성도 함께 판단합니다. [GitHub PR 권장사항](https://docs.github.com/en/pull-requests/concepts/helping-others-review-your-changes)

<a id="maintenance"></a>
## Stacked PR 변경과 반영

- 선행 PR 반영 전에도 검토할 수 있으며 반영은 의존 순서대로 합니다. 게시·병합은 [승인 범위](../approvals/publishing.md#publishing)를 지킵니다.
- 선행 PR이 수정·병합되면 후속 PR의 base/head SHA·diff·기존 댓글 위치·영향을 확인해 변경 범위를 알리고 필요한 검사·최종 연결 검토를 마칩니다. 정상 검증 뒤 변화가 없으면 반복하지 않습니다.
- 커밋 흐름을 보존하는 merge commit이 기본입니다. squash 등 다른 방식은 사용자 명시 선택을 따릅니다. 선행 PR을 squash했다면 후속 변경의 중복 여부를 확인합니다. 이력 재작성·푸시·병합에는 별도 승인이 필요합니다. [GitHub 병합 방식](https://docs.github.com/en/pull-requests/reference/pull-request-merges)
