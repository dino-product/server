# Auth 모듈

## 책임과 범위

카카오·Apple OIDC로 사용자를 인증하고, 제공자 `sub`에 연결된 계정(`Account`)을 발급·조회·폐쇄하며, 자체 Access Token을 발급·검증·폐기합니다. 계정을 닫으면 외부 식별을 끊고 그 계정의 토큰을 거부하며, 커밋 뒤 카카오 연결 끊기·Apple 토큰 철회를 시도합니다. Apple 토큰을 철회할 수 있도록 Apple refresh token을 암호화해 보관합니다. 계정의 식별자 발급까지가 책임이며 이름·연락처 같은 가입 프로필·가입 상태·약관 동의와 탈퇴는 [profile](profile.md#profile), 조직 역할은 organization이 소유합니다.

현재 구현 범위는 카카오 앱(네이티브 SDK)의 id_token 제출 로그인, Apple iOS의 id_token·authorization code 제출 로그인, Apple 웹·Android의 서버 authorize·callback 로그인, Access Token 발급, Bearer 인증, 로그아웃, 탈퇴를 소유한 profile이 부르는 [계정 폐쇄](#계정-폐쇄-흐름)와 [외부 연결 해제](#외부-연결-해제-흐름)입니다. 카카오 웹 흐름, Refresh Token 회전, 조직 역할, organization 공개 계약은 아직 없습니다. Apple 로그인은 Notion 정책서 반영 전에 구현했습니다([A-10](bounded-contexts.md#auth-gaps)). 예제 subject 조회와 등록 이벤트 후속 처리는 user와의 ACL 관계를 보여주는 구조 예제로 남아 있으며 인증 의미가 없습니다. 예제 subject는 인증 증명이 아니며 접근 권한을 부여하지 않습니다.

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
- Apple이 첫 승인 때만 주는 이름·이메일(`user`, id_token의 `email`)은 저장하지 않습니다. 이름은 가입 때 [profile](profile.md#profile)이 따로 받습니다.
- Apple 로그인 유즈케이스(iOS 로그인·콜백)는 Apple 토큰 API를 기다리는 동안 DB 커넥션을 잡지 않도록 메서드 트랜잭션을 두지 않습니다. 계정 등록은 저장소가 별도 트랜잭션으로 확정하고 refresh token은 계정 확정 뒤 upsert하므로, 보관이 실패해도 다음 로그인에서 다시 저장됩니다.

## 인증과 로그아웃 흐름

```text
Authorization: Bearer {accessToken}
  → AccessTokenAuthenticationFilter (shared::security 확장점으로 등록)
  → AuthenticateAccessTokenQuery → AuthenticateAccessTokenUseCase → AuthenticateAccessTokenService
  → AccessTokenPort.parse (서명·iss·exp·token_use) → RevokedAccessTokenPort.isRevoked → ClosedAccountPort.isClosed
  → AccessTokenInfo → AuthenticatedAccount principal

GET  /api/v1/auth/me      → GetAccountQuery → GetAccountUseCase → GetAccountService → AccountRepository.findById
                          → AuthenticatedAccountResponse(accountId, registeredAt, accessTokenExpiresAt)
POST /api/v1/auth/logout  → LogoutCommand → LogoutUseCase → LogoutService
                          → RevokedAccessTokenPort.revoke (남은 유효 시간만큼 Redis 보관) → 204
```

- Access Token은 HS256 JWT이며 `iss`, `sub`(accountId), `jti`, `iat`, `exp`, `token_use=access`를 담습니다. 만료는 시계 오차 없이 정각부터 거부합니다.
- 토큰이 없으면 익명으로 넘겨 인증이 필요한 자원은 `COMMON-401`을 받습니다. 토큰이 있는데 폐기·만료·위조됐거나 닫힌 계정의 토큰이면 실제 존재하지 않는 자원과 같은 `COMMON-404` 응답으로 요청을 끝내 자원 존재를 드러내지 않습니다. 실패 이유는 응답으로 구분하지 않습니다. 카카오·Apple 로그인 경로(`/api/v1/auth/kakao/**`, `/api/v1/auth/apple/**`)만 예외로 토큰을 검사하지 않아 만료·로그아웃된 토큰을 아직 들고 있는 클라이언트도 다시 로그인할 수 있습니다.
- 이 정책의 알려진 한계: 경로는 존재하지만 대상 레코드가 없을 때의 모듈 오류(`AUTH-001`, `AUTH-004`)는 `COMMON-404`와 본문이 다르므로, 유효한 토큰으로 접근한 결과와 무효 토큰의 404는 구분됩니다. 무효 토큰 응답끼리는 구분되지 않습니다.
- `/api/v1/auth/**` 전체는 profile [가입 공통 검사](profile.md#profile-signup-gate)의 허용 목록이라 가입 미완료·필수 약관 재동의 전 계정도 로그인·로그아웃·`/me`를 쓸 수 있습니다. 가입 전에 써야 하는 auth API를 이 접두사 밖에 두면 profile의 허용 목록도 함께 고칩니다.
- `GET /api/v1/auth/me`는 토큰 클레임만 믿지 않고 계정을 저장소에서 확인합니다. 계정이 없거나 닫혔으면(`closed_at`, Redis 폐쇄 표시와 무관) `AUTH-004`(HTTP 404)입니다.
- 로그아웃은 해당 토큰만 폐기하며 같은 계정의 다른 토큰은 유지합니다. 만료 뒤에는 검증기가 먼저 거부하므로 폐기 기록은 남은 유효 시간만 보관합니다.
- 권한(authorities)은 비어 있습니다. 다른 모듈이 `AccountPrincipal`로 auth에 의존하게 되므로 auth는 조직 역할을 조회하지 않습니다. 역할에 따른 인가를 소비 모듈의 Application이 organization 계약으로 확인할지, organization이 보안 확장점으로 채울지는 organization 공개 계약을 연결할 때 정합니다.

## 계정 폐쇄 흐름

제품 규칙: 탈퇴 처리·재가입은 [조직·계정] 정책 §5.1, 기능명세 §6·§7. 탈퇴 API와 차단 판정은 [profile 탈퇴](profile.md#profile-withdrawal)가 소유합니다.

```text
profile WithdrawService (탈퇴 트랜잭션)
  → AccountClosure.close(accountId) → AccountClosureService (호출자 트랜잭션에 참여)
  → AccountRepository.findByIdForUpdate (계정 행 PESSIMISTIC_WRITE, MANDATORY) — 없으면 AUTH-004
  → 이미 닫힌 계정: DB를 바꾸지 않고 원래 closedAt으로 AccountClosed 발행
  → 열린 계정: Account.close(now) → ProviderUnlinkRepository.saveAll (provider_unlinks)
             → AccountRepository.update (oauth_credentials 삭제·closed_at, MANDATORY) → AccountClosed 발행

[호출자 커밋 뒤, 같은 스레드] AccountClosedListener
  → RevokeAccountTokensUseCase → RevokeAccountTokensService
  → ClosedAccountPort.markClosed → RedisClosedAccountAdapter (auth:closed-account:{accountId})

[1분 주기] ClosedAccountReconfirmScheduler → ReconfirmClosedAccountsUseCase → ReconfirmClosedAccountsService
  → AccountRepository.listClosedSince(지금 − 토큰 수명 − 5분) → 계정마다 폐쇄 표시
```

- 계정 행은 남기고(`closed_at`) 외부 식별(`oauth_credentials`)만 지웁니다. 같은 카카오·Apple 계정으로 다시 로그인하면 새 계정이 등록되고(`registered: true`), 다른 모듈의 기록은 옛 계정 식별자를 계속 가리킵니다.
- 호출자 트랜잭션에 참여하므로 탈퇴가 롤백되면 외부 식별·연결 해제 대기·폐쇄 표시가 모두 그대로입니다. 잠금 조회·저장 포트는 `Propagation.MANDATORY`라 트랜잭션 밖에서 부르면 실패합니다. `close`가 던진 예외(AUTH-004 포함)는 호출자 트랜잭션을 롤백 전용으로 만듭니다.
- 계정 행을 `PESSIMISTIC_WRITE`로 잠가 같은 계정의 동시 폐쇄를 줄 세웁니다. 잠금은 호출자 트랜잭션이 끝날 때 풀립니다. PostgreSQL은 바깥 조인의 nullable 쪽을 잠글 수 없어 계정 행만 잠그고 외부 식별은 지연 로딩합니다.
- 이미 닫힌 계정을 다시 닫으면 DB를 바꾸지 않고 원래 폐쇄 시각으로 폐쇄 사실만 다시 알립니다(멱등). 커밋 뒤 폐쇄 표시 저장이 실패했어도 다시 닫으면 복구됩니다.
- 외부 식별을 지우기 전에 계정·제공자마다 연결 해제 대기(`ProviderUnlink`)를 남깁니다. 처리는 [외부 연결 해제 흐름](#외부-연결-해제-흐름)입니다.
- 폐쇄 표시: 인증은 DB 없이 동작하고 계정별 발급 토큰 목록이 없으므로, 커밋 뒤 Redis에 계정 단위 표시를 남겨 매 요청 대조합니다. 보관 기간은 `closedAt + Access Token 수명 + 5분`까지 남은 시간입니다. 5분은 폐쇄 직전에 시작한 로그인이 폐쇄 뒤에 발급한 토큰과 서버 간 시계 차이를 덮는 여유입니다. 남은 기간이 1초 미만이면 저장하지 않습니다. 다시 표시할 때도 원래 폐쇄 시각 기준으로 남은 기간만 남깁니다.
- 인증 대조: 폐기 목록 다음에 폐쇄 표시를 대조해 닫힌 계정의 토큰을 폐기 토큰과 같은 `COMMON-404`로 끝냅니다. Apple 웹·Android 교환(`/exchange`)은 콜백 뒤 교환 전에 계정이 닫혔으면 `AUTH-007`로 거부합니다. 교환 코드는 이미 소비했으므로 다시 로그인하면 새 계정이 됩니다.
- 표시는 커밋 뒤 같은 스레드에서 남겨 탈퇴 응답 전에 이전 토큰 거부가 확정됩니다. 저장이 실패해도 커밋된 폐쇄는 유지되고 Spring이 오류를 기록합니다. 1분 주기 재확정이 토큰이 남았을 수 있는 최근 폐쇄 계정의 표시를 원래 폐쇄 시각 기준으로 되살립니다.

## 외부 연결 해제 흐름

```text
[폐쇄 커밋 뒤, 다른 스레드] AccountClosedUnlinkListener (트랜잭션 없음)
  → UnlinkAccountProvidersUseCase → UnlinkAccountProvidersService
  → ProviderUnlinkRepository.listByAccount → 실패한 적 없는 대기만 ProviderUnlinker.process

[1분 주기] ProviderUnlinkRetryScheduler → RetryProviderUnlinksUseCase → RetryProviderUnlinksService
  → ProviderUnlinkRepository.listDue(지금, 50) (다음 시도 시각이 이른 순) → ProviderUnlinker.process

ProviderUnlinker.process(대기)
  카카오 → UnlinkKakaoAccountPort → KakaoUnlinkClient (POST /v1/user/unlink, KakaoAK 어드민 키, target_id_type=user_id)
  Apple  → AppleRefreshTokenRepository.listByAccount → 토큰마다 RevokeAppleTokenPort.revoke → 철회·거절이면 토큰 삭제
  완료·거절 → 대기 삭제 / 일시 실패 → ProviderUnlink.afterFailure → 대기 갱신, 요청 7일 뒤를 넘으면 포기
```

- 탈퇴는 제공자 응답을 기다리지 않고, 해제 결과와 상관없이 폐쇄는 유지됩니다. 전달 보장은 이벤트가 아니라 `provider_unlinks` 대기와 주기 재시도가 맡습니다([ADR-001 인증과 계정](../adr/001-backend-architecture.md#인증과-계정)).
- 카카오는 같은 카카오 앱의 서비스 앱 어드민 키로 회원번호의 앱 연결을 끊습니다. 2xx와 `-101`(앱과 연결되지 않은 사용자)은 완료, `-103`(휴면·없는 계정)·`-2`(잘못된 인자)는 거절로 보고 다시 시도하지 않습니다. 그 밖의 오류 응답·어드민 키 오류·통신 실패는 일시 실패입니다.
- Apple은 계정에 보관한 refresh token을 발급 클라이언트마다 `/auth/revoke`로 철회합니다. 철회했거나 Apple이 거절한(이미 무효) 토큰은 지우고, 일시 실패한 토큰만 남겨 다시 시도합니다. 토큰을 모두 정리하면 대기를 지웁니다.
- 재시도 간격은 1분·5분·30분·2시간·12시간, 그 뒤 매일입니다. 다음 시도가 요청 7일 뒤를 넘으면 포기하고 대기와 남은 Apple 토큰을 지우며, WARN 로그에는 계정 식별자·제공자·실패 횟수만 남깁니다. 예상하지 못한 예외(복호화·저장소 오류)도 일시 실패로 봅니다. 결과 저장마저 실패하면 ERROR 로그만 남기고 대기를 그대로 둬 다음 주기에 다시 처리합니다.
- 대기의 첫 재시도 시각은 요청 1분 뒤입니다. 커밋 직후 시도는 실패한 적 없는 대기만 처리하므로 같은 계정을 다시 닫아도 재시도 간격을 건너뛰지 않습니다.
- 대기를 잠그지 않습니다. 이미 끊긴 카카오 사용자·철회된 Apple 토큰의 재해제와 이미 없는 대기의 변경·삭제가 멱등이라, 여러 곳에서 같은 대기를 처리해도 결과가 같습니다. 다만 제공자 호출은 겹칠 수 있습니다.
- 재가입 경쟁을 따지지 않고 항상 해제합니다. 해제 대기 중에 같은 카카오 계정으로 새 계정을 만들면 카카오 회원번호가 앱 단위로 같아 새 계정의 앱 연결도 끊깁니다.
- 해제가 끝나거나 포기할 때까지(최대 7일) 카카오 대기의 회원번호(`provider_unlinks.oauth_uid`)와 Apple refresh token(암호화)을 보관합니다. Apple 대기에는 회원번호를 남기지 않습니다.
- 주기 작업은 `app.auth.provider-unlink.retry-enabled`·`app.auth.closed-account.reconfirm-enabled`가 `true`일 때만 등록되고, 앞선 실행이 끝난 뒤 간격을 두고 다시 돕니다. 테스트 프로필은 끄고 유즈케이스를 직접 부릅니다. `@EnableScheduling`은 `shared.internal.config.SchedulingConfig`에 있습니다.

## Domain과 저장 모델

- `Account`: 외부 식별 목록(제공자당 하나)과 등록 시각, 폐쇄 시각을 가진 애그리게잇 루트. `register`로 첫 로그인에 만들고 `reconstitute`로 복원하며 열린 계정의 빈 목록·중복 제공자를 거부합니다. `close`는 외부 식별을 모두 끊고 끊은 식별마다 `ProviderUnlink`를 돌려주며, 이미 닫힌 계정이면 아무것도 바꾸지 않습니다. 닫힌 계정은 외부 식별 없이 `reconstituteClosed`로 복원합니다.
- `ProviderUnlink`: 닫힌 계정의 제공자 연결 해제 할 일(계정·제공자마다 하나). 실패 횟수와 다음 시도 시각을 가지며 `afterFailure`가 재시도 간격과 7일 포기를 정합니다. 카카오만 회원번호를 가지고 문자열 표현에서 가립니다.
- `AccountClosed`: 계정이 닫혔다는 auth 안의 사실(계정 식별자·폐쇄 시각). 다른 모듈에 공개하지 않습니다.
- `ExternalIdentity(provider, subject)`: 제공자가 준 `sub`를 그대로 보관하는 값. `OAuthProvider`는 `KAKAO`, `APPLE`입니다.
- `HashedNonce`: 서버가 발급한 raw 값(nonce·브라우저 연결)의 SHA-256 소문자 hex. id_token nonce 클레임은 대소문자를 구분하지 않고 읽습니다.
- `PkceChallenge`: RFC 7636 S256 code_challenge(base64url 43자). code_verifier의 SHA-256과 일정 시간 비교합니다.
- `AccessToken(tokenId, accountId, issuedAt, expiresAt)`: 발급·검증 어댑터가 오가는 클레임 값. 유효 구간의 정합성을 소유합니다. 로그아웃 폐기 기간(남은 유효 시간) 계산은 `LogoutService`가 합니다.
- 테이블은 `accounts`(id, registered_at, closed_at), `oauth_credentials`(id, account_id, provider, oauth_uid, `provider+oauth_uid` 유니크), `apple_refresh_tokens`(id, account_id, client_id, encrypted_token, updated_at, `account_id+client_id` 유니크), `provider_unlinks`(id, account_id, provider, oauth_uid, requested_at, failed_attempts, next_attempt_at)입니다. Apple refresh token은 계정·클라이언트마다 최신 값 하나를 PostgreSQL upsert로 남기고, AES-256-GCM(`v1:` + base64(IV·암호문·태그), 계정·클라이언트 문맥을 AAD로 묶음)으로 암호화합니다. 닫힌 계정의 토큰은 철회하거나 해제를 포기하면 지웁니다.
- Redis 키는 `auth:login-nonce:{nonce}`(카카오), `auth:apple-login-nonce:{해시}`(Apple iOS, 5분), `auth:apple-web-login:{state}:{연결 해시}`(웹·Android, 10분), `auth:apple-login-exchange:{교환 코드 해시}`(60초), `auth:revoked-access-token:{jti}`, `auth:closed-account:{accountId}`(폐쇄 표시, 폐쇄 시각 + 토큰 수명 + 5분까지)입니다. 교환 코드 값에는 Access Token이 아니라 계정 식별자·등록 여부·PKCE challenge만 둡니다.

## 설정

| 속성 | 환경 변수 | 설명 |
| --- | --- | --- |
| `app.auth.kakao.allowed-audiences` | `KAKAO_ALLOWED_AUDIENCES` | id_token `aud`로 허용할 카카오 앱 키 목록. 앱은 네이티브 앱 키, 웹은 REST API 키를 받으므로 둘 다 나열. 모든 키는 하나의 카카오 앱에 속해야 하며 다른 앱의 키를 섞으면 앱별 회원번호(`sub`)가 같은 사용자가 한 계정으로 합쳐집니다 |
| `app.auth.kakao.issuer`, `jwk-set-uri` | — | 카카오 고정값. 테스트는 JWKS 주소만 로컬 스텁으로 바꿉니다 |
| `app.auth.kakao.admin-api.admin-key` | `KAKAO_ADMIN_KEY` | 같은 카카오 앱의 서비스 앱 어드민 키. 탈퇴한 계정의 카카오 연결 끊기에만 서버에서 씁니다. 기본값이 없고 비면 기동 실패. 클라이언트에 넣지 않고 문자열 표현에서 가립니다 |
| `app.auth.kakao.admin-api.unlink-uri`, `timeout` | — | 카카오 연결 끊기 엔드포인트 고정값(`https://kapi.kakao.com/v1/user/unlink`)과 호출 제한 시간(5초). 테스트는 닫힌 로컬 주소를 두고, 연결 끊기를 검사하는 테스트만 스텁 주소로 바꿉니다 |
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
| `app.auth.provider-unlink.retry-enabled`, `retry-interval` | — | 외부 연결 해제 주기 재시도의 등록 여부(기본 `true`, 테스트 `false`)와 간격(`PT1M`) |
| `app.auth.closed-account.reconfirm-enabled`, `reconfirm-interval` | — | 최근 폐쇄 계정의 폐쇄 표시 재확정의 등록 여부(기본 `true`, 테스트 `false`)와 간격(`PT1M`) |
| `spring.task.scheduling.pool.size` | — | 주기 작업 스레드 수(2). 해제 재시도가 제공자 장애로 길어져도 폐쇄 표시 재확정이 밀리지 않게 작업마다 하나씩 둡니다 |
| `spring.data.redis.host`, `port` | `REDIS_HOST`, `REDIS_PORT` | nonce·state·교환 코드·폐기 토큰·폐쇄 표시 저장소. 운영은 기본값 없이 받습니다 |

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
- 로그 외 영속 모델은 없습니다. 모듈 리스너라 리스너가 실패하면 [발행 기록](../../.claude/skills/dino-architecture/references/persistence.md#events)에서 다시 받으며, 같은 이벤트를 두 번 받으면 로그가 두 번 남습니다.

```text
auth.domain.AccountClosed (auth 안의 사실, 폐쇄 트랜잭션에서 발행)
  → AccountClosedListener (@TransactionalEventListener, 커밋 뒤 같은 스레드) → RevokeAccountTokensUseCase
  → AccountClosedUnlinkListener (@Async @TransactionalEventListener, 커밋 뒤 다른 스레드) → UnlinkAccountProvidersUseCase
```

두 리스너는 [트랜잭션 이벤트](../../.claude/skills/dino-architecture/references/persistence.md#events)의 `@ApplicationModuleListener`(다른 스레드·별도 트랜잭션) 규칙의 예외입니다. 둘 다 롤백된 폐쇄는 받지 않고 리스너 트랜잭션을 열지 않습니다.

- 토큰 폐기는 같은 스레드에서 처리합니다. 비동기로 처리하면 탈퇴 응답 뒤 짧은 틈에 이전 토큰이 통과합니다. Redis만 쓰므로 트랜잭션이 필요 없습니다.
- 외부 연결 해제는 트랜잭션 없는 비동기 리스너입니다. `@ApplicationModuleListener`는 리스너 트랜잭션을 열어 외부 HTTP(제공자마다 5초 제한)를 기다리는 동안 DB 커넥션을 잡고, 동기 리스너면 탈퇴 응답이 제공자 응답을 기다립니다.
- 리스너가 실패해도 커밋된 폐쇄는 되돌리지 않습니다. 전달 보장은 이벤트 발행 기록이 아니라 폐쇄 표시 재확정과 `provider_unlinks` 주기 재시도가 맡습니다.

## 패키지와 공개 계약

- 로그인·nonce·웹 로그인 시작·콜백·교환·로그아웃과 계정 폐쇄 뒤처리(토큰 폐기·외부 연결 해제·주기 재시도·폐쇄 표시 재확정) 계약과 값: `application/port/in/command`, `command/dto`. 토큰 인증과 예제 조회: `application/port/in/query`, `query/dto`.
- 출력 Port: `application/port/out`. 구현은 `adapter/out/oidc`(id_token 검증 공통 골격), `adapter/out/kakao`(카카오 id_token 검증·관리자 API 연결 끊기), `adapter/out/apple`(Apple id_token 검증·client_secret·토큰 교환·철회·웹 인가 주소), `adapter/out/jwt`(Access Token), `adapter/out/redis`(nonce·state·교환 코드·폐기 토큰·폐쇄 표시), `adapter/out/persistence`(계정·Apple refresh token·연결 해제 대기), `adapter/out/user`(예제)에 둡니다.
- HTTP: `adapter/in/web`의 `KakaoLoginController`, `AppleLoginController`(iOS), `AppleWebLoginController`(웹·Android), `AuthSessionController`, 예제 `AuthExampleController`. 인증 필터·principal·공개 경로 확장점은 `adapter/in/web/security`입니다.
- 이벤트: `adapter/in/event`(user 등록 예제, 계정 폐쇄 뒤 토큰 폐기·외부 연결 해제). 주기 작업: `adapter/in/scheduler`(외부 연결 해제 재시도, 폐쇄 표시 재확정).
- 오류: `AuthErrorCode`의 `AUTH-001`(예제 사용자 없음, 404), `AUTH-002`(id_token 무효, 401), `AUTH-003`(nonce·state 무효, 401), `AUTH-004`(계정 없음·닫힌 계정, 404), `AUTH-005`(Apple code 거절·다른 사용자, 401), `AUTH-006`(Apple 통신 실패·Apple이 보낸 취소 외 오류, 502), `AUTH-007`(Apple 웹 교환 코드·code_verifier 무효, 교환 전에 닫힌 계정, 401), `AUTH-008`(Apple 로그인 취소, 웹 복귀 주소의 `error` 값으로만 사용). 폐기·만료·위조 토큰과 닫힌 계정 토큰의 404는 `CommonErrorCode.NOT_FOUND`와 같은 본문입니다.
- 미구현·운영 전 과제
  - nonce·authorize 발급 경로의 호출 제한, refresh token 암호화 키 교체 절차, Refresh 회전, 카카오 웹 흐름, 실제 Apple 계정 E2E(Sandbox) 확인.
  - 운영 환경의 `KAKAO_ADMIN_KEY` 값. 없으면 기동하지 않습니다.
  - 정상 토큰의 인증마다 Redis를 두 번 왕복합니다(폐기 목록 다음 폐쇄 표시).
  - 다중 인스턴스로 운영하면 연결 해제 대기 조회에 선점(`SKIP LOCKED`)이 없어 같은 대기를 동시에 처리할 수 있습니다. 결과는 같지만 제공자 호출이 겹칩니다.
- 모듈 루트 공개 계약은 `AccountPrincipal`과 `AccountClosure`입니다. 허용 의존성은 [도메인 지도](README.md)가 관리합니다.
  - `AccountPrincipal`: 인증된 요청의 계정 식별자만 담고, 다른 모듈의 웹 어댑터가 `@AuthenticationPrincipal`로 받습니다. 현재 소비 모듈은 profile(프로필·약관·탈퇴 API와 가입 공통 검사), organization(요청자 계정), schedule(작업 API 요청자)입니다. 내부 principal `AuthenticatedAccount`가 구현하며 토큰 식별자·만료 시각은 auth 안에서만 쓰고 문자열 표현에도 토큰 식별자를 남기지 않습니다. organization이 참조할 계정 존재 확인 계약은 그 모듈을 구현할 때 모듈 루트에 추가합니다.
  - `AccountClosure#close(Long)`: 탈퇴를 소유한 모듈이 탈퇴와 같은 트랜잭션에서 계정을 닫습니다. 호출자 트랜잭션에 참여하고, 다시 닫아도 결과가 같으며, 계정이 없으면 AUTH-004입니다([계정 폐쇄 흐름](#계정-폐쇄-흐름)). 소비 모듈은 profile(`WithdrawService`)입니다. profile이 auth에 의존하므로 auth는 profile의 탈퇴를 구독하지 않고 profile이 직접 호출합니다.

예제의 선택 배경은 [ADR-001 예제 모듈](../adr/001-backend-architecture.md#예제-모듈), 인증 결정의 배경은 [ADR-001 인증과 계정](../adr/001-backend-architecture.md#인증과-계정)을 따릅니다.
