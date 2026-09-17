# User 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 책임·불변식·공개 타입은 [User 문서](../../../../../../docs/domain/user.md)의 관련 절이 원본입니다. 아래 코드 경로는 이 모듈 기준입니다.

- 사용자 원본은 user가 소유하며 공개 계약은 모듈 루트에 둡니다. 계약 변경 시 User 문서를 갱신하고 여기에 타입 목록을 복제하지 않습니다.
- 불변식은 `domain`에 두며 JPA annotation을 넣지 않습니다. JPA Entity·Spring Data Repository는 `adapter/out/persistence` 밖으로 노출하지 않습니다.
- 등록 이벤트는 저장 후 같은 트랜잭션에서 발행합니다. 소셜 가입 등 새 진입점도 이 계약을 지키고 기존 사용자 재사용 시 중복 발행하지 않습니다. `userId`와 Clock 기반 UTC `occurredAt`만 공개하며 커밋 시각으로 해석하거나 표시 이름을 추가하지 않습니다.
- 계약 변경은 `auth` 사용처·허용 의존성도 확인합니다. `auth` 구현에 역의존하지 않습니다.

## 집중 검증

- 불변식·등록·조회: `com.example.user.domain.UserTest`, `com.example.user.application.service.RegisterUserServiceTest`, `com.example.user.application.service.UserLookupServiceTest` 중 관련 테스트.
- 모듈 조립: `com.example.user.UserModuleTest`와 영향받는 auth 테스트.
- 공개 계약·등록 이벤트·HTTP는 [공통 검사 표](../../../../../../docs/conventions/testing/selection.md#selection)를 따릅니다. 발행자 mock만으로 커밋·롤백을 검증했다고 보지 않습니다.
