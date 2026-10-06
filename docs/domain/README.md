# 도메인과 모듈 지도

현재 Application Module로 등록된 예제 모듈과 제품 골격의 책임·공개 계약·허용 의존성을 찾는 출발점입니다. 대상 모듈만 아래 문서·지침으로 이어서 읽습니다. 제품 골격의 설계와 향후 확장은 [바운디드 컨텍스트 지도](bounded-contexts.md#bounded-contexts), 제품 결정과 기능 흐름은 [기획 원본 색인](../planning/README.md#planning)이 가리키는 노션 정책서·기능명세가 원본입니다. 계획된 관계는 현재 허용 의존성과 구분합니다.

## 모듈별 책임과 공개 계약

| 모듈 | 소유 책임 | 공개 계약 | 허용 의존성 |
| --- | --- | --- | --- |
| `shared` | 오류 기반, OpenAPI 오류 문서화, 공통 응답 구현, 설정과 보안 | `shared::error` (`BaseCode`, `BusinessException`, `CommonErrorCode`), `shared::openapi` (`ApiErrorCodes`, `ApiErrorCodesGroup`), `shared::security` (`SecurityFilterChainCustomizer`, `ApiErrorResponseWriter`) | 없음 |
| `user` | 사용자 등록과 사용자 요약 조회 | [User 공개 계약](user.md#공개-계약) | `shared::error`, `shared::openapi` |
| `auth` | 카카오 OIDC 로그인, 계정(`Account`) 발급·조회, Access Token 발급·검증·로그아웃과 예제 subject 조회·등록 이벤트 후속 처리 | [Auth 공개 계약](auth.md#패키지와-공개-계약) | `shared::error`, `shared::openapi`, `shared::security`, `user` |
| `organization` | 발주사(회사 코드 포함)와 직원 소속 — 현재 발주사 생성(최초 총관리자 소속·회사 코드 발급). 기사 계약·참여 요청·유형 관리 예정 | 없음 | `shared::error`, `shared::openapi` |
| `schedule` | 작업 생애주기 — `domain`(Work 애그리게잇·값객체·배정 이력·완료보고·일정 겹침 정책)과 Application(작업 생애주기 유즈케이스 — 목록은 `application/port/in` 패키지, 출력 포트·오류 코드), 기사 일정 잠금 어댑터(PostgreSQL advisory lock), 임시 출력 어댑터(메모리 저장소·빈 조회 목록·모두 거부 행위자·가짜 기사 이름·가짜 사진 주소, `local`·`test`에서만 등록 — 그 밖의 프로필은 현재 기동하지 않음) | 없음 | `shared::error` |
| `notification` | 골격 — 이벤트 기반 알림 관리 예정 | 없음 | 없음 |

골격 모듈 `notification`은 `package-info.java`만 존재하며 `allowedDependencies = {}`로 모듈 의존성을 허용하지 않습니다. `organization`은 공개 계약이 없고 오류 코드·OpenAPI 오류 문서화를 위해 `shared::error`, `shared::openapi`만 허용합니다. `schedule`은 공개 계약이 없고 오류 코드를 위해 `shared::error`만 허용합니다.

## 작업 경로와 추가 지침

대상 경로의 지침을 먼저 확인한 뒤 관련 계약 절을 읽습니다. 루트 세션에서 파일 목록에 지침이 보였다는 사실만으로 본문을 읽었다고 간주하지 않습니다. `AGENTS.override.md`가 있으면 같은 디렉터리의 기본 지침보다 우선합니다.

| 대상 경로 (저장소 루트 기준) | 추가 지침 → 계약 원본 | 확인할 내용 |
| --- | --- | --- |
| `src/main/java/com/orbit/user/**` | [user 지침](../../src/main/java/com/orbit/user/AGENTS.md) → [User](user.md) | 불변식·공개 정보·발행 조건과 auth 영향 |
| `src/main/java/com/orbit/auth/**` | [auth 지침](../../src/main/java/com/orbit/auth/AGENTS.md) → [Auth](auth.md) | id_token·nonce·Access Token 검증 조건, 404 정책, 계정 소유 범위, 예제 ACL 변환 |
| `src/main/java/com/orbit/shared/**` | [shared 지침](../../src/main/java/com/orbit/shared/AGENTS.md) → [공개 타입·소비자](#모듈별-책임과-공개-계약)·[공개 경계 규칙](../../.claude/skills/dino-architecture/references/architecture.md#shared) | named interface와 내부 구현, 소비 모듈 영향 |
| `src/main/java/com/orbit/organization/**` | [organization 지침](../../src/main/java/com/orbit/organization/AGENTS.md) → [조직 설계](bounded-contexts.md#organization)·[설계 결정](bounded-contexts.md#organization-design)·[차이·공백](bounded-contexts.md#organization-gaps) | 발주사·직원 소속 구현; 총관리자 표시, 회사 코드 형식(O-11), 요청자 계정 임시 resolver 교체 조건 |
| `src/main/java/com/orbit/schedule/**` | [schedule 지침](../../src/main/java/com/orbit/schedule/AGENTS.md) → [schedule 문서](schedule.md#schedule)의 [구현 결정](schedule.md#schedule-implementation)·[차이·공백](schedule.md#schedule-gaps) | `domain`, `application` 일부, 기사 일정 잠금 어댑터, 임시 출력 어댑터 구현; 공통 오류 순서, 상태 전이·배정 이력 불변식, 도메인 예외 변환, organization ID 참조 경계, 임시 어댑터 교체 조건 |
| `src/main/java/com/orbit/notification/**` | 하위 지침 없음 → [알림 설계](bounded-contexts.md#notification) | 골격만 존재; 이벤트 소비 경계 |
| `src/test/java/com/orbit/**` | [테스트 지침](../../src/test/java/com/orbit/AGENTS.md) → 위 대상 소스 모듈 지침·계약 | 테스트가 다루는 소유 모듈·소비 경계 |

모듈 목록·책임 요약·허용 의존성은 이 지도, 공개 타입·필드·동작은 개별 모듈 문서가 소유합니다. 새 모듈은 지도에 진입점을 추가하고 상세 계약은 해당 문서에 기록합니다. 같은 타입 목록을 지도·하위 지침에 복제하지 않습니다. 별도 문서가 없는 shared의 공개 타입 목록은 [모듈별 책임과 공개 계약](#모듈별-책임과-공개-계약) 표가 소유합니다.

검증할 때는 [집중 검사 표](../../.claude/skills/dino-testing/references/verification.md#selection)를 적용합니다. `user`와 `auth`의 subject 조회·이벤트 후속 처리는 구조를 보여주는 예제이며, 새 프로젝트에서는 실제 유스케이스와 데이터 소유권에 맞춰 교체합니다.
