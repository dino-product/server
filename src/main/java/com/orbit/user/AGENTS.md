# User 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 책임·불변식·공개 타입은 [User 문서](../../../../../../docs/domain/user.md)의 관련 절이 원본입니다. 아래 코드 경로는 이 모듈 기준입니다.

- 사용자 원본은 user가 소유합니다. 공개 계약 변경 시 User 문서를 갱신하고 여기에 타입 목록을 복제하지 않습니다.
- 불변식은 `domain`에 두며 JPA annotation을 넣지 않습니다. JPA Entity·Spring Data Repository는 `adapter/out/persistence` 밖으로 노출하지 않습니다.
- 소셜 가입 등 새 등록 진입점도 등록 이벤트를 발행하고, 기존 사용자를 재사용할 때는 중복 발행하지 않습니다. 이벤트에는 `userId`와 Clock 기반 UTC `occurredAt`만 담고 표시 이름을 추가하지 않습니다.
- 공개 계약을 바꾸면 소비자인 `auth`의 사용처를 확인합니다.

## 집중 검증

- 불변식·등록·조회: `com.orbit.user.domain.UserTest`, `com.orbit.user.application.service.RegisterUserServiceTest`, `com.orbit.user.application.service.UserLookupServiceTest` 중 관련 테스트.
- 모듈 조립: `com.orbit.user.UserModuleTest`와 영향받는 auth 테스트.
- 공개 계약·등록 이벤트·HTTP는 [공통 검사 표](../../../../../../docs/conventions/testing/selection.md#selection)를 따릅니다.
