# 도메인과 모듈 지도

현재 Application Module로 등록된 예제 모듈과 제품 골격의 책임·공개 계약·허용 의존성을 찾는 출발점입니다. 대상 모듈만 아래 문서·지침으로 이어서 읽습니다. 제품 골격의 설계와 향후 확장은 [바운디드 컨텍스트 지도](bounded-contexts.md#bounded-contexts), 제품 결정과 기능 흐름은 [기획 원본 색인](../planning/README.md#planning)이 가리키는 노션 정책서·기능명세가 원본입니다. 계획된 관계는 현재 허용 의존성과 구분합니다.

## 모듈별 책임과 공개 계약

| 모듈 | 소유 책임 | 공개 계약 | 허용 의존성 |
| --- | --- | --- | --- |
| `shared` | 오류 기반, OpenAPI 오류 문서화, 공통 응답 구현, 설정과 보안, JPA 감사 컬럼 | `shared::error` (`BaseCode`, `BusinessException`, `CommonErrorCode`), `shared::openapi` (`ApiErrorCodes`, `ApiErrorCodesGroup`), `shared::security` (`SecurityFilterChainCustomizer`, `ApiErrorResponseWriter`), `shared::persistence` (`BaseTimeEntity`) | 없음 |
| `user` | 사용자 등록과 사용자 요약 조회 | [User 공개 계약](user.md#공개-계약) | `shared::error`, `shared::openapi` |
| `auth` | 카카오·Apple OIDC 로그인, 계정(`Account`) 발급·조회, Apple refresh token 암호화 보관·철회 포트, Access Token 발급·검증·로그아웃과 예제 subject 조회·등록 이벤트 후속 처리 | [Auth 공개 계약](auth.md#패키지와-공개-계약) | `shared::error`, `shared::openapi`, `shared::security`, `user` |
| `profile` | 계정의 가입 프로필(이름·연락처)·가입 상태·약관 동의 이력, 가입 미완료·필수 약관 재동의 전 계정의 API 이용 제한([가입 공통 검사](profile.md#profile-signup-gate)) | [Profile 공개 계약](profile.md#공개-계약) — 계정 표시 이름 조회 `ProfileLookup` | `shared::error`, `shared::openapi`, `shared::persistence`, `auth` |
| `organization` | 발주사(회사 코드 포함)·직원 소속·기사 계약 — 현재 발주사 생성(최초 총관리자 소속·회사 코드 발급), 발주사 정보 조회·수정, 회사 코드 조회·변경, 총관리자 지정·해제, 회사 확인·참여 요청 생성·취소·내 신청 현황, 참여 요청 승인 대기 목록(신청자 이름은 profile `ProfileLookup`)·승인·거절(직원 소속·기사 계약 생성, 비활성 소속 재활성화), 계정·발주사의 활성 구성원 조회, 기사 계약 조회(계정의 계약 목록·발주사의 활성 계약 확인), 요청자의 내 소속 목록 조회, 직원·기사 유형 관리. 요청자 계정은 웹 어댑터가 auth의 `AccountPrincipal`로 받음. 작업 유형 관리 예정 | `OrganizationMemberLookup` — 활성 직원 소속(`StaffMember`, 총관리자 표시 포함) 또는 활성 기사 계약(`TechnicianMember`) 조회, 소비자 `schedule`. `TechnicianContractLookup` — 계정이 모든 발주사와 맺은 기사 계약(`TechnicianContract`, 비활성 포함·활성 여부 표시)을 조회하고, 기사 계약이 그 발주사의 활성 계약인지 확인(`existsActive`). 둘 다 캐시 없이 DB 상태를 읽음. 소비자 `schedule`(배정할 기사 확인, 탈퇴 차단 판정·뒤처리 유즈케이스 — 탈퇴 유즈케이스는 profile 연결(HM-320) 전이라 아직 호출 경로가 없음). 요구 인터페이스 `TechnicianWorkRelease` — 역할 변경·비활성화의 대기함 반환(역할 변경만 결과 `WorkReleased`·`WorkReleaseBlocked`), 구현 `schedule`. 이벤트 `ParticipationRequestApproved`(희망·확정 유형)·`ParticipationRequestRejected`(거절 사유) — 승인·거절 커밋 후 소비, 소비자 `notification`(HM-277 예정) | `shared::error`, `shared::openapi`, `shared::persistence`, `auth`, `profile` |
| `schedule` | 작업 생애주기 — `domain`(Work 애그리게잇·값객체·배정 이력·완료보고·일정 겹침 정책)과 Application(작업 생애주기 유즈케이스 — 목록은 `application/port/in` 패키지, 출력 포트·오류 코드), Work 저장소·목록 조회 어댑터(JPA, PostgreSQL)와 기사 일정 잠금 어댑터(PostgreSQL advisory lock), 작업 변경·조회 HTTP API(`adapter/in/web`, 조회는 타임테이블·작업 목록·대기함 요약·작업내역 출력·작업 상세·진행 보드, 요청자는 auth의 `AccountPrincipal`), organization 요청자 조회·기사 계약 확인·계정의 기사 계약 조회 어댑터(ACL), organization 요구 인터페이스 `TechnicianWorkRelease` 구현(`adapter/in/organization`, 역할 변경·비활성화의 대기함 반환), 임시 출력 어댑터(빈 표시 정보(기사·등록자·작업유형)·가짜 사진 주소·모두 거부하는 작업 유형 확인, `local`·`test`에서만 등록 — 그 밖의 프로필은 현재 기동하지 않음) | 없음 | `shared::error`, `shared::openapi`, `shared::persistence`, `organization`, `auth` |
| `notification` | 골격 — 이벤트 기반 알림 관리 예정 | 없음 | 없음 |

골격 모듈 `notification`은 `package-info.java`만 존재하며 `allowedDependencies = {}`로 모듈 의존성을 허용하지 않습니다. `organization`은 schedule에 활성 구성원 조회·기사 계약 조회 계약을 공개하고, 오류 코드·OpenAPI 오류 문서화를 위해 `shared::error`, `shared::openapi`를, Entity 공통 부모를 위해 `shared::persistence`를, 요청자 계정을 받기 위해 `auth`를, 승인 대기 목록의 신청자 이름을 찾기 위해 `profile`을 허용합니다. `schedule`은 공개 계약이 없고 오류 코드·OpenAPI 오류 문서화를 위해 `shared::error`, `shared::openapi`를, Entity 공통 부모를 위해 `shared::persistence`를, 요청자와 기사 계약을 판정하기 위해 `organization`을, 요청자 계정을 받기 위해 `auth`를 허용합니다. 이 의존 때문에 organization은 schedule 타입에 의존할 수 없습니다. 역할 변경의 작업중 확인과 역할 변경·비활성화의 대기함 반환, 작업 유형 삭제의 영향 건수 확인은 organization이 선언하고 schedule이 구현하는 [요구 인터페이스](../../.claude/skills/dino-architecture/references/communication.md#요구-인터페이스)로 처리합니다. 대기함 반환은 `TechnicianWorkRelease` 한 호출로 organization 트랜잭션에서 함께 커밋합니다(호출 HM-292·HM-293, 작업 유형은 HM-295에서 추가). `profile`은 Entity 공통 부모를 위해 `shared::persistence`를 허용하고, 웹 어댑터에서만 auth 루트 계약 `AccountPrincipal`을 쓰므로 auth는 profile을 조회·구독할 수 없습니다. profile의 가입 공통 검사는 모듈 의존 없이 허용 목록 밖의 모든 `/api/**`에 적용됩니다.

## 작업 경로와 추가 지침

대상 경로의 지침을 먼저 확인한 뒤 관련 계약 절을 읽습니다. 루트 세션에서 파일 목록에 지침이 보였다는 사실만으로 본문을 읽었다고 간주하지 않습니다. `AGENTS.override.md`가 있으면 같은 디렉터리의 기본 지침보다 우선합니다.

| 대상 경로 (저장소 루트 기준) | 추가 지침 → 계약 원본 | 확인할 내용 |
| --- | --- | --- |
| `src/main/java/com/orbit/user/**` | [user 지침](../../src/main/java/com/orbit/user/AGENTS.md) → [User](user.md) | 불변식·공개 정보·발행 조건과 auth 영향 |
| `src/main/java/com/orbit/auth/**` | [auth 지침](../../src/main/java/com/orbit/auth/AGENTS.md) → [Auth](auth.md) | id_token·nonce·state·Access Token 검증 조건, Apple 토큰 API 실패 구분, 404 정책, 계정 소유 범위, 예제 ACL 변환 |
| `src/main/java/com/orbit/profile/**` | [profile 지침](../../src/main/java/com/orbit/profile/AGENTS.md) → [Profile](profile.md#profile) | 가입 상태·이용 가능 판정, 본인 계정 제한, 약관 버전 설정, 공통 검사 허용 목록과 auth의 무효 토큰 404 계약, auth 의존 방향 |
| `src/main/java/com/orbit/shared/**` | [shared 지침](../../src/main/java/com/orbit/shared/AGENTS.md) → [공개 타입·소비자](#모듈별-책임과-공개-계약)·[공개 경계 규칙](../../.claude/skills/dino-architecture/references/architecture.md#shared) | named interface와 내부 구현, 소비 모듈 영향 |
| `src/main/java/com/orbit/organization/**` | [organization 지침](../../src/main/java/com/orbit/organization/AGENTS.md) → [조직 설계](bounded-contexts.md#organization)·[설계 결정](bounded-contexts.md#organization-design)·[차이·공백](bounded-contexts.md#organization-gaps) | 발주사·직원 소속·기사 계약 조회 구현; 총관리자 표시, schedule 공개 조회 계약(`OrganizationMemberLookup`)·대기함 반환 요구 인터페이스(`TechnicianWorkRelease`), 회사 코드 형식·발주사명 입력 규칙([조직·계정] §5·§6), 요청자 계정은 웹 어댑터에서만 `AccountPrincipal`로 받음, 발주사 유즈케이스 오류 순서, 직원·기사 유형의 종류별 저장소·발주사 잠금(대기 한도) |
| `src/main/java/com/orbit/schedule/**` | [schedule 지침](../../src/main/java/com/orbit/schedule/AGENTS.md) → [schedule 문서](schedule.md#schedule)의 [구현 결정](schedule.md#schedule-implementation)·[차이·공백](schedule.md#schedule-gaps) | `domain`, `application` 일부, 작업 변경·조회 HTTP API, organization 대기함 반환 요구 인터페이스 구현, Work JPA 저장소·조회 어댑터·기사 일정 잠금 어댑터, 임시 출력 어댑터 구현; 공통 오류 순서, 상태 전이·배정 이력 불변식, 도메인 예외 변환, organization ID 참조 경계, 임시 어댑터 교체 조건 |
| `src/main/java/com/orbit/notification/**` | 하위 지침 없음 → [알림 설계](bounded-contexts.md#notification) | 골격만 존재; 이벤트 소비 경계 |
| `src/test/java/com/orbit/**` | [테스트 지침](../../src/test/java/com/orbit/AGENTS.md) → 위 대상 소스 모듈 지침·계약 | 테스트가 다루는 소유 모듈·소비 경계 |

모듈 목록·책임 요약·허용 의존성은 이 지도, 공개 타입·필드·동작은 개별 모듈 문서가 소유합니다. 새 모듈은 지도에 진입점을 추가하고 상세 계약은 해당 문서에 기록합니다. 같은 타입 목록을 지도·하위 지침에 복제하지 않습니다. 별도 문서가 없는 shared의 공개 타입 목록은 [모듈별 책임과 공개 계약](#모듈별-책임과-공개-계약) 표가 소유합니다.

검증할 때는 [집중 검사 표](../../.claude/skills/dino-testing/references/verification.md#selection)를 적용합니다. `user`와 `auth`의 subject 조회·이벤트 후속 처리는 구조를 보여주는 예제이며, 새 프로젝트에서는 실제 유스케이스와 데이터 소유권에 맞춰 교체합니다.
