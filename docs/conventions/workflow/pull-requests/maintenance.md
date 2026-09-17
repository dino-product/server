<a id="maintenance"></a>
# Stacked PR 변경과 반영

- 선행 PR 반영 전에도 검토할 수 있으며 반영은 의존 순서대로 합니다. 게시·병합은 [승인 범위](../approvals/publishing.md#publishing)를 지킵니다.
- 선행 PR이 수정·병합되면 후속 PR의 base/head SHA·diff·기존 댓글 위치·영향을 확인해 변경된 범위를 알리고, 필요한 검사와 최종 연결 검토를 마칩니다. 정상 검증 뒤 변화가 없으면 반복하지 않습니다.
- 커밋 흐름을 보존하는 merge commit이 기본입니다. squash 등 다른 방식은 사용자 명시 선택을 따릅니다. 선행 PR을 squash했다면 후속 변경의 중복 여부를 확인합니다. 이력 재작성·푸시·병합에는 별도 승인이 필요합니다.

참고: [GitHub 병합 방식](https://docs.github.com/en/pull-requests/reference/pull-request-merges).
