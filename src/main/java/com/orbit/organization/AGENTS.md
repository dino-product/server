# Organization 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 책임·애그리게잇·설계 결정은 [조직 컨텍스트](../../../../../../docs/domain/bounded-contexts.md#organization), 제품 규칙은 [기획 원본 색인](../../../../../../docs/planning/README.md#planning)의 [조직·계정] 정책서·기능명세와 [제품 규칙 참조](../../../../../../docs/domain/bounded-contexts.md#organization-product)가 원본입니다. 여기에 타입 목록·제품 규칙을 복제하지 않습니다.

- [차이·공백](../../../../../../docs/domain/bounded-contexts.md#organization-gaps)의 항목은 [대역별 구현 규칙](../../../../../../docs/planning/README.md#gap-actions)대로 다룹니다.
- 모듈 루트 공개 계약은 없습니다. 허용 의존성은 [도메인 지도](../../../../../../docs/domain/README.md#모듈별-책임과-공개-계약)가 원본입니다.
- 총관리자는 직원 소속의 총관리자 표시입니다. 발주사는 총관리자 소속을 가리키지 않으며, 발주사와 소속은 서로 ID로만 참조합니다. 계정은 auth가 발급한 ID를 `AccountId`로만 보관합니다.
- 회사 코드의 저장 형식과 입력 정규화는 `CompanyCode`가 소유합니다([조직·계정] §6). 발급은 `CompanyCodeGenerator` 후보를 현재 코드와 겹치지 않을 때까지 다시 만들고, `companies.code` 유일 제약이 최종 방어선입니다. 폐기 코드를 보관하게 되면(HM-287) 같은 중복 확인에 폐기 코드도 넣어 재발급을 막습니다.
- 요청자 계정은 컨트롤러가 auth의 루트 공개 계약 `AccountPrincipal`을 `@AuthenticationPrincipal`로 받아 계정 식별자만 Command에 넘깁니다. 이 타입은 웹 어댑터에서만 참조하고 Application·Domain에는 `AccountId`나 `Long`만 넘깁니다. auth 내부 principal(`AuthenticatedAccount`)이나 `Authentication#getName()`에 기대지 않습니다. 이 의존 때문에 auth는 organization을 조회·구독할 수 없습니다.
- 입력 규칙(발주사명 앞뒤 공백 제거·길이·이모지 불가 등)은 Domain 값객체가 한 번만 검사하고 서비스가 `ORGANIZATION-001`로 바꿉니다. Request에 같은 길이 제약을 중복하지 않습니다(Bean Validation은 UTF-16 단위로, Domain은 코드 포인트로 셉니다). Domain은 Spring·JPA·Web 타입과 `Clock`에 의존하지 않습니다.

## 오류 순서

총관리자만 하는 발주사 유즈케이스(발주사 정보 조회·수정)는 아래 순서로 확인하고 먼저 걸린 오류 하나만 돌려줍니다. 숫자가 아닌 식별자(2번)와 3번은 웹 계층이, 0 이하 식별자(2번)와 1·4·5번은 `OrganizationOwners`가, 6번은 각 서비스가 확인합니다. 서비스 javadoc은 이 순서를 복제하지 않습니다.

1. 요청자 계정 누락 — 인증 계층의 프로그래밍 오류(500)
2. 발주사 식별자 형식 — 400 `COMMON-400`
3. 요청 본문 형식(깨진 JSON, 목록에 없는 업종 값 등) — 400 `COMMON-400`. 웹 계층이 본문을 읽을 때 걸러지므로 권한 확인보다 먼저 응답합니다
4. 그 발주사의 활성 직원 소속이 아님(없는 발주사·다른 발주사·기사 포함) — 403 `ORGANIZATION-002`. 발주사 존재 여부를 드러내지 않으려고 404를 쓰지 않습니다
5. 활성 소속이지만 총관리자가 아님 — 403 `ORGANIZATION-003`
6. 요청 값(발주사명 규칙·업종 누락) — 400 `ORGANIZATION-001`

## 집중 검증

- 회사 코드: `com.orbit.organization.domain.CompanyCodeTest`, `com.orbit.organization.adapter.out.code.SecureRandomCompanyCodeGeneratorTest`.
- 발주사 생성: `OrganizationTest`, `MembershipTest`, `CreateOrganizationServiceTest`, `OrganizationPersistenceAdapterTest`, `OrganizationApiIntegrationTest` 중 관련 테스트.
- 발주사 정보 조회·수정: `OrganizationTest`, `GetOrganizationServiceTest`, `UpdateOrganizationServiceTest`, `OrganizationPersistenceAdapterTest`, `OrganizationApiIntegrationTest` 중 관련 테스트.
- 모듈 조립: `com.orbit.organization.OrganizationModuleTest`. auth 계약 사용은 `ModularityTest`와 실제 Access Token으로 호출하는 `OrganizationApiIntegrationTest`.
- 공개 계약·HTTP는 [공통 검사 표](../../../../../../.claude/skills/dino-testing/references/verification.md#selection)를 따릅니다.
