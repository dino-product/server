# Profile 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 책임·가입 상태·공통 검사·차이·공백은 [Profile 문서](../../../../../../docs/domain/profile.md)가 원본입니다. 제품 규칙은 [기획 원본 색인](../../../../../../docs/planning/README.md#planning)의 [조직·계정] 정책서(§5 계정·가입, §5.1 마이페이지)와 기능명세가 원본입니다. 여기에 타입 목록·제품 규칙을 복제하지 않습니다.

- 계정의 가입 프로필(이름·연락처)과 가입 상태를 소유합니다. auth는 외부 식별·토큰만 맡고, 계정은 auth가 발급한 ID를 `AccountId`로만 보관합니다.
- 프로필이 없는 계정은 카카오 인증만 마친 가입 미완료 계정으로 봅니다. 프로필 행은 처음 프로필을 저장할 때 만듭니다.
- 요청자 계정은 컨트롤러가 auth의 루트 공개 계약 `AccountPrincipal`을 `@AuthenticationPrincipal`로 받아 계정 식별자만 Command·Query에 넘깁니다. 이 의존 때문에 auth는 profile을 조회·구독할 수 없습니다. auth에 무언가를 시켜야 하면 auth 공개 계약을 profile이 직접 호출합니다.
- 시행 중인 약관 버전은 설정값 `app.profile.terms.versions`가 원본입니다. 필수 약관 값을 올리면 기존 계정은 재동의 전까지 가입 미완료와 같은 범위만 씁니다. 동의 기록은 추가만 하고 고치거나 지우지 않습니다.
- 가입 미완료·재동의 전 계정의 이용 범위는 [가입 공통 검사](../../../../../../docs/domain/profile.md#profile-signup-gate)가 원본입니다. 다른 모듈이 가입 전 계정에 열어야 하는 API(회사 확인·탈퇴 등)를 만들면 `ProfileWebConfig`의 허용 목록에 경로를 더하고, 공통 검사를 바꾸면 auth의 토큰 404 계약(`AuthApiIntegrationTest`)도 확인합니다.
- 프로필 API는 본인 계정만 다룹니다. 경로의 계정이 요청자와 다르면 존재를 드러내지 않도록 `PROFILE-002`(404)로 거부합니다.
- 입력 규칙(이름·연락처)은 Domain 값객체가 한 번만 검사하고 서비스가 `PROFILE-001`로 바꿉니다. Request에 같은 제약을 중복하지 않습니다. Domain은 Spring·JPA·Web 타입과 `Clock`에 의존하지 않습니다.

## 집중 검증

- 입력 규칙·상태: `com.orbit.profile.domain` 패키지의 테스트.
- 유스케이스: `com.orbit.profile.application.service` 패키지의 테스트.
- 저장소: `com.orbit.profile.adapter.out.persistence.ProfilePersistenceAdapterTest`.
- API·공통 검사: 실제 Access Token으로 호출하는 `com.orbit.profile.adapter.in.web.ProfileApiIntegrationTest`, `SignupGateInterceptorTest`. 공통 검사를 바꾸면 `com.orbit.auth.adapter.in.web.AuthApiIntegrationTest`(토큰 404 계약)도 실행합니다.
- 모듈 조립: `com.orbit.profile.ProfileModuleTest`. auth 계약 사용은 `ModularityTest`.
- 공개 계약·HTTP는 [공통 검사 표](../../../../../../.claude/skills/dino-testing/references/verification.md#selection)를 따릅니다.
