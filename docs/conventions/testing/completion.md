<a id="completion"></a>
# 완료 검증과 CI

완료 명령·문서-only 예외는 [루트 검증 기준](../../../AGENTS.md#검증과-완료)을 따릅니다.

- 코드 변경의 중간 커밋은 [단위별 집중 검사·계약 검토](../workflow/checkpoints.md#checkpoints) 후 생성합니다. 각 PR의 변경·알려진 수정을 마치면 전체 검증을 수행합니다. 커밋마다 전체 검사를 반복할 필요는 없으나 필요한 집중 검사는 생략하지 않습니다. 완료 검증에 `--tests` 필터를 쓰지 않습니다.
- 현재 [CI](../../../.github/workflows/ci.yml)는 PR 최종 병합 결과를 검사하며 내부 커밋을 순회하지 않습니다. 커밋별 조립·실행 가능성은 해당 상태의 검사 근거로 확인합니다. `verify` 뒤의 [자동 리뷰](../workflow/review/automation.md#automation) 잡은 결과를 인용해 게시하며 병합을 막지 않고, 변경 문서의 링크·앵커는 별도 [PR conventions](../../../.github/workflows/pr-conventions.yml)가 판정합니다. [GitHub PR 이벤트](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#pull_request)
- 로컬 `./gradlew test`는 Docker가 없으면 컨테이너 테스트를 건너뜁니다. 전체 실행은 `./gradlew test -PrequireAllTests=true`를 사용합니다.
- `CI=true` 또는 `-PrequireAllTests=true`는 0건 실행·건너뛴 테스트가 있으면 실패합니다. `-PrequireAllTests=false`로 CI 정책을 해제할 수 없습니다. 이 옵션은 `--tests` 필터를 감지하지 않으므로 전체 실행 여부는 실제 명령도 확인합니다.
- Docker가 없으면 가능한 검사를 수행하고 나머지는 원인과 함께 미검증으로 보고합니다. 성공·실패·건너뛰기·필터 여부를 구분하며 이전 결과를 이번 실행으로 보고하지 않습니다.
- 최종 상태의 필수 검사 통과 후 추가 변경·실패·미해결 우려 없이 반복·확대하지 않습니다.
- CI는 Docker를 먼저 확인하고 성공 여부와 관계없이 테스트·Checkstyle·JaCoCo 보고서를 `verification-reports` artifact로 14일간 보관합니다.
- [DB 승인](../workflow/approvals/database.md#database)은 에이전트의 로컬 검사에 적용하며 기존 CI 실행 조건·테스트 정리 동작은 변경하지 않습니다. 푸시 등으로 CI를 유발하면 승인 요약에 CI의 DB 테스트·자원 정리도 포함합니다.
