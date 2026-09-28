# Organization 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용한다. 상세 계약은 [조직 도메인](../../../../../../docs/domain/organization.md#organization), 모델 분리 이유는 [ADR-003](../../../../../../docs/adr/003-organization-domain.md#organization-domain)이 소유한다. 현재 구현 상태·허용 의존성은 [도메인 지도](../../../../../../docs/domain/README.md#모듈별-책임과-공개-계약)를 따른다.

- Domain은 외부 모듈·Spring·JPA·Application·Adapter 타입에 의존하지 않는다. 시각은 호출자가 `Instant`로 전달한다.
- 확정한 계정 참조·직원 소속·기사 계약·참여 요청 용어를 사용한다. 분류 유형과 권한을 합치거나 직원↔기사 전환을 추가하지 않는다.
- 회사·계정별 중복 정책에는 비활성 관계도 포함한다. 후속 Application은 최신 조회·정책 호출·저장의 원자성과 동시성을 함께 보장한다.
- 직원 비활성화는 조직의 총관리자 보존 검사를 통한다. 권한 상태를 직원에 복제하지 않는다.
- 회사 코드는 8자 대문자 Crockford Base32 형식을 따른다. 발급·변경 정책은 도메인 문서의 확정 범위만 적용하며 색상 매핑 TODO를 임의의 제품 정책으로 확정하지 않는다.
- 회사 생성은 계정 ID와 회사명을 확인한 뒤 회사·최초 소속·총관리자 참조를 한 트랜잭션에 저장한다. 도메인 호출만 입력 오류로 변환하고 포트·시계 실패는 그대로 전파한다.
- 회사 정보 수정은 회사 행을 잠근 뒤 총관리자를 확인하며 이름·선택 업종만 바꾼다. 조회·수정은 회사 없음과 권한 없음에 같은 403을 사용한다.
- 회사 코드 조회는 활성 총관리자에게만 허용하며 회사 없음과 권한 없음에 같은 403을 사용한다. 링크·QR URL은 반환하지 않는다.
- 도메인 변경의 집중 검사는 `com.orbit.organization.domain` 테스트와 `com.orbit.ArchitectureTest`, `com.orbit.ModularityTest`를 사용한다. 도메인 테스트는 Spring Context 없이 실행한다. 영속성 변경은 PostgreSQL 컨테이너 기반 `OrganizationPersistenceAdapterTest`와 프로필 설정 검사를 추가한다.
