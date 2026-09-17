<a id="planning"></a>
# 기능 작업의 PR 계획

기능 작업의 단위 계획·첫 편집 전에 읽습니다. 사람이 수용 여부를 판단할 단위로 stacked PR을 계획하고, 각 PR 안에 더 작은 [커밋](../checkpoints.md#checkpoints)을 둡니다. 기능 일부도 기존 동작·검증을 유지하면 별도 검토할 수 있습니다. 작은 기능은 하나로 완결하며 개수를 맞추려 빈 PR을 만들지 않습니다.

- PR마다 판단할 내용·대상 범위·선행 PR·완료 조건·검사와 커밋 순서를 대화나 임시 메모에 정합니다. 전체 구현 뒤 분할하지 않습니다.
- [PR 크기](../review/size.md#size)를 적용합니다. 선행 PR·현재 diff·기존 코드만으로 판단·기동·검사할 수 있게 필요한 테스트·설정·계약 문서를 포함합니다. 후속 구현이 필요한 경로는 노출하지 않습니다.
- 공개 API 선언만 떼어 사용 맥락을 없애거나 테스트를 후속 PR로 미루지 않습니다. 기능 활성화를 나누면 안전한 기본 동작·활성화·복구 조건을 명시합니다.
- 첫 PR의 base는 실제 기본 브랜치, 의존하는 PR은 직접 선행 PR의 브랜치입니다. 독립 변경은 기본 브랜치에서 나눕니다. 모든 후속 PR을 기본 브랜치와 비교해 선행 변경을 중복 제출하지 않습니다.
- 작성 요청 시 [PR 작성](writing.md#writing), 선행 PR 변경·반영 시 [stack 관리](maintenance.md#maintenance)를 읽습니다. 계획 자체는 게시·푸시·병합 권한을 늘리지 않습니다.

예: `main ← 계정 연결 PR ← 로그인 조립 PR`. 로그인 PR에는 계정 연결 이후 차이만 표시합니다.

참고 근거: [Google Small CLs](https://google.github.io/eng-practices/review/developer/small-cls.html), [GitHub PR 안내](https://docs.github.com/en/pull-requests/get-started/about-pull-requests).
