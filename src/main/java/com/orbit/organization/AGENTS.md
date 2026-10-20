# Organization 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 책임·애그리게잇·설계 결정은 [조직 컨텍스트](../../../../../../docs/domain/bounded-contexts.md#organization), 제품 규칙은 [기획 원본 색인](../../../../../../docs/planning/README.md#planning)의 [조직·계정] 정책서·기능명세와 [제품 규칙 참조](../../../../../../docs/domain/bounded-contexts.md#organization-product)가 원본입니다. 여기에 타입 목록·제품 규칙을 복제하지 않습니다.

- [차이·공백](../../../../../../docs/domain/bounded-contexts.md#organization-gaps)의 항목은 [대역별 구현 규칙](../../../../../../docs/planning/README.md#gap-actions)대로 다룹니다.
- 모듈 루트 공개 계약의 목록·소비자와 허용 의존성은 [도메인 지도](../../../../../../docs/domain/README.md#모듈별-책임과-공개-계약)가 원본이며, 아래는 그 구현 규칙입니다. 활성 구성원 조회(`OrganizationMemberLookup`)는 비활성 행(역할 변경으로 끝난 행 포함)과 다른 발주사의 행은 보지 않고, 한 계정·발주사에 활성 직원 소속과 활성 기사 계약이 함께 있으면 실패합니다(두 저장소에 걸친 규칙이라 조회 서비스가 확인). 직원 소속·기사 계약 두 테이블을 함께 보는 조회(`ActiveMemberQueryAdapter`, 내 소속 목록의 `AccountMembershipQueryAdapter`)는 한 문장(UNION ALL)으로 읽습니다. 따로 읽으면 그 사이에 커밋된 역할 변경 때문에 READ COMMITTED에서 둘 다 활성으로 보여 정상 요청이 실패하기 때문입니다. 기사 계약 조회(`TechnicianContractLookup`)의 계정별 목록은 반대로 모든 발주사의 기사 계약을 비활성 행까지 활성 여부와 함께 돌려줍니다. schedule의 탈퇴 뒤처리가 같은 탈퇴로 이미 비활성화된 계약의 작업도 돌려야 하기 때문입니다. 활성 확인은 다른 발주사·비활성·없는 계약을 모두 거짓으로 봅니다. 두 조회 모두 schedule이 판단에 바로 쓰므로, 호출 트랜잭션의 영속성 컨텍스트에 기사 계약 엔티티가 남아 있어도 DB 상태를 보도록 엔티티가 아닌 스칼라 값으로 읽습니다(`TechnicianContractQueryAdapter`). 역할 변경·비활성화의 대기함 반환은 모듈 루트의 요구 인터페이스 `TechnicianWorkRelease`를 같은 트랜잭션에서 호출해 처리하며 구현은 schedule이 맡습니다. 구현은 schedule의 기사 일정 잠금을 거치므로, 호출하는 트랜잭션에 READ COMMITTED보다 높은 격리 수준을 지정하지 않습니다(schedule이 거부).
- 총관리자는 직원 소속의 총관리자 표시입니다. 발주사는 총관리자 소속을 가리키지 않으며, 발주사와 소속은 서로 ID로만 참조합니다. 계정은 auth가 발급한 ID를 `AccountId`로만 보관합니다.
- 회사 코드의 저장 형식과 입력 정규화는 `CompanyCode`가 소유합니다([조직·계정] §6). 발급은 생성·변경 모두 `CompanyCodeIssuer`로 하며, `CompanyCodeGenerator` 후보를 현재 코드·폐기 코드와 겹치지 않을 때까지 다시 만듭니다. 중복 확인은 두 테이블을 한 SQL 문으로 봐서 동시 변경이 커밋되는 순간에도 폐기 코드를 놓치지 않고, `companies.code`·`retired_company_codes.code` 유일 제약이 최종 방어선입니다. 발주사 행을 바꾸는 유즈케이스(정보 수정·코드 변경)는 `OrganizationLocks`를 거쳐 `OrganizationRepository#lock`으로 발주사 행을 잠근 뒤 읽습니다. JPA가 행 전체를 다시 쓰므로 잠그지 않으면 늦게 커밋한 쪽이 다른 쪽의 변경(예: 새 회사 코드)을 되돌립니다.
- 총관리자 수를 바꾸거나 그 수로 판단하는 변경(총관리자 지정·해제, 이후 소속 비활성화·탈퇴 차단)은 요청자가 그 발주사의 활성 소속인지만 잠그기 전에 확인하고(소속을 읽어 들이지 않는 존재 확인), `OrganizationLocks`를 거쳐 발주사 행을 잠근 뒤 요청자 소속을 다시 읽어 총관리자인지 확인합니다. 활성 총관리자 수도 잠근 뒤에 셉니다. 마지막 1명 판단은 `Membership#revokeOwner`처럼 Domain이 하고 서비스가 오류 코드로 바꿉니다.
- 참여 요청은 같은 계정·발주사에 승인 대기 요청을 하나만 둡니다. DB 제약이 없으므로 요청 생성은 회사 코드로 찾은 발주사 행을 `OrganizationLocks`를 거쳐 `OrganizationRepository#lock`으로 잠근 뒤 회사 코드를 다시 확인하고 소속·대기 요청을 판정합니다. 요청 상태를 바꾸는 유즈케이스(취소, 승인·거절)는 요청 행을 `ParticipationRequestRepository#findByIdForUpdate`로 처음 읽어 서로 한 줄로 섭니다. 회사 코드 입력의 판정(없는 코드·이전 코드)과 참여 가능 여부는 회사 확인과 요청 생성이 같은 판정(`CompanyCodes`, `ParticipationEligibility`)을 씁니다. 무차별 대입을 막는 계정별 실패 횟수 제한(`CompanyCodeAttemptLimiter`, Redis)도 `CompanyCodes`가 함께 판정해 두 유즈케이스가 같은 횟수를 나눠 씁니다. 시도를 코드를 찾기 전에 원자적으로 세고 현재 코드·이전 코드로 판정되면 되돌려, 막힌 계정은 코드를 찾아보지 못하고 동시에 몰아 보내도 한도만큼만 찾아봅니다. 이전 코드는 실제 회사를 맞힌 것이라 실패로 세지 않습니다.
- 요청자 계정은 컨트롤러가 auth의 루트 공개 계약 `AccountPrincipal`을 `@AuthenticationPrincipal`로 받아 계정 식별자만 Command에 넘깁니다. 이 타입은 웹 어댑터에서만 참조하고 Application·Domain에는 `AccountId`나 `Long`만 넘깁니다. auth 내부 principal(`AuthenticatedAccount`)이나 `Authentication#getName()`에 기대지 않습니다. 이 의존 때문에 auth는 organization을 조회·구독할 수 없습니다.
- 입력 규칙(발주사명·유형 이름 보이지 않는 문자 정리·앞뒤 공백 제거·길이·이모지·제어 문자 불가 등)은 Domain 값객체가 한 번만 검사하고(공통 이름 규칙은 `NameText`) 서비스가 발주사는 `ORGANIZATION-001`, 유형은 `ORGANIZATION-007`로 바꿉니다. Request에 같은 길이 제약을 중복하지 않습니다(Bean Validation은 UTF-16 단위로, Domain은 코드 포인트로 셉니다). Domain은 Spring·JPA·Web 타입과 `Clock`에 의존하지 않습니다.
- 직원 유형·기사 유형은 테이블이 달라 식별자가 겹칠 수 있으므로 항상 `MemberCategoryKind`와 함께 찾습니다. 유형 생성·수정·삭제는 권한 확인 뒤 발주사 행을 잠그고(`OrganizationLocks`를 거쳐 `OrganizationRepository#lock`) 이름 중복·삭제 여부를 확인합니다. 발주사 잠금은 모든 유즈케이스가 `OrganizationLocks`를 거치며, 대기 한도는 `app.organization.lock.wait-limit`(기본 2초)이고 넘기면 `OrganizationBusyException`을 거쳐 `ORGANIZATION-010`이 됩니다. 삭제되지 않은 행끼리의 (발주사, 이름) 부분 유일 인덱스가 잠금을 거치지 않은 저장의 최종 방어선이며, 위반은 `DuplicateCategoryNameException`을 거쳐 `ORGANIZATION-009`가 됩니다. JPA 매핑으로 표현할 수 없어 마이그레이션(`src/main/resources/db/migration`)에 두며, 인덱스 이름이 중복 판정에 쓰이므로 바꾸지 않습니다.

## 오류 순서

발주사 유즈케이스(발주사 정보 조회·수정, 회사 코드 조회·변경, 직원·기사 유형 관리)는 아래 순서로 확인하고 먼저 걸린 오류 하나만 돌려줍니다. 숫자가 아닌 식별자(2번)와 3번은 웹 계층이, 0 이하 발주사 식별자(2번)와 1·4·5번은 `OrganizationOwners`가, 0 이하 유형 식별자(2번)와 6·8·9번의 유형 확인은 `MemberCategories`를 거쳐, 7번은 `OrganizationLocks`를 거쳐 각 서비스가 확인합니다. 유형 목록 조회는 5번을 확인하지 않습니다. 총관리자 지정·해제는 잠금을 4번과 5번 사이에 하므로 `OrganizationOwners`의 순서를 따릅니다. 서비스 javadoc은 이 순서를 복제하지 않습니다.

1. 요청자 계정 누락 — 인증 계층의 프로그래밍 오류(500)
2. 발주사·유형 식별자 형식 — 400 `COMMON-400`
3. 요청 본문 형식(깨진 JSON, 목록에 없는 업종 값 등) — 400 `COMMON-400`. 웹 계층이 본문을 읽을 때 걸러지므로 권한 확인보다 먼저 응답합니다
4. 그 발주사의 활성 직원 소속이 아님(없는 발주사·다른 발주사·기사 포함) — 403 `ORGANIZATION-002`. 발주사 존재 여부를 드러내지 않으려고 404를 쓰지 않습니다
5. 활성 소속이지만 총관리자가 아님 — 403 `ORGANIZATION-003`
6. 요청 값 — 발주사명 규칙·업종 누락은 400 `ORGANIZATION-001`, 유형 이름·색상 규칙은 400 `ORGANIZATION-007`
7. 같은 발주사의 앞선 변경이 잠금 대기 한도 안에 끝나지 않음(발주사 정보 수정, 회사 코드 변경, 유형 생성·수정·삭제) — 409 `ORGANIZATION-010`. 총관리자 지정·해제는 4번 다음, 5번 전에 잠그므로 이 오류가 4번 다음에 옵니다
8. 유형이 없거나 다른 발주사 유형, 수정하려는 유형이 삭제됨 — 404 `ORGANIZATION-008`. 삭제는 이미 삭제된 유형도 성공합니다
9. 같은 발주사·같은 종류의 삭제되지 않은 유형과 이름 중복 — 409 `ORGANIZATION-009`

## 집중 검증

- 회사 코드 형식·후보 생성: `com.orbit.organization.domain.CompanyCodeTest`, `com.orbit.organization.adapter.out.code.SecureRandomCompanyCodeGeneratorTest`.
- 회사 코드 조회·변경: `OrganizationTest`, `GetCompanyCodeServiceTest`, `ChangeCompanyCodeServiceTest`, `CreateOrganizationServiceTest`(발급 공유), `OrganizationPersistenceAdapterTest`, `OrganizationApiIntegrationTest` 중 관련 테스트.
- 발주사 생성: `OrganizationTest`, `MembershipTest`, `CreateOrganizationServiceTest`, `OrganizationPersistenceAdapterTest`, `OrganizationApiIntegrationTest` 중 관련 테스트.
- 발주사 정보 조회·수정: `OrganizationTest`, `GetOrganizationServiceTest`, `UpdateOrganizationServiceTest`, `OrganizationPersistenceAdapterTest`, `OrganizationApiIntegrationTest` 중 관련 테스트, 정보 수정과의 잠금 경합은 `OrganizationUpdateLockIntegrationTest`.
- 회사 확인·참여 요청 생성·취소: `ParticipationRequestTest`, `CheckCompanyServiceTest`, `RequestParticipationServiceTest`, `CancelParticipationRequestServiceTest`, `ParticipationRequestPersistenceAdapterTest`, `OrganizationPersistenceAdapterTest`(코드로 찾기·폐기 코드 확인), `ParticipationRequestApiIntegrationTest`, 동시 요청은 `ParticipationRequestLockIntegrationTest`. 회사 코드 실패 횟수 제한은 `RedisCompanyCodeAttemptLimiterTest`(Testcontainers Redis)와 위 서비스·API 테스트. 내 신청 현황은 `ListMyParticipationRequestsServiceTest`, `ParticipationRequestQueryAdapterTest`, `ParticipationRequestApiIntegrationTest`.
- 활성 구성원 조회: `OrganizationMemberLookupServiceTest`, `ActiveMemberQueryAdapterTest`.
- 기사 계약 조회(계정의 계약 목록·활성 확인): `TechnicianContractLookupServiceTest`, `TechnicianContractQueryAdapterTest`, `OrganizationModuleTest`.
- 내 소속 목록: `ListMyMembershipsServiceTest`, `AccountMembershipQueryAdapterTest`, `MembershipApiIntegrationTest`, `OpenApiDocumentationIntegrationTest`. schedule 소비는 `com.orbit.schedule.adapter.out.organization.OrganizationActorAdapterTest`.
- 대기함 반환 요구 인터페이스: `TechnicianWorkReleaseResultTest`, 전체 컨텍스트에서 구현이 하나인지 `TechnicianWorkReleaseRegistrationTest`. schedule 구현은 `com.orbit.schedule.adapter.in.organization.TechnicianWorkReleaseIntegrationTest`.
- 총관리자 지정·해제: `MembershipTest`, `DesignateOwnerServiceTest`, `RevokeOwnerServiceTest`, `OrganizationPersistenceAdapterTest`, `OrganizationOwnerApiIntegrationTest`, 잠금 경합은 `OwnerChangeLockIntegrationTest`.
- 직원·기사 유형: `MemberCategoryTest`, `CreateMemberCategoryServiceTest`, `UpdateMemberCategoryServiceTest`, `DeleteMemberCategoryServiceTest`, `ListMemberCategoriesServiceTest`, `MemberCategoryPersistenceAdapterTest`, `MemberCategoryLockIntegrationTest`, `MemberCategoryApiIntegrationTest` 중 관련 테스트. 발주사 잠금은 `OrganizationLockPropertiesTest`, `OrganizationPersistenceAdapterTest`, `MemberCategoryLockIntegrationTest`.
- 모듈 조립: `com.orbit.organization.OrganizationModuleTest`. auth 계약 사용은 `ModularityTest`와 실제 Access Token으로 호출하는 `OrganizationApiIntegrationTest`.
- 공개 계약·HTTP는 [공통 검사 표](../../../../../../.claude/skills/dino-testing/references/verification.md#selection)를 따릅니다.
