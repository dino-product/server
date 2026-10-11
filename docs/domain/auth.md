# Auth 모듈

## 책임과 범위

카카오·Apple OIDC로 사용자를 인증하고, 제공자 `sub`에 연결된 계정(`Account`)을 발급·조회하며, 자체 Access Token을 발급·검증·폐기합니다. 계정 삭제 때 Apple 토큰을 철회할 수 있도록 Apple refresh token을 암호화해 보관합니다. 계정의 식별자 발급까지가 책임이며 이름·연락처 같은 프로필과 조직 역할은 소유하지 않습니다([BC-001](bounded-contexts.md#미해결-설계-이슈)).

현재 구현 범위는 카카오 앱(네이티브 SDK)의 id_token 제출 로그인, Apple iOS의 id_token·authorization code 제출 로그인, Apple 웹·Android의 서버 authorize·callback 로그인, Access Token 발급, Bearer 인증, 로그아웃입니다. 카카오 웹 흐름, Refresh Token 회전, 계정 삭제(탈퇴) 유즈케이스, 프로필·역할, organization 공개 계약은 아직 없습니다. Apple 로그인은 Notion 정책서 반영 전에 구현했습니다([A-10](bounded-contexts.md#auth-gaps)). 예제 subject 조회와 등록 이벤트 후속 처리는 user와의 ACL 관계를 보여주는 구조 예제로 남아 있으며 인증 의미가 없습니다. 예제 subject는 인증 증명이 아니며 접근 권한을 부여하지 않습니다.

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

## Apple 로그인 흐름

```text
[iOS] POST /api/v1/auth/apple/nonces
  → IssueAppleLoginNonceUseCase → IssueAppleLoginNonceService
  → AppleLoginNoncePort → RedisAppleLoginNonceAdapter (raw nonce 응답, SHA-256 hex만 5분 보관)

[iOS] POST /api/v1/auth/apple/login {idToken, authorizationCode}
  → LoginWithAppleCommand → LoginWithAppleUseCase → LoginWithAppleService
  → VerifyAppleIdTokenPort → AppleIdTokenVerifier (JWKS 서명·iss·허용 aud·exp, sub·nonce 존재)
  → AppleLoginNoncePort.consume (nonce 클레임의 해시, 일회성)
  → ExchangeAppleAuthorizationCodePort → AppleTokenClient (/auth/token, ES256 client_secret, 교환 id_token의 sub 일치)
  → AccountRepository.findByIdentity / saveNew → AppleRefreshTokenRepository.save (암호화)
  → AccessTokenPort.issue → LoginInfo → LoginResponse

[웹·Android] GET /api/v1/auth/apple/authorize?client=web|android&code_challenge={S256}[&code_challenge_method=S256]
  → StartAppleWebLoginUseCase → StartAppleWebLoginService
  → AppleWebLoginStatePort ((state, 브라우저 연결 해시) → nonce 해시·복귀 주소·PKCE challenge, 10분)
  → AppleWebAuthorizationPort → 302 Apple 인가 주소 + 브라우저 연결 쿠키

[웹·Android] POST /api/v1/auth/apple/callback (Apple form_post: state, code, id_token, user, error)
  → CompleteAppleWebLoginUseCase → CompleteAppleWebLoginService
  → state·브라우저 연결로 일회성 소비 → id_token(웹 Services ID, state의 nonce) → code 교환(redirect_uri)
  → 계정 조회/등록 → refresh token 보관 → AppleLoginExchangePort (60초 교환 코드 + PKCE challenge)
  → 302 복귀 주소?code=… 또는 ?error=…

[웹·Android] POST /api/v1/auth/apple/exchange {code, codeVerifier}
  → ExchangeAppleWebLoginUseCase → ExchangeAppleWebLoginService (S256(codeVerifier) = challenge)
  → AccessTokenPort.issue → LoginResponse
```

- iOS 앱은 raw nonce를 받아 UTF-8 SHA-256 소문자 hex를 Apple 요청 nonce에 넣습니다. Apple은 그 값을 id_token에 그대로 싣고 서버는 해시로 보관·대조하므로, raw 값을 그대로 넣은 토큰은 AUTH-003입니다. Apple nonce는 카카오 nonce와 키 공간이 달라 카카오용 nonce로 Apple 로그인할 수 없습니다.
- iOS 로그인은 앱 클라이언트(Bundle ID)로 받은 토큰만 받습니다. 웹 Services ID의 code는 콜백 주소와 함께만 교환되므로, 웹 Services ID 대상 id_token은 iOS 경로에서 AUTH-002입니다.
- 검증기는 Apple JWKS(`https://appleid.apple.com/auth/keys`)의 RS256 키, 발급자 `https://appleid.apple.com`, 허용 클라이언트 목록(iOS Bundle ID·웹/Android Services ID), 만료(60초 오차)를 확인하고 `sub`·`nonce`를 요구합니다. 카카오와 같은 공통 골격(`adapter/out/oidc`)을 씁니다. Apple `sub`는 개발자 팀 단위로 같으므로 허용 목록의 다른 클라이언트로 받은 토큰도 같은 계정이며, 카카오 계정과는 별개입니다(자동 연결 없음).
- authorization code는 id_token을 받은 클라이언트(`aud`)로 교환하고, 교환 응답 id_token을 다시 검증해 `sub`가 같을 때만 계정을 처리합니다. 교환 실패는 로그인 실패입니다. Apple이 code를 거절했거나(만료·재사용) 다른 사용자의 code면 AUTH-005(401), Apple 장애·통신·client_secret 설정 오류면 AUTH-006(502)입니다. nonce는 교환 전에 소비하므로 실패하면 nonce부터 다시 받습니다.
- client_secret은 `.p8` 개인키로 ES256 서명한 JWT(`kid`, `iss`=Team ID, `sub`=client_id, `aud`=`https://appleid.apple.com`)이며 클라이언트별로 유효 기간(기본 10분)의 절반까지 재사용합니다. 개인키는 처음 쓸 때 해석합니다.
- 웹·Android 클라이언트는 PKCE code_verifier(43~128자)를 만들어 보관하고 그 S256 값을 `code_challenge`로 보냅니다(`code_challenge_method`는 생략하거나 `S256`만, 그 밖이나 challenge 형식 오류는 COMMON-400). 서버는 state·nonce를 만들고 시작한 브라우저에 연결 쿠키(`apple_login_binding`, `HttpOnly; Secure; SameSite=None; Path=/api/v1/auth/apple`)를 남깁니다. Apple 콜백은 다른 사이트에서 오는 form POST라 `SameSite=None`이 필요합니다.
- state는 브라우저 연결 값의 해시와 함께 키로 보관하므로, state가 없거나 재사용됐거나 연결 쿠키가 다르면 찾지 못하고 리다이렉트 없이 AUTH-003(401)으로 끝냅니다. 연결 쿠키가 없는 위조 콜백은 정상 로그인의 state를 지우지 못합니다.
- 콜백의 그 밖의 실패는 복귀 주소에 `error`만 싣습니다(id_token AUTH-002, nonce AUTH-003, code AUTH-005, Apple 장애·Apple이 보낸 취소 외 오류 AUTH-006, 사용자 취소 AUTH-008, 예상하지 못한 내부 오류 COMMON-500). 성공하면 Access Token 대신 60초짜리 일회성 교환 코드를 싣고, 클라이언트가 `/exchange`에 code_verifier와 함께 보내 토큰을 받습니다. 토큰이 URL·브라우저 기록·Referer에 남지 않고, 복귀 주소(특히 Android 앱 스킴)를 가로채도 code_verifier 없이 쓸 수 없게 하려는 방식입니다. 교환 코드가 없거나 재사용되거나 verifier가 틀리면 AUTH-007이며 그 코드는 다시 쓸 수 없습니다.
- 교환 코드를 쓰지 않으면 계정은 만들어진 채로 남아 다음 로그인은 `registered: false`입니다.
- 복귀 주소는 `client` 이름(`web`, `android`)별로 설정에 등록한 값만 씁니다. 그 밖의 값은 COMMON-400입니다.
- Apple이 첫 승인 때만 주는 이름·이메일(`user`, id_token의 `email`)은 저장하지 않습니다([BC-001](bounded-contexts.md#미해결-설계-이슈)).
- Apple 로그인 유즈케이스(iOS 로그인·콜백)는 Apple 토큰 API를 기다리는 동안 DB 커넥션을 잡지 않도록 메서드 트랜잭션을 두지 않습니다. 계정 등록은 저장소가 별도 트랜잭션으로 확정하고 refresh token은 계정 확정 뒤 upsert하므로, 보관이 실패해도 다음 로그인에서 다시 저장됩니다.

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
- 토큰이 없으면 익명으로 넘겨 인증이 필요한 자원은 `COMMON-401`을 받습니다. 토큰이 있는데 폐기·만료·위조됐으면 실제 존재하지 않는 자원과 같은 `COMMON-404` 응답으로 요청을 끝내 자원 존재를 드러내지 않습니다. 실패 이유는 응답으로 구분하지 않습니다. 카카오·Apple 로그인 경로(`/api/v1/auth/kakao/**`, `/api/v1/auth/apple/**`)만 예외로 토큰을 검사하지 않아 만료·로그아웃된 토큰을 아직 들고 있는 클라이언트도 다시 로그인할 수 있습니다.
- 이 정책의 알려진 한계: 경로는 존재하지만 대상 레코드가 없을 때의 모듈 오류(`AUTH-001`, `AUTH-004`)는 `COMMON-404`와 본문이 다르므로, 유효한 토큰으로 접근한 결과와 무효 토큰의 404는 구분됩니다. 무효 토큰 응답끼리는 구분되지 않습니다.
- `GET /api/v1/auth/me`는 토큰 클레임만 믿지 않고 계정을 저장소에서 확인합니다. 계정이 삭제됐으면 `AUTH-004`(HTTP 404)입니다.
- 로그아웃은 해당 토큰만 폐기하며 같은 계정의 다른 토큰은 유지합니다. 만료 뒤에는 검증기가 먼저 거부하므로 폐기 기록은 남은 유효 시간만 보관합니다.
- 권한(authorities)은 비어 있습니다. 조직 역할에 따른 인가는 organization 공개 계약을 연결할 때 설계합니다.

## Domain과 저장 모델

- `Account`: 외부 식별 목록(제공자당 하나)과 등록 시각을 가진 애그리게잇 루트. `register`로 첫 로그인에 만들고 `reconstitute`로 복원하며 빈 목록·중복 제공자를 거부합니다.
- `ExternalIdentity(provider, subject)`: 제공자가 준 `sub`를 그대로 보관하는 값. `OAuthProvider`는 `KAKAO`, `APPLE`입니다.
- `HashedNonce`: 서버가 발급한 raw 값(nonce·브라우저 연결)의 SHA-256 소문자 hex. id_token nonce 클레임은 대소문자를 구분하지 않고 읽습니다.
- `PkceChallenge`: RFC 7636 S256 code_challenge(base64url 43자). code_verifier의 SHA-256과 일정 시간 비교합니다.
- `AccessToken(tokenId, accountId, issuedAt, expiresAt)`: 발급·검증 어댑터가 오가는 클레임 값. 유효 구간의 정합성을 소유합니다. 로그아웃 폐기 기간(남은 유효 시간) 계산은 `LogoutService`가 합니다.
- 테이블은 `accounts`(id, registered_at), `oauth_credentials`(id, account_id, provider, oauth_uid, `provider+oauth_uid` 유니크), `apple_refresh_tokens`(id, account_id, client_id, encrypted_token, updated_at, `account_id+client_id` 유니크)입니다. Apple refresh token은 계정·클라이언트마다 최신 값 하나를 PostgreSQL upsert로 남기고, AES-256-GCM(`v1:` + base64(IV·암호문·태그), 계정·클라이언트 문맥을 AAD로 묶음)으로 암호화합니다.
- Redis 키는 `auth:login-nonce:{nonce}`(카카오), `auth:apple-login-nonce:{해시}`(Apple iOS, 5분), `auth:apple-web-login:{state}:{연결 해시}`(웹·Android, 10분), `auth:apple-login-exchange:{교환 코드 해시}`(60초), `auth:revoked-access-token:{jti}`입니다. 교환 코드 값에는 Access Token이 아니라 계정 식별자·등록 여부·PKCE challenge만 둡니다.

## 설정

| 속성 | 환경 변수 | 설명 |
| --- | --- | --- |
| `app.auth.kakao.allowed-audiences` | `KAKAO_ALLOWED_AUDIENCES` | id_token `aud`로 허용할 카카오 앱 키 목록. 앱은 네이티브 앱 키, 웹은 REST API 키를 받으므로 둘 다 나열. 모든 키는 하나의 카카오 앱에 속해야 하며 다른 앱의 키를 섞으면 앱별 회원번호(`sub`)가 같은 사용자가 한 계정으로 합쳐집니다 |
| `app.auth.kakao.issuer`, `jwk-set-uri` | — | 카카오 고정값. 테스트는 JWKS 주소만 로컬 스텁으로 바꿉니다 |
| `app.auth.apple.allowed-audiences` | `APPLE_ALLOWED_AUDIENCES` | id_token `aud`로 허용할 Apple 클라이언트 목록(iOS Bundle ID, 웹·Android Services ID). 같은 Apple 개발자 팀의 클라이언트만 나열합니다 |
| `app.auth.apple.issuer`, `jwk-set-uri` | — | Apple 고정값. 테스트는 JWKS 주소만 로컬 스텁으로 바꿉니다 |
| `app.auth.apple.client-secret.team-id`, `key-id`, `private-key`, `ttl` | `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`, `APPLE_CLIENT_SECRET_TTL` | client_secret 서명 정보. 개인키는 `.p8` 내용(줄바꿈을 `\n`으로 적은 PEM 또는 base64)이며 처음 쓸 때 해석합니다. TTL 기본 `PT10M`, Apple 상한 15777000초 |
| `app.auth.apple.token-api.token-uri`, `revoke-uri`, `timeout` | — | Apple 토큰·철회 엔드포인트와 호출 제한 시간(5초) |
| `app.auth.apple.refresh-token.encryption-key` | `APPLE_REFRESH_TOKEN_ENCRYPTION_KEY` | refresh token 저장 암호화 키(base64 32바이트). 길이가 맞지 않으면 기동 실패 |
| `app.auth.apple.web.authorization-uri` | — | Apple 인가 주소 고정값 |
| `app.auth.apple.web.services-id`, `redirect-uri` | `APPLE_SERVICES_ID`, `APPLE_WEB_REDIRECT_URI` | 웹·Android용 Services ID와 그 Services ID에 등록한 콜백 주소. Apple은 HTTPS 공개 도메인의 콜백만 받으므로 로컬에서는 HTTPS 터널 등이 필요합니다. Services ID가 허용 목록에 없으면 기동 실패 |
| `app.auth.apple.web.return-uris.web`, `.android` | `APPLE_WEB_RETURN_URI`, `APPLE_ANDROID_RETURN_URI` | 콜백 뒤 돌아갈 웹 주소와 Android 앱 스킴 주소(절대 주소) |
| `app.auth.jwt.secret` | `AUTH_JWT_SECRET` | HS256 비밀키. 32바이트 미만이면 기동 실패 |
| `app.auth.jwt.issuer`, `access-token-ttl` | `AUTH_JWT_ISSUER`, `AUTH_ACCESS_TOKEN_TTL` | 발급자(기본 `orbit`)와 유효 기간(기본 `PT1H`) |
| `spring.data.redis.host`, `port` | `REDIS_HOST`, `REDIS_PORT` | nonce·state·교환 코드·폐기 토큰 저장소. 운영은 기본값 없이 받습니다 |

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

- 로그인·nonce·웹 로그인 시작·콜백·교환·로그아웃 계약과 값: `application/port/in/command`, `command/dto`. 토큰 인증과 예제 조회: `application/port/in/query`, `query/dto`.
- 출력 Port: `application/port/out`. 구현은 `adapter/out/oidc`(id_token 검증 공통 골격), `adapter/out/kakao`(카카오 id_token 검증), `adapter/out/apple`(Apple id_token 검증·client_secret·토큰 교환·철회·웹 인가 주소), `adapter/out/jwt`(Access Token), `adapter/out/redis`(nonce·state·교환 코드·폐기 토큰), `adapter/out/persistence`(계정·Apple refresh token), `adapter/out/user`(예제)에 둡니다.
- HTTP: `adapter/in/web`의 `KakaoLoginController`, `AppleLoginController`(iOS), `AppleWebLoginController`(웹·Android), `AuthSessionController`, 예제 `AuthExampleController`. 인증 필터·principal·공개 경로 확장점은 `adapter/in/web/security`입니다.
- 오류: `AuthErrorCode`의 `AUTH-001`(예제 사용자 없음, 404), `AUTH-002`(id_token 무효, 401), `AUTH-003`(nonce·state 무효, 401), `AUTH-004`(계정 없음, 404), `AUTH-005`(Apple code 거절·다른 사용자, 401), `AUTH-006`(Apple 통신 실패·Apple이 보낸 취소 외 오류, 502), `AUTH-007`(Apple 웹 교환 코드·code_verifier 무효, 401), `AUTH-008`(Apple 로그인 취소, 웹 복귀 주소의 `error` 값으로만 사용). 폐기·만료·위조 토큰의 404는 `CommonErrorCode.NOT_FOUND`와 같은 본문입니다.
- 계정 삭제 준비: `RevokeAppleTokenPort`가 저장한 refresh token을 발급받은 클라이언트로 `/auth/revoke`에 철회합니다. 호출할 계정 삭제 유즈케이스는 탈퇴 정책([조직·계정] §5.1, ORG-009)과 소유 컨텍스트(BC-001)가 정해진 뒤 추가합니다.
- 미구현·운영 전 과제: nonce·authorize 발급 경로의 호출 제한, `accounts`·`oauth_credentials`·`apple_refresh_tokens`(유니크 제약 포함) 스키마 마이그레이션과 `oauth_credentials.provider`의 `APPLE` 값 허용(Hibernate가 만든 enum check 제약이 있는 DB) (현재 마이그레이션 도구 없음, [프로필 규칙](../../.claude/skills/dino-architecture/references/persistence.md#profiles)), refresh token 암호화 키 교체 절차, Refresh 회전, 카카오 웹 흐름, 계정 삭제 유즈케이스, 실제 Apple 계정 E2E(Sandbox) 확인.
- 현재 다른 모듈에 공개하는 타입은 없습니다. organization이 참조할 계정 존재 확인 계약은 그 모듈을 구현할 때 모듈 루트에 추가합니다. 허용 의존성은 [도메인 지도](README.md)가 관리합니다.

예제의 선택 배경은 [ADR-001 예제 모듈](../adr/001-backend-architecture.md#예제-모듈), 인증 결정의 배경은 [ADR-001 인증과 계정](../adr/001-backend-architecture.md#인증과-계정)을 따릅니다.
