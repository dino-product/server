<a id="profile"></a>
# Profile 모듈

- 기준일: 2026-10-16
- 기획 원본: [기획 원본 색인](../planning/README.md#baseline)의 [조직·계정] 정책서·기능명세. 표기(`[조직·계정] §5`)·차이 ID는 색인을 따른다
- 다른 컨텍스트와의 관계: [바운디드 컨텍스트 지도](bounded-contexts.md#profile)

이 문서는 `profile`의 책임·구현 결정·가입 공통 검사·연동 관계와 Notion 기획과의 차이를 소유한다. 제품 규칙(이름·연락처 입력 규칙, 가입 미완료 계정이 쓸 수 있는 기능, 약관 종류, 탈퇴 차단 조건·데이터 처리)은 다시 적지 않고 절 번호로만 참조한다. 모듈 목록·허용 의존성은 [도메인 지도](README.md#모듈별-책임과-공개-계약), 작업 지침은 [profile 지침](../../src/main/java/com/orbit/profile/AGENTS.md)이 원본이다.

## 책임과 범위

계정의 가입 프로필(이름·연락처), 가입 상태와 가입일, 약관 동의 이력과 마케팅 수신 동의, 탈퇴를 소유하고, 가입을 마치지 않았거나 필수 약관 재동의 전인 계정이 가입 마무리에 필요한 API 밖을 쓰지 못하게 막는다. 계정 식별자 발급·외부 식별·토큰과 계정 폐쇄는 [auth](auth.md), 조직 소속·역할은 organization이 소유한다. profile은 auth가 발급한 계정 식별자를 값(`AccountId`)으로만 보관한다.

현재 구현 범위는 프로필 입력·수정, 필수 약관 동의(가입 완료)·재동의, 내 정보와 가입 단계 조회, 마이페이지 마케팅 수신 동의 변경, 탈퇴 가능 여부 확인과 [탈퇴](#profile-withdrawal), [가입 공통 검사](#profile-signup-gate), 다른 모듈에 주는 이름 조회 계약과 탈퇴 연동 계약([공개 계약](#공개-계약))이다. 프로필 사진은 아직 없다([남은 일](#profile-todo)).

## 가입 상태와 이용 가능 판정

제품 규칙: [조직·계정] 정책 §5(가입 미완료 계정)·§5.1(필수 약관 재동의)·§9(계정 상태), 기능명세 §6(상태 전이).

- **가입 미완료는 행이 없는 것으로 시작한다**: 카카오 인증만 마친 계정은 프로필 행이 없고 가입 미완료(`PENDING_SIGNUP`)로 본다. 처음 프로필을 저장할 때 `PENDING_SIGNUP` 행을 만들고, 필수 약관에 모두 동의하면 `ACTIVE`가 되며 그 시각을 가입일로 남긴다. 프로필 저장은 가입 상태를 바꾸지 않는다.
- **이용 가능 = `ACTIVE` ∧ 필수 약관마다 마지막 동의 버전이 시행 버전**: 필수 약관이 개정되면 `ACTIVE` 계정도 재동의 전까지 가입 미완료와 같은 범위만 쓰므로 상태값만으로 판정하지 않는다. 선택 약관(마케팅)의 개정은 이용을 막지 않는다. 시행 버전의 원본은 [설정](#설정)이다.
- **다음 단계**(`nextStep`): 아직 마치지 않은 단계다. 프로필이 없으면 `PROFILE`, 프로필은 있으나 이용 불가면 `TERMS`, 이용 가능하면 `COMPLETED`. 변경 API도 저장 뒤의 단계를 같은 응답 모양으로 돌려준다. 다시 시작할 화면은 이 값만으로 고르지 않는다 — 가입 미완료 계정이 다시 로그인하면 입력해 둔 값이 채워진 프로필 입력 화면부터 시작해 "완료" 뒤 약관 동의로 이어진다([조직·계정] 정책 §5).
- **동의 이력은 추가만 한다**: 약관마다 종류·버전·동의 시각을 한 줄씩 남기고 고치거나 지우지 않는다. 시행 버전에 이미 동의한 필수 약관은 다시 기록하지 않는다. 마케팅은 동의 여부와 마지막 변경 시각을 따로 두고, 동의로 바꿀 때만 마케팅 약관 버전을 이력에 더한다. 약관 동의 요청에 마케팅 값을 보내지 않으면 기존 값을 두며 처음 가입이면 미동의로 시작한다.
- **동시 저장**: 같은 계정의 프로필 저장과 약관 동의가 겹치면 버전 열로 늦은 쪽을 `COMMON-409`로 거부해 먼저 저장된 가입 상태를 되돌리지 않는다. 첫 프로필을 동시에 만들 때의 기본 키 충돌도 같은 오류다.
- 상태 코드값은 Notion 제안값을 따른다(ORG-002, organization 쪽 [O-31](bounded-contexts.md#organization-gaps)). 탈퇴하면 `WITHDRAWN`이 되고 다시 바뀌지 않는다([탈퇴](#profile-withdrawal)).

## HTTP

모든 경로는 본인 계정만 다룬다. 경로의 계정이 요청자와 다르면 존재를 드러내지 않도록 `PROFILE-002`(404)로 거부한다. 프로필을 돌려주는 네 API의 응답은 계정 식별자·가입 상태·다음 단계·이름·연락처·가입일·마케팅 동의 여부를 담는다.

| 요청 | 하는 일 | 제품 규칙 |
| --- | --- | --- |
| `GET /api/v1/profiles/{accountId}` | 내 정보와 가입 단계. 프로필이 없으면 `PENDING_SIGNUP`·`PROFILE` | [조직·계정] 정책 §5·§5.1, 기능명세 §7 |
| `PUT /api/v1/profiles/{accountId}` | 프로필 입력(없으면 가입 미완료 프로필 생성)·수정 | [조직·계정] 정책 §5(입력값·이름 공통 입력 규칙)·§5.1·§11, 기능명세 §5·§9 |
| `POST /api/v1/profiles/{accountId}/terms-agreements` | 필수 약관 동의(가입 완료)·재동의, 마케팅 값을 보내면 함께 반영 | [조직·계정] 정책 §5(약관 동의)·§5.1, 기능명세 §5 |
| `POST /api/v1/profiles/{accountId}/marketing-consents` | 마이페이지 마케팅 수신 동의 켜고 끄기. 경로는 허용 목록 안이지만 가입을 마치기 전이면 서비스가 `PROFILE-005`로 거부한다. 필수 약관 재동의 전인 활성 계정은 바꿀 수 있다 | [조직·계정] 정책 §5.1 |
| `GET /api/v1/profiles/{accountId}/withdrawal-eligibility` | 탈퇴 확인 화면 전에 탈퇴를 막는 사유 확인. 탈퇴 가능 여부(`withdrawable`)와 사유·건수 목록(`blocks`) | [조직·계정] 정책 §5.1·§11, 기능명세 §7 |
| `POST /api/v1/profiles/{accountId}/withdrawals` | 즉시 탈퇴, 성공하면 204. 본문 `{"reason"}`은 선택 자유 텍스트이며 본문을 생략해도 된다. 가입 미완료 계정도 쓸 수 있다 | [조직·계정] 정책 §5.1·§11, 기능명세 §4·§5·§6·§7 |

- 오류: `PROFILE-001`(입력 규칙 위반, 400), `PROFILE-002`(다른 계정·탈퇴한 계정, 404), `PROFILE-003`(프로필 입력 전 약관 동의, 409), `PROFILE-004`(필수 약관 미동의, 400), `PROFILE-005`(가입 미완료·재동의 전 이용, 403, [공통 검사](#profile-signup-gate)), `PROFILE-006`(탈퇴 차단, 409, 코드만), `PROFILE-007`(탈퇴 사유 200자 초과·NUL 문자, 400), 동시 저장 충돌 `COMMON-409`.
- 이름·연락처·탈퇴 사유 규칙은 Domain 값객체가 한 번만 검사하고 서비스가 `PROFILE-001`·`PROFILE-007`로 바꾼다. 요청 본문에 같은 제약을 두지 않는다.

<a id="profile-signup-gate"></a>
## 가입 공통 검사

제품 규칙: 가입 미완료·재동의 전 계정이 쓸 수 있는 기능은 [조직·계정] 정책 §5(가입 미완료 계정)·§5.1, 기능명세 §8.

```text
Authorization: Bearer {accessToken} → /api/** (허용 목록 제외)
  → AccessTokenAuthenticationFilter (auth, 무효 토큰은 여기서 404로 끝남)
  → DispatcherServlet → SignupGateInterceptor.preHandle (컨트롤러 메서드로 연결된 요청만)
  → CheckServiceAccessUseCase → 이용 불가면 PROFILE-005(403)
```

- **등록과 허용 목록**: `ProfileWebConfig`(`WebMvcConfigurer`)가 인터셉터를 `/api/**`에 걸고 `/api/v1/profiles/**`, `/api/v1/auth/**`, organization의 회사 확인 `/api/v1/organizations/lookup`을 뺀다. 인증되지 않은 요청은 인가 단계가 이미 걸렀거나 공개 경로이므로 넘긴다. 공개 API도 토큰을 붙여 부르면 인증된 요청이라 검사한다(2026-10-10 결정). 탈퇴 API는 `/api/v1/profiles/**` 안이라 가입 미완료 계정도 쓴다. 다른 모듈에 가입 전에 써야 하는 API가 생기면 공개 여부와 관계없이 허용 목록에 더한다.
- **보안 필터가 아니라 인터셉터인 이유**: 보안 필터로 두면 없는 경로도 403이 되어 auth의 "폐기·만료·위조 토큰의 404는 실제로 없는 자원의 404와 같다" 계약([Auth 인증 흐름](auth.md#인증과-로그아웃-흐름))이 깨진다. 인터셉터는 컨트롤러 메서드로 연결된 요청만 보므로 없는 경로는 그대로 404가 된다.
- **다른 모듈 영향**: 다른 모듈은 검사를 직접 넣지 않으며 허용 목록 밖의 새 API는 자동으로 검사 대상이 된다. 허용 목록은 다른 모듈의 URL 문자열이라 모듈 의존이 아니고 `ModularityTest`가 보지 못하므로, auth API를 `/api/v1/auth/**` 밖에 두거나 가입 전 API를 더할 때 허용 목록을 직접 고친다.
- **테스트 영향**: 다른 모듈의 `@ApplicationModuleTest`에는 profile이 올라오지 않아 이 검사가 빠진다. 전체 컨텍스트 API 통합 테스트에서 실제 Access Token으로 인증 요청을 보내려면 계정이 가입을 마쳐야 하며, `src/test/java/com/orbit/support/SignupTestSupport`가 실제 API로 로그인·프로필 입력·필수 약관 동의를 마친다. 토큰을 직접 서명하는 테스트는 `SignupTestSupport.SignupOnce`가 계정마다 한 번 가입을 마친다.
- **OpenAPI**: 다른 모듈의 ControllerDocs는 profile 오류 타입을 선언할 수 없으므로 `SignupGateOpenApiCustomizer`가 허용 목록 밖의 Bearer 인증 연산마다 403 `PROFILE-005` 응답과 예시를 덧붙인다. 각 ControllerDocs에는 선언하지 않는다.

<a id="profile-withdrawal"></a>
## 탈퇴

제품 규칙: 탈퇴 차단 조건·처리·재가입·데이터 처리는 [조직·계정] 정책 §5.1·§11, 기능명세 §4·§5·§6·§7.

```text
GET  /api/v1/profiles/{accountId}/withdrawal-eligibility
  → CheckWithdrawalEligibilityUseCase → CheckWithdrawalEligibilityService (읽기 전용이 아닌 트랜잭션)
  → 본인·탈퇴 여부 확인(PROFILE-002) → 모든 WithdrawalBlocker 구현 → 사유별 건수 합산 → 200

POST /api/v1/profiles/{accountId}/withdrawals {reason?}
  → WithdrawUseCase → WithdrawService (트랜잭션)
  → 본인 확인(PROFILE-002) → 사유 검사(PROFILE-007)
  → 이미 탈퇴: auth AccountClosure.close만 다시 → 204
  → 모든 WithdrawalBlocker 구현으로 다시 판정 → 막히면 PROFILE-006(409)
  → Profile.withdraw(프로필 없으면 탈퇴 표시 행) → ProfileRepository.save (버전 충돌 COMMON-409)
  → auth AccountClosure.close → AccountWithdrawn 발행 → 커밋 → 204
```

- **탈퇴 상태와 데이터**: `WITHDRAWN`으로 바꾸고 이름·연락처를 지우며 탈퇴 시각과 선택 사유를 남긴다. 약관 동의 이력(동의 증빙)과 가입일은 남긴다. 동의 상태였던 현재 마케팅 수신은 탈퇴 시각으로 해제하고 동의 이력은 그대로 둔다. 프로필을 입력하기 전에 탈퇴하면 이름·연락처·약관 동의 없는 탈퇴 표시 행만 만든다. 탈퇴한 프로필은 다시 바뀌지 않는다.
- **탈퇴한 계정**: 조회·프로필 입력·약관 동의·마케팅 변경·사전 확인은 없는 프로필과 같은 `PROFILE-002`다. 탈퇴하면 토큰이 거부되므로 보통은 닿지 않지만, 거부가 확정되기 전의 요청이 탈퇴한 계정을 되살리지 않게 한다.
- **재요청과 동시 요청**: 이미 탈퇴한 계정의 재요청은 프로필을 그대로 두고 계정 폐쇄만 다시 확정해 204로 끝내며 이벤트를 다시 내지 않는다. 같은 프로필을 다른 요청이 먼저 저장했으면 버전 열로 `COMMON-409`를 주고 계정을 닫지 않는다. 탈퇴를 두 번 보낸 직후의 409는 앞선 요청으로 이미 탈퇴된 상태다.
- **차단 판정**: 사전 확인 뒤 상태가 바뀔 수 있어 탈퇴 직전에 다시 판정한다. 막히면 `PROFILE-006` 코드만 주고 사유·건수는 사전 확인 API가 알린다. 409 본문에 사유를 담으려면 shared 오류 응답 계약을 바꿔야 하기 때문이다. 구현이 같은 사유를 여러 번 돌려주면 profile이 사유마다 건수를 합쳐 사유 enum 순서로 돌려준다.
- **사전 확인의 트랜잭션**: 조회지만 읽기 전용 트랜잭션으로 열지 않는다. `WithdrawalBlocker` 구현에 행 잠금을 허용하는데 PostgreSQL은 읽기 전용 트랜잭션의 `SELECT ... FOR UPDATE`를 거부하기 때문이다. 잠금은 응답과 함께 풀린다.
- **auth 계정 폐쇄와 잠금 순서**: 프로필 저장 뒤 같은 트랜잭션에서 auth `AccountClosure.close`를 불러, 둘 중 하나가 실패하면 함께 롤백된다. 프로필 행을 먼저 저장(flush)하고 auth가 계정 행을 잠그므로 잠금 순서는 profile → auth다. auth 쪽 처리는 [Auth 계정 폐쇄 흐름](auth.md#계정-폐쇄-흐름)이다.
- **사유 길이**: 앞뒤 공백을 지운 뒤 코드포인트로 센다. 여러 코드포인트로 된 이모지는 화면의 글자 수와 어긋날 수 있다. NUL 문자는 PostgreSQL 문자열에 담을 수 없어 `PROFILE-007`로 거부한다.

## 저장 모델

- `profiles`: 기본 키 `account_id`(auth가 발급한 계정 식별자), 이름, 연락처(숫자만), 가입 상태(`PENDING_SIGNUP`·`ACTIVE`·`WITHDRAWN`), 가입일, 탈퇴 시각 `withdrawn_at`, 탈퇴 사유 `withdrawal_reason`(200자), 마케팅 동의 여부·변경 시각, 낙관적 잠금용 `version`. 이름·연락처는 탈퇴하면 비우므로 nullable이다. 연락처는 계정 사이에 중복을 허용하므로 유일 제약이 없다.
- `profile_terms_agreements`: 계정별 약관 종류·버전·동의 시각. 추가만 한다.
- 두 테이블 모두 `BaseTimeEntity`의 `created_at`·`updated_at` 감사 컬럼을 가진다. 가입일·동의 시각 같은 업무 시각은 이 컬럼과 별개다.
- auth의 `accounts`로 가는 외래 키는 두지 않는다(모듈 경계). 두 테이블의 스키마 마이그레이션은 auth와 마찬가지로 아직 없다([실행 프로필 규칙](../../.claude/skills/dino-architecture/references/persistence.md#profiles)).

## 설정

| 속성 | 환경 변수 | 설명 |
| --- | --- | --- |
| `app.profile.terms.versions.service`, `privacy`, `marketing` | — | 시행 중인 약관 종류별 버전. 비면 기동에 실패한다. 필수 약관(`service`·`privacy`) 값을 올리면 이전 버전에만 동의한 계정은 그 뒤 첫 요청부터 재동의 전까지 [공통 검사](#profile-signup-gate)에 막힌다 |

## 연동 관계

- **auth (현재)**: 컨트롤러와 공통 검사가 auth 루트 계약 `AccountPrincipal`에서 계정 식별자만 받아 Command·Query에 넘긴다(Conformist). 이 의존 때문에 auth는 profile을 조회·구독할 수 없다. 탈퇴의 계정 폐쇄(외부 식별 해제·토큰 거부)는 `WithdrawService`가 auth 루트 계약 `AccountClosure`를 탈퇴 트랜잭션 안에서 직접 호출한다(auth가 탈퇴 이벤트를 구독하면 순환). 로그인 응답에는 가입 단계가 없으므로 클라이언트는 로그인 뒤 `GET /api/v1/profiles/{accountId}`로 확인한다.
- **organization·schedule (탈퇴, schedule 연결·organization 연결 전)**: 탈퇴 차단 판정은 두 모듈이 profile 요구 인터페이스 `WithdrawalBlocker`를 구현하고, 탈퇴 뒤처리(소속 비활성화·대기함 반환)는 두 모듈이 `AccountWithdrawn`을 구독해 맡는다(2026-10-14 결정, organization HM-319·schedule HM-320). schedule은 `adapter/in/profile`·`adapter/in/event`로 연결됐다(HM-320). 두 모듈이 profile에 의존하게 되므로 profile은 그들을 조회·구독하지 않는다.
- **계획된 관계** (현재 허용 의존성이 아님): schedule의 기사 이름 표시가 `ProfileLookup`을 쓰도록 연결한다(HM-288). 기사 계약 ID를 계정 ID로 바꾸는 연결은 organization과 합의한다.

## 공개 계약

- `ProfileLookup#findNames(Set<Long>)`: 계정 ID별 표시 이름. 가입을 마친(`ACTIVE`) 계정만 돌려주고, 가입 미완료·프로필 없는·탈퇴한 계정은 결과에 키가 없어 소비자가 셋을 구분하지 못한다. 소비 모듈은 organization(승인 대기 목록의 신청자 이름과 참여 인원 목록의 이름·이름 검색·이름순 정렬, 이름이 없으면 비워 두고 목록 맨 뒤에 두며 검색에서 빠짐)이다. 탈퇴한 계정의 표기는 [남은 일](#profile-todo)이다.
- `WithdrawalBlocker#blocksFor(Long)` (요구 인터페이스): 탈퇴를 막는 상태를 소유한 모듈이 구현한다 — organization은 유일한 총관리자(HM-319), schedule은 기사 소속의 작업중·배정됨 작업(HM-320). profile이 사전 확인과 탈퇴 직전에 등록된 모든 구현을 부른다. 탈퇴 직전 판정은 탈퇴 트랜잭션 안이라 상태를 바꾸지 않되, 판정과 커밋 사이의 경합을 막는 행 잠금은 잡을 수 있다. 같은 사유를 여러 번 돌려줘도 된다.
  - [요구 인터페이스 규칙](../../.claude/skills/dino-architecture/references/communication.md#요구-인터페이스)과 달리 구현을 여러 모듈이 제공하고, 구현이 없으면 임시 구현 없이 아무것도 막지 않는다. 차단 조건이 상태를 소유한 모듈마다 나뉘고, profile은 그 모듈들을 모르기 때문이다. 그래서 운영 출시 전에 두 구현이 들어가야 한다.
- `WithdrawalBlock(reason, count)`·`WithdrawalBlockReason`: 차단 사유와 먼저 정리해야 할 대상의 수(1 이상). 사유 `SOLE_OWNER`·`WORK_IN_PROGRESS`·`WORK_PENDING_ACCEPT`는 [조직·계정] 정책 §5.1의 차단 조건이라 profile이 목록을 소유한다. `WORK_PENDING_ACCEPT`는 [작업] 정책 §9의 배정됨 코드 이름을 따른다. 대상 ID는 담지 않는다(정책 §11은 건수와 처리 경로만 안내).
- `AccountWithdrawn(accountId, occurredAt)` (이벤트): 탈퇴 트랜잭션 안에서 발행하고 구독자는 커밋 뒤 다른 트랜잭션에서 받는다. 차단·롤백·재요청이면 발행하지 않는다. 개인정보 없이 계정 식별자와 발생 시각만 담는다. organization·schedule이 `@ApplicationModuleListener`로 구독해 뒤처리와 실패 재시도를 각자 맡는다. 구독 실패의 재전송은 이벤트 발행 기록(HM-278)이 맡으며, 아직 없어서 지금은 구독자가 실패하면 다시 전달되지 않는다. 아직 구독 모듈은 없다.
- 사용하는 계약: auth `AccountPrincipal`(요청자 계정), `AccountClosure`(탈퇴의 계정 폐쇄).

가입 공통 검사는 공개 계약 없이 웹 계층에서 다른 모듈 API에 적용된다.

<a id="profile-gaps"></a>
## 차이·공백

ID 대역·추적 칸의 뜻과 [대역별 구현 규칙](../planning/README.md#gap-actions)은 색인을 따른다. 해소되면 행을 지운다.

없음.

<a id="profile-todo"></a>
## 남은 일

- **탈퇴 연동** (운영 출시 전 필수): organization의 `WithdrawalBlocker` 구현과 `AccountWithdrawn` 구독(HM-319). schedule 쪽(HM-320)은 연결됐다. 그 전에는 유일한 총관리자도 탈퇴되고 소속이 활성으로 남는다. 구독 실패는 이벤트 발행 기록의 재전송(HM-278)이 다시 실행한다.
- **"탈퇴한 사용자" 표기** ([조직·계정] 정책 §5.1): `ProfileLookup`이 탈퇴한 계정을 어떻게 돌려줄지 정한다. 지금은 가입 미완료·프로필 없는 계정과 구분되지 않는다.
- **탈퇴 뒤 보관 범위**: 약관 동의 이력·가입일·탈퇴 시각·사유를 기한 없이 보관한다. 법적 적정성은 organization 쪽 O-32(ORG-009).
- **프로필 사진** ([조직·계정] 정책 §5·§5.1): 파일 저장 방식이 정해진 뒤 추가한다.
- **운영 전**: `profiles`·`profile_terms_agreements` 스키마 마이그레이션. `profiles`는 `name`·`phone_number` NOT NULL 해제, `withdrawn_at`·`withdrawal_reason`(varchar(200)) 추가, `status`의 enum check 제약(Hibernate가 만든 DB)에 `WITHDRAWN` 허용을 함께 적용한다.
