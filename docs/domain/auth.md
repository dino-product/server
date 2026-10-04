# Auth 모듈

## 책임과 범위

카카오 OIDC로 사용자를 인증하고, 카카오 `sub`에 연결된 계정(`Account`)을 발급·조회하며, 자체 Access Token을 발급·검증·폐기합니다. 계정의 식별자 발급까지가 책임이며 이름·연락처 같은 프로필과 조직 역할은 소유하지 않습니다([BC-001](bounded-contexts.md#미해결-이슈)).

현재 구현 범위는 앱(카카오 네이티브 SDK)의 id_token 제출 로그인, Access Token 발급, Bearer 인증, 로그아웃입니다. 웹의 Authorization Code(authorize·callback) 흐름, Refresh Token 회전, 프로필·역할, organization 공개 계약은 아직 없습니다. 예제 subject 조회와 등록 이벤트 후속 처리는 user와의 ACL 관계를 보여주는 구조 예제로 남아 있으며 인증 의미가 없습니다. 예제 subject는 인증 증명이 아니며 접근 권한을 부여하지 않습니다.

## 카카오 로그인 흐름

```text
POST /api/v1/auth/kakao/nonces
  → IssueLoginNonceUseCase → IssueLoginNonceService
  → LoginNoncePort → RedisLoginNonceAdapter (5분 만료)
  → LoginNonceInfo → LoginNonceResponse

POST /api/v1/auth/kakao/login {idToken}
  → LoginWithKakaoCommand → LoginWithKakaoUseCase → LoginWithKakaoService
  → VerifyKakaoIdTokenPort → KakaoIdTokenVerifier (JWKS 서명·iss·aud·exp)
  → LoginNoncePort.consume (일회성)
  → AccountRepository.findByIdentity / saveNew(Account.register)
  → AccessTokenPort.issue → JwtAccessTokenAdapter (HS256)
  → LoginInfo → LoginResponse
```

- 앱은 nonce를 받아 카카오 SDK 로그인에 넘기고, SDK가 돌려준 id_token을 제출합니다. id_token은 로그인 증명일 뿐 보관하지 않으며 카카오 access·refresh 토큰도 받지 않습니다.
- 검증기는 카카오 JWKS(`https://kauth.kakao.com/.well-known/jwks.json`)의 RS256 키로 서명을, 발급자 `https://kauth.kakao.com`, 허용 앱 키 목록(`aud`), 만료(60초 오차 허용)를 확인하고 `sub`·`nonce` 존재를 요구합니다. 하나라도 어긋나면 `AUTH-002`(HTTP 401)입니다.
- nonce는 서버가 발급한 값이어야 하고 한 번만 소비됩니다. 미발급·재사용·만료는 `AUTH-003`(HTTP 401)입니다. 서명이 유효하지 않으면 nonce를 소비하지 않습니다. nonce 소비는 Redis 연산이라 이후 단계(계정 저장·토큰 발급)가 실패해도 되돌리지 않으므로, 클라이언트는 실패 시 nonce부터 다시 받아 로그인합니다.
- 앱 흐름은 브라우저 세션이 없어 nonce를 발급 주체와 따로 묶지 않습니다. nonce 발급은 인증 없는 공개 호출이라 발급 응답에 핸들을 더해도 핸들이 nonce·id_token과 같은 응답·로그인 요청으로 오가므로, id_token을 얻을 수 있는 위치에서는 핸들도 얻을 수 있어 보호가 늘지 않습니다. 재사용은 id_token 서명에 포함된 nonce의 서버 발급 여부·일회성·만료로 막습니다. 웹 흐름을 추가하면 state/nonce를 시작 브라우저와 연결합니다.
- 같은 `sub`의 첫 로그인이 동시에 들어오면 한쪽만 계정을 만들고 다른 쪽은 유니크 제약 위반을 받아 먼저 만들어진 계정을 다시 조회해 씁니다(`registered: false`).
- `ExternalIdentity(KAKAO, sub)`에 연결된 계정이 없으면 등록하고 `registered: true`로, 있으면 같은 계정으로 응답합니다. 이메일·닉네임은 식별에 쓰지 않습니다.

## 인증과 로그아웃 흐름

```text
Authorization: Bearer {accessToken}
  → AccessTokenAuthenticationFilter (shared::security 확장점으로 등록)
  → AuthenticateAccessTokenQuery → AuthenticateAccessTokenUseCase → AuthenticateAccessTokenService
  → AccessTokenPort.parse (서명·iss·exp·token_use) → RevokedAccessTokenPort.isRevoked
  → AccessTokenInfo → AuthenticatedAccount principal

GET  /api/v1/auth/me      → GetAccountQuery → GetAccountUseCase → GetAccountService → AccountRepository.findById
                          → AuthenticatedAccountResponse(accountId, registeredAt, accessTokenExpiresAt)
POST /api/v1/auth/logout  → LogoutCommand → LogoutUseCase → LogoutService
                          → RevokedAccessTokenPort.revoke (남은 유효 시간만큼 Redis 보관) → 204
```

- Access Token은 HS256 JWT이며 `iss`, `sub`(accountId), `jti`, `iat`, `exp`, `token_use=access`를 담습니다. 만료는 시계 오차 없이 정각부터 거부합니다.
- 토큰이 없으면 익명으로 넘겨 인증이 필요한 자원은 `COMMON-401`을 받습니다. 토큰이 있는데 폐기·만료·위조됐으면 실제 존재하지 않는 자원과 같은 `COMMON-404` 응답으로 요청을 끝내 자원 존재를 드러내지 않습니다. 실패 이유는 응답으로 구분하지 않습니다. 카카오 로그인 경로(`/api/v1/auth/kakao/**`)만 예외로 토큰을 검사하지 않아 만료·로그아웃된 토큰을 아직 들고 있는 클라이언트도 다시 로그인할 수 있습니다.
- 이 정책의 알려진 한계: 경로는 존재하지만 대상 레코드가 없을 때의 모듈 오류(`AUTH-001`, `AUTH-004`)는 `COMMON-404`와 본문이 다르므로, 유효한 토큰으로 접근한 결과와 무효 토큰의 404는 구분됩니다. 무효 토큰 응답끼리는 구분되지 않습니다.
- `GET /api/v1/auth/me`는 토큰 클레임만 믿지 않고 계정을 저장소에서 확인합니다. 계정이 삭제됐으면 `AUTH-004`(HTTP 404)입니다.
- 로그아웃은 해당 토큰만 폐기하며 같은 계정의 다른 토큰은 유지합니다. 만료 뒤에는 검증기가 먼저 거부하므로 폐기 기록은 남은 유효 시간만 보관합니다.
- 권한(authorities)은 비어 있습니다. 조직 역할에 따른 인가는 organization 공개 계약을 연결할 때 설계합니다.

## Domain과 저장 모델

- `Account`: 외부 식별 목록(제공자당 하나)과 등록 시각을 가진 애그리게잇 루트. `register`로 첫 로그인에 만들고 `reconstitute`로 복원하며 빈 목록·중복 제공자를 거부합니다.
- `ExternalIdentity(provider, subject)`: 제공자가 준 `sub`를 그대로 보관하는 값. `OAuthProvider`는 현재 `KAKAO`뿐입니다.
- `AccessToken(tokenId, accountId, issuedAt, expiresAt)`: 발급·검증 어댑터가 오가는 클레임 값. 유효 구간의 정합성을 소유합니다. 로그아웃 폐기 기간(남은 유효 시간) 계산은 `LogoutService`가 합니다.
- 테이블은 `accounts`(id, registered_at)와 `oauth_credentials`(id, account_id, provider, oauth_uid, `provider+oauth_uid` 유니크)입니다. Redis 키는 `auth:login-nonce:{nonce}`, `auth:revoked-access-token:{jti}`입니다.

## 설정

| 속성 | 환경 변수 | 설명 |
| --- | --- | --- |
| `app.auth.kakao.allowed-audiences` | `KAKAO_ALLOWED_AUDIENCES` | id_token `aud`로 허용할 카카오 앱 키 목록. 앱은 네이티브 앱 키, 웹은 REST API 키를 받으므로 둘 다 나열. 모든 키는 하나의 카카오 앱에 속해야 하며 다른 앱의 키를 섞으면 앱별 회원번호(`sub`)가 같은 사용자가 한 계정으로 합쳐집니다 |
| `app.auth.kakao.issuer`, `jwk-set-uri` | — | 카카오 고정값. 테스트는 JWKS 주소만 로컬 스텁으로 바꿉니다 |
| `app.auth.jwt.secret` | `AUTH_JWT_SECRET` | HS256 비밀키. 32바이트 미만이면 기동 실패 |
| `app.auth.jwt.issuer`, `access-token-ttl` | `AUTH_JWT_ISSUER`, `AUTH_ACCESS_TOKEN_TTL` | 발급자(기본 `orbit`)와 유효 기간(기본 `PT1H`) |
| `spring.data.redis.host`, `port` | `REDIS_HOST`, `REDIS_PORT` | nonce·폐기 토큰 저장소. 운영은 기본값 없이 받습니다 |

## 예제 subject 조회 흐름

```text
GET /api/v1/auth/examples/subjects/{userId}
  → GetAuthSubjectQuery → GetAuthSubjectUseCase
  → GetAuthSubjectService → LoadAuthSubjectPort
  → UserSubjectAdapter → user.UserLookup
  → UserSummary의 id를 AuthSubject로 변환
  → AuthSubjectInfo → AuthSubjectResponse
```

- 양수 식별자에 해당하는 사용자가 있으면 `subject: "user:{id}"`를 반환합니다. `UserSubjectAdapter`는 `UserSummary`에서 식별자만 선택하여 auth Domain/Application에 전달합니다.
- `AuthSubject`는 양수 식별자와 `user:` subject 이름 규칙을 소유합니다.
- 없는 사용자는 `AuthErrorCode.USER_NOT_FOUND` (`AUTH-001`, HTTP 404), 양수가 아닌 경로 값은 공통 입력 오류 (`COMMON-400`, HTTP 400)를 반환합니다.
- 공개 예제 경로는 `/api/v1/auth/examples/**`입니다. `/api/v1/auth/**` 전체를 공개하지 않습니다.

## 이벤트 흐름

```text
user.UserRegistered
  → UserRegisteredListener
  → RecordUserRegistrationCommand
  → RecordUserRegistrationUseCase
  → RecordUserRegistrationService
```

- 리스너가 공개 이벤트를 auth 소유 Command로 변환합니다. ACL 관계이므로 auth의 Application과 Domain은 user 타입을 참조하지 않습니다.
- 수신한 `userId`, `occurredAt`만 로그로 기록합니다.
- 로그 외 영속 모델·자동 재처리는 없습니다.

## 패키지와 공개 계약

- 로그인·nonce·로그아웃 계약과 값: `application/port/in/command`, `command/dto`. 토큰 인증과 예제 조회: `application/port/in/query`, `query/dto`.
- 출력 Port: `application/port/out`. 구현은 `adapter/out/kakao`(id_token 검증), `adapter/out/jwt`(Access Token), `adapter/out/redis`(nonce·폐기 토큰), `adapter/out/persistence`(계정), `adapter/out/user`(예제)에 둡니다.
- HTTP: `adapter/in/web`의 `KakaoLoginController`, `AuthSessionController`, 예제 `AuthExampleController`. 인증 필터·principal·공개 경로 확장점은 `adapter/in/web/security`입니다.
- 오류: `AuthErrorCode`의 `AUTH-001`(예제 사용자 없음, 404), `AUTH-002`(id_token 무효, 401), `AUTH-003`(nonce 무효, 401), `AUTH-004`(계정 없음, 404). 폐기·만료·위조 토큰의 404는 `CommonErrorCode.NOT_FOUND`와 같은 본문입니다.
- 미구현·운영 전 과제: nonce 발급 경로의 호출 제한, `accounts`·`oauth_credentials` 스키마 마이그레이션(현재 마이그레이션 도구 없음, [프로필 규칙](../../.claude/skills/dino-architecture/references/persistence.md#profiles)), Refresh 회전, 웹 authorize·callback.
- 현재 다른 모듈에 공개하는 타입은 없습니다. organization이 참조할 계정 존재 확인 계약은 그 모듈을 구현할 때 모듈 루트에 추가합니다. 허용 의존성은 [도메인 지도](README.md)가 관리합니다.

예제의 선택 배경은 [ADR-001 예제 모듈](../adr/001-backend-architecture.md#예제-모듈), 인증 결정의 배경은 [ADR-001 인증과 계정](../adr/001-backend-architecture.md#인증과-계정)을 따릅니다.
