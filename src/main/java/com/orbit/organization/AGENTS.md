# Organization 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 책임·애그리게잇·설계 결정은 [조직 컨텍스트](../../../../../../docs/domain/bounded-contexts.md#organization), 제품 규칙은 [기획 원본 색인](../../../../../../docs/planning/README.md#planning)의 [조직·계정] 정책서·기능명세와 [제품 규칙 참조](../../../../../../docs/domain/bounded-contexts.md#organization-product)가 원본입니다. 여기에 타입 목록·제품 규칙을 복제하지 않습니다.

- [차이·공백](../../../../../../docs/domain/bounded-contexts.md#organization-gaps)의 항목은 [대역별 구현 규칙](../../../../../../docs/planning/README.md#gap-actions)대로 다룹니다.
- 모듈 루트 공개 계약은 없습니다. 허용 의존성은 [도메인 지도](../../../../../../docs/domain/README.md#모듈별-책임과-공개-계약)가 원본입니다.
- 총관리자는 직원 소속의 총관리자 표시입니다. 발주사는 총관리자 소속을 가리키지 않으며, 발주사와 소속은 서로 ID로만 참조합니다. 계정은 auth가 발급한 ID를 `AccountId`로만 보관합니다.
- 회사 코드의 저장 형식과 입력 정규화는 `CompanyCode`가 소유합니다(O-11). 발급은 `CompanyCodeGenerator` 후보를 현재 코드와 겹치지 않을 때까지 다시 만들고, `companies.code` 유일 제약이 최종 방어선입니다. 폐기 코드를 보관하게 되면(HM-287) 같은 중복 확인에 폐기 코드도 넣어 재발급을 막습니다.
- 요청자 계정은 웹 어댑터의 `RequesterAccountResolver`로 얻습니다. auth가 인증 계정을 공개 계약으로 제공하기 전(HM-296)까지 구현은 임시 `DenyingRequesterAccountResolver`(모두 403, `local`·`test` 프로필 전용)뿐이라 실제 요청은 거부되며, 이것이 의도입니다. auth 담당자가 정한 공개 계약이 생기면 컨트롤러가 그 계약으로 요청자를 받게 바꾸고 인터페이스와 임시 구현을 함께 지웁니다. `@Primary`로 덮지 않습니다. [제공 기능이 아직 없을 때](../../../../../../.claude/skills/dino-architecture/references/communication.md#제공-기능이-아직-없을-때)의 임시 구현 규칙은 원래 다른 모듈 계약·ACL 출력 Port 대상이지만, 요청자 계정은 인증 주체에서 읽는 입력이라 같은 규칙(안전한 거부, 프로필 제한, 교체 시 삭제)을 `adapter/in/web`에 적용한 예외입니다. 교체 전까지 문서화한 오류 목록은 최종 계약 기준이라 임시 거부(`COMMON-403`)를 넣지 않습니다.
- 입력 규칙(발주사명 길이 등)은 Domain 값객체가 한 번만 검사하고 서비스가 `ORGANIZATION-001`로 바꿉니다. Request에 같은 길이 제약을 중복하지 않습니다(Bean Validation은 UTF-16 단위로, Domain은 코드 포인트로 셉니다). Domain은 Spring·JPA·Web 타입과 `Clock`에 의존하지 않습니다.

## 집중 검증

- 회사 코드: `com.orbit.organization.domain.CompanyCodeTest`, `com.orbit.organization.adapter.out.code.SecureRandomCompanyCodeGeneratorTest`.
- 발주사 생성: `OrganizationTest`, `MembershipTest`, `CreateOrganizationServiceTest`, `OrganizationPersistenceAdapterTest`, `OrganizationApiIntegrationTest` 중 관련 테스트.
- 임시 resolver: `DenyingRequesterAccountResolverTest`. 모듈 조립: `com.orbit.organization.OrganizationModuleTest`.
- 공개 계약·HTTP는 [공통 검사 표](../../../../../../.claude/skills/dino-testing/references/verification.md#selection)를 따릅니다.
