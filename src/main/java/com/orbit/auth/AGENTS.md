# Auth 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 구현 범위·공개 계약·흐름은 [Auth 문서](../../../../../../docs/domain/auth.md)의 관련 절이 원본입니다.

- 인증 성공·토큰·세션은 검증을 통과한 카카오 id_token과 서버 발급 nonce로만 만듭니다. 예제 subject나 사용자 식별자만으로 만들지 않습니다.
- Domain은 Spring·JPA·Web·Nimbus 타입에 의존하지 않습니다. 시각은 호출자가 UTC `Instant`로 넘기고 서비스는 주입받은 `Clock`을 씁니다.
- 카카오 sub는 `ExternalIdentity`로만 보관하고 이메일·닉네임을 식별에 쓰지 않습니다. 프로필·조직 역할은 auth에 두지 않습니다.
- user의 공개 조회 결과는 `adapter/out/user`, 공개 이벤트는 `adapter/in/event`에서 auth 소유 값으로 변환합니다. 이벤트 입력은 auth의 입력 Port에 위임합니다. user 내부 타입·Repository를 참조하거나 Application·Domain에서 user 타입을 참조하지 않습니다.
- 등록 커밋 후 별도 스레드·트랜잭션에서 소비하며 롤백 시 처리하지 않습니다. 로그는 `userId`, `occurredAt`만 기록합니다.

## 인증·세션 변경 시

- id_token 검증은 서명·발급자·허용 앱 키·만료·nonce 존재를 모두 요구합니다. nonce는 서버 발급 값을 일회성으로 소비하며 다른 클라이언트의 유효한 id_token을 수용하지 않는지 확인합니다.
- Access Token은 실제 검증기로 서명·시간과 발급자·`token_use`를 확인합니다. 유효한 서명의 잘못된 claim도 거부되는지 검사합니다.
- 폐기·만료·위조 토큰은 어느 경로든 실제 404와 같은 본문으로 응답하고 실패 이유를 구분하지 않습니다. 토큰 없음은 401입니다. 정책을 바꾸면 Auth 문서·ADR·통합 테스트를 함께 바꿉니다.
- Refresh 회전을 추가하면 실제 저장소에서 정상 회전·동시 호출·재사용 철회와 만료 정책을 확인합니다. 전체 세션 철회를 제공하면 수명이 다른 세션이 공존할 때도 모두 철회되는지 검사합니다. 시계와 mock의 검증 범위는 [테스트 규칙](../../../../../../docs/conventions/testing/design.md#design)을 따릅니다.
- 공개 경로·필터 등록은 `shared::security` 확장점만 사용하고 별도 SecurityFilterChain을 만들지 않습니다.

## 집중 검증

- Domain: `com.orbit.auth.domain` 패키지의 관련 테스트.
- 유스케이스: `com.orbit.auth.application.service` 패키지의 관련 테스트.
- id_token·Access Token 검증기: `com.orbit.auth.adapter.out.kakao.KakaoIdTokenVerifierTest`, `com.orbit.auth.adapter.out.jwt.JwtAccessTokenAdapterTest`.
- 저장소: `com.orbit.auth.adapter.out.persistence.AccountPersistenceAdapterTest`, `com.orbit.auth.adapter.out.redis` 패키지의 테스트(Testcontainers Redis).
- 필터·API: `com.orbit.auth.adapter.in.web.security.AccessTokenAuthenticationFilterTest`, `com.orbit.auth.adapter.in.web.AuthApiIntegrationTest`(JWKS 스텁 서버로 실제 조회·검증 경로 통과, 카카오 E2E 아님).
- 경계 변환: `com.orbit.auth.adapter.out.user.UserSubjectAdapterTest`, `com.orbit.auth.adapter.in.event.UserRegisteredListenerTest`.
- 모듈 조립: `com.orbit.auth.AuthModuleTest`.
- HTTP·OpenAPI·이벤트·모듈 경계는 [공통 검사 표](../../../../../../docs/conventions/testing/selection.md#selection)를 따릅니다. 실제 커밋·롤백·비동기 검증을 리스너 직접 호출로 대체하지 않습니다.
