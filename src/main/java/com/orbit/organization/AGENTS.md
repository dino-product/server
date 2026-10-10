# Organization 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 책임·애그리게잇·설계 결정은 [조직 컨텍스트](../../../../../../docs/domain/bounded-contexts.md#organization), 제품 규칙은 [기획 원본 색인](../../../../../../docs/planning/README.md#planning)의 [조직·계정] 정책서·기능명세와 [제품 규칙 참조](../../../../../../docs/domain/bounded-contexts.md#organization-product)가 원본입니다. 여기에 타입 목록·제품 규칙을 복제하지 않습니다.

- [차이·공백](../../../../../../docs/domain/bounded-contexts.md#organization-gaps)의 항목은 [대역별 구현 규칙](../../../../../../docs/planning/README.md#gap-actions)대로 다룹니다.
- 모듈 루트 공개 계약은 schedule이 쓰는 활성 구성원 조회 `OrganizationMemberLookup`과 그 결과 record입니다. 비활성 행(역할 변경으로 끝난 행 포함)과 다른 발주사의 행은 보지 않고, 한 계정·발주사에 활성 직원 소속과 활성 기사 계약이 함께 있으면 실패합니다(두 저장소에 걸친 규칙이라 조회 서비스가 확인). 직원 소속·기사 계약 두 테이블을 함께 보는 조회(`ActiveMemberQueryAdapter`, 내 소속 목록의 `AccountMembershipQueryAdapter`)는 한 문장(UNION ALL)으로 읽습니다. 따로 읽으면 그 사이에 커밋된 역할 변경 때문에 READ COMMITTED에서 둘 다 활성으로 보여 정상 요청이 실패하기 때문입니다. 역할 변경·비활성화의 대기함 반환은 모듈 루트의 요구 인터페이스 `TechnicianWorkRelease`를 같은 트랜잭션에서 호출해 처리하며 구현은 schedule이 맡습니다. 그 전까지 `DenyingTechnicianWorkRelease`(local·test)가 모든 호출을 거부하고, schedule 구현 PR에서 삭제합니다. 허용 의존성은 [도메인 지도](../../../../../../docs/domain/README.md#모듈별-책임과-공개-계약)가 원본입니다.
- 총관리자는 직원 소속의 총관리자 표시입니다. 발주사는 총관리자 소속을 가리키지 않으며, 발주사와 소속은 서로 ID로만 참조합니다. 계정은 auth가 발급한 ID를 `AccountId`로만 보관합니다.
- 회사 코드의 저장 형식과 입력 정규화는 `CompanyCode`가 소유합니다([조직·계정] §6). 발급은 `CompanyCodeGenerator` 후보를 현재 코드와 겹치지 않을 때까지 다시 만들고, `companies.code` 유일 제약이 최종 방어선입니다. 폐기 코드를 보관하게 되면(HM-287) 같은 중복 확인에 폐기 코드도 넣어 재발급을 막습니다.
- 요청자 계정은 컨트롤러가 auth의 루트 공개 계약 `AccountPrincipal`을 `@AuthenticationPrincipal`로 받아 계정 식별자만 Command에 넘깁니다. 이 타입은 웹 어댑터에서만 참조하고 Application·Domain에는 `AccountId`나 `Long`만 넘깁니다. auth 내부 principal(`AuthenticatedAccount`)이나 `Authentication#getName()`에 기대지 않습니다. 이 의존 때문에 auth는 organization을 조회·구독할 수 없습니다.
- 입력 규칙(발주사명 앞뒤 공백 제거·길이·이모지 불가 등)은 Domain 값객체가 한 번만 검사하고 서비스가 `ORGANIZATION-001`로 바꿉니다. Request에 같은 길이 제약을 중복하지 않습니다(Bean Validation은 UTF-16 단위로, Domain은 코드 포인트로 셉니다). Domain은 Spring·JPA·Web 타입과 `Clock`에 의존하지 않습니다.

## 집중 검증

- 회사 코드: `com.orbit.organization.domain.CompanyCodeTest`, `com.orbit.organization.adapter.out.code.SecureRandomCompanyCodeGeneratorTest`.
- 발주사 생성: `OrganizationTest`, `MembershipTest`, `CreateOrganizationServiceTest`, `OrganizationPersistenceAdapterTest`, `OrganizationApiIntegrationTest` 중 관련 테스트.
- 활성 구성원 조회: `OrganizationMemberLookupServiceTest`, `ActiveMemberQueryAdapterTest`.
- 내 소속 목록: `ListMyMembershipsServiceTest`, `AccountMembershipQueryAdapterTest`, `MembershipApiIntegrationTest`, `OpenApiDocumentationIntegrationTest`. schedule 소비는 `com.orbit.schedule.adapter.out.organization.OrganizationActorAdapterTest`.
- 대기함 반환 요구 인터페이스: `TechnicianWorkReleaseResultTest`, `DenyingTechnicianWorkReleaseTest`, 전체 컨텍스트에서 구현이 하나인지 `TechnicianWorkReleaseRegistrationTest`.
- 모듈 조립: `com.orbit.organization.OrganizationModuleTest`. auth 계약 사용은 `ModularityTest`와 실제 Access Token으로 호출하는 `OrganizationApiIntegrationTest`.
- 공개 계약·HTTP는 [공통 검사 표](../../../../../../.claude/skills/dino-testing/references/verification.md#selection)를 따릅니다.
