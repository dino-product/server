# Organization 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용한다. 상세 계약은 [조직 도메인](../../../../../../docs/domain/organization.md#organization), 모델 분리 이유는 [ADR-003](../../../../../../docs/adr/003-organization-domain.md#organization-domain)이 소유한다. 현재 구현 상태·허용 의존성은 [도메인 지도](../../../../../../docs/domain/README.md#모듈별-책임과-공개-계약)를 따른다.

- Domain은 외부 모듈·Spring·JPA·Application·Adapter 타입에 의존하지 않는다. 시각은 호출자가 `Instant`로 전달한다.
- 확정한 계정 참조·직원 소속·기사 계약·참여 요청 용어를 사용한다. 분류 유형과 권한을 합치거나 직원↔기사 전환을 추가하지 않는다.
- 회사·계정별 중복 정책에는 비활성 관계도 포함한다. 후속 Application은 최신 조회·정책 호출·저장의 원자성과 동시성을 함께 보장한다.
- 직원 비활성화는 조직의 총관리자 보존 검사를 통한다. 권한 상태를 직원에 복제하지 않는다.
- 회사 코드 정책과 색상 매핑 TODO를 임의의 제품 정책으로 확정하지 않는다.
- 집중 검사는 변경한 `com.orbit.organization.domain` 테스트와 `com.orbit.ArchitectureTest`, `com.orbit.ModularityTest`를 사용한다. 테스트는 Spring Context 없이 실행한다.
