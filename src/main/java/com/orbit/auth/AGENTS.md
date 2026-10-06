# Auth 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 구현 범위·공개 계약·흐름은 [Auth 문서](../../../../../../docs/domain/auth.md)의 관련 절이 원본입니다.

- 인증 성공·토큰·세션은 검증을 통과한 카카오·Apple id_token과 서버 발급 nonce(Apple은 그 해시, 웹 흐름은 state와 시작 브라우저 연결까지)로만 만듭니다. Apple은 authorization code 교환 결과의 `sub`가 id_token과 같을 때만 계정을 처리합니다. 예제 subject나 사용자 식별자만으로 만들지 않습니다.
- Domain은 Spring·JPA·Web·Nimbus 타입에 의존하지 않습니다. 시각은 호출자가 UTC `Instant`로 넘기고 서비스는 주입받은 `Clock`을 씁니다.
- 제공자 sub는 `ExternalIdentity`로만 보관하고 이메일·닉네임·이름을 식별에 쓰거나 저장하지 않습니다. 로그·예외 메시지·`toString`에 sub, id_token, authorization code, client_secret, `.p8` 키, refresh token 암호화 키, refresh token, state·브라우저 연결 값, 교환 코드, code_verifier를 넣지 않습니다. 프로필·조직 역할은 auth에 두지 않습니다.
- user와의 연동은 ACL 방식 예제로 유지합니다. 조회 결과는 `adapter/out/user`, 이벤트는 `adapter/in/event`에서 auth 소유 값으로 변환하며 Application·Domain에서 user 타입을 참조하지 않습니다. 선택 배경은 [ADR-001](../../../../../../docs/adr/001-backend-architecture.md#예제-모듈)입니다.
- 이벤트 로그는 `userId`, `occurredAt`만 기록합니다.
- 사용자 원본은 user가 소유합니다. 영속 모델·재처리를 추가하면 Auth 문서에 반영합니다.

## 인증·세션 변경 시

- id_token 검증은 서명·발급자·허용 앱 키·클라이언트·만료·nonce 존재를 모두 요구합니다.
- 앱 SDK 흐름의 nonce는 서버 발급 값을 일회성으로 소비하고 미발급·재사용·만료 nonce를 거부하는지 확인합니다. 발급 주체와 따로 묶지 않는 이유는 [카카오 로그인 흐름](../../../../../../docs/domain/auth.md#카카오-로그인-흐름)에 있습니다.
- 웹 Authorization Code 흐름은 state/nonce를 시작 브라우저와 연결하고 콜백에서 일회성·만료·연결을 검증합니다. 다른 브라우저의 유효한 콜백을 수용하지 않는지, 복귀 주소가 설정에 등록된 값뿐인지, Access Token이 URL에 실리지 않고 교환 코드가 시작 클라이언트의 PKCE code_verifier로만 쓰이는지 확인합니다(Apple은 일회성 교환 코드).
- Apple 토큰 API 실패는 Apple이 거절한 `invalid_grant`만 AUTH-005, 그 밖의 오류 응답·통신·설정 오류는 AUTH-006으로 구분합니다. 외부 HTTP를 기다리는 유즈케이스의 트랜잭션은 [트랜잭션 예외](../../../../../../.claude/skills/dino-architecture/references/persistence.md#transactions)를 따릅니다.
- Apple refresh token은 암호화해 저장하고 계정 삭제 유즈케이스는 `RevokeAppleTokenPort`로 철회합니다. 암호문 형식·키를 바꾸면 기존 암호문 복호화 경로를 함께 유지합니다.
- Access Token은 실제 검증기로 서명·시간과 발급자·`token_use`를 확인합니다. 유효한 서명의 잘못된 claim도 거부되는지 검사합니다.
- 폐기·만료·위조 토큰은 어느 경로든 실제 404와 같은 본문으로 응답하고 실패 이유를 구분하지 않습니다. 토큰 없음은 401입니다. 정책을 바꾸면 Auth 문서·ADR·통합 테스트를 함께 바꿉니다.
- Refresh 회전을 추가하면 실제 저장소에서 정상 회전·동시 호출·재사용 철회와 만료 정책을 확인합니다. 전체 세션 철회를 제공하면 수명이 다른 세션이 공존할 때도 모두 철회되는지 검사합니다. 시계와 mock의 검증 범위는 [테스트 규칙](../../../../../../.claude/skills/dino-testing/references/design.md#design)을 따릅니다.
- 공개 경로·필터 등록은 `shared::security` 확장점만 사용하고 별도 SecurityFilterChain을 만들지 않습니다.

## 집중 검증

- Domain: `com.orbit.auth.domain` 패키지의 관련 테스트.
- 유스케이스: `com.orbit.auth.application.service` 패키지의 관련 테스트.
- id_token·Access Token 검증기: `com.orbit.auth.adapter.out.kakao.KakaoIdTokenVerifierTest`, `com.orbit.auth.adapter.out.apple.AppleIdTokenVerifierTest`, `com.orbit.auth.adapter.out.jwt.JwtAccessTokenAdapterTest`.
- Apple 토큰 API: `com.orbit.auth.adapter.out.apple` 패키지의 `AppleClientSecretFactoryTest`, `AppleTokenClientTest`, `AppleWebAuthorizationAdapterTest`(토큰 엔드포인트는 로컬 스텁, 실제 Apple E2E 아님).
- 저장소: `com.orbit.auth.adapter.out.persistence` 패키지의 테스트(계정·Apple refresh token·암호화, Testcontainers PostgreSQL), `com.orbit.auth.adapter.out.redis` 패키지의 테스트(Testcontainers Redis).
- 비밀값 가림·복귀 주소 등록: `com.orbit.auth.application.port.in.command.dto.AppleLoginCommandsTest`, `com.orbit.auth.adapter.in.web.AppleWebReturnPropertiesTest`.
- 필터·API: `com.orbit.auth.adapter.in.web.security.AccessTokenAuthenticationFilterTest`, `com.orbit.auth.adapter.in.web.AuthApiIntegrationTest`, `AppleLoginApiIntegrationTest`, `AppleWebLoginApiIntegrationTest`(JWKS·토큰 엔드포인트 스텁 서버로 실제 조회·검증·교환 경로 통과, 제공자 E2E 아님).
- 경계 변환: `com.orbit.auth.adapter.out.user.UserSubjectAdapterTest`, `com.orbit.auth.adapter.in.event.UserRegisteredListenerTest`.
- 모듈 조립: `com.orbit.auth.AuthModuleTest`.
- HTTP·OpenAPI·이벤트·모듈 경계는 [공통 검사 표](../../../../../../.claude/skills/dino-testing/references/verification.md#selection)를 따릅니다.
