<a id="size"></a>
# PR 크기

사람에게 제출하는 PR 하나의 변경 텍스트를 약 500줄 이내로 유지합니다. 해당 base/head diff의 추가·삭제 줄을 합산하고 문맥은 제외합니다. 코드·테스트·설정·문서를 모두 포함하며 작은 PR을 채우지 않습니다. 커밋 크기·AI 읽기 묶음에는 이 수치를 적용하지 않습니다.

같은 기능도 판단할 문제·위험·의존성이 다르면 [stacked PR](../pull-requests/planning.md#planning)로 나눕니다. 예상 범위가 기준을 넘으면 구현 전에 결합된 계약을 보존할 분할 경계를 검토합니다. 불가피한 초과는 이유·검토 범위를 밝힙니다. 파일 분포·정책 복잡성도 보며 줄 수만으로 리뷰 가능성을 판단하지 않습니다. [GitHub PR 권장사항](https://docs.github.com/en/pull-requests/concepts/helping-others-review-your-changes)
