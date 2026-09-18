# 테스트 지침

[루트 지침](../../../../../AGENTS.md)에 추가 적용합니다. 작성 시 [설계](../../../../../docs/conventions/testing/design.md#design), 검사 선택 시 [집중 검사](../../../../../docs/conventions/testing/selection.md#selection), 실행 시 [출력·대기](../../../../../docs/conventions/testing/execution.md#execution)를 읽습니다.

- 테스트가 다루는 소스 모듈을 먼저 식별하고 해당 `AGENTS.md`와 계약 절을 읽습니다. 소스 지침은 이 경로의 상위 파일이 아니므로 자동 로딩을 가정하지 않습니다. [경로별 진입점](../../../../../docs/domain/README.md#작업-경로와-추가-지침)에서 대상만 선택하고, 이미 받은 본문은 재사용합니다.
- 단위 테스트는 Spring Context 없이, DB·전체 API는 PostgreSQL Testcontainers로 검증합니다. H2로 대체하지 않습니다.
- 커밋·롤백·비동기 검증은 실제 경계에서 수행합니다. 리스너 직접 호출·mock·테스트 전체의 롤백 트랜잭션으로 대체하지 않습니다.
- 집중 검사 목록은 소유 모듈 지침과 테스트 문서의 표를 확인합니다. 필터링·건너뛰기를 전체 통과로 보고하지 않습니다.
