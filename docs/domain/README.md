# 도메인과 모듈 지도

현재 Application Module로 등록된 예제 모듈과 제품 골격의 책임·공개 계약·허용 의존성을 찾는 출발점입니다. 대상 모듈만 아래 문서·지침으로 이어서 읽습니다. 제품 골격의 설계와 향후 확장은 [바운디드 컨텍스트 지도](bounded-contexts.md#bounded-contexts), 제품 정책은 [기획 원본 색인](../planning/README.md#planning)이 가리키는 Notion 정책서, 기능·화면 흐름은 [노션 기능명세](#노션-기능명세)를 따릅니다. 계획된 관계는 현재 허용 의존성과 구분합니다.

## 모듈별 책임과 공개 계약

| 모듈 | 소유 책임 | 공개 계약 | 허용 의존성 |
| --- | --- | --- | --- |
| `shared` | 오류 기반, OpenAPI 오류 문서화, 공통 응답 구현, 설정과 보안 | `shared::error` (`BaseCode`, `BusinessException`, `CommonErrorCode`), `shared::openapi` (`ApiErrorCodes`, `ApiErrorCodesGroup`) | 없음 |
| `user` | 사용자 등록과 사용자 요약 조회 | [User 공개 계약](user.md#공개-계약) | `shared::error`, `shared::openapi` |
| `auth` | subject 조회 예제와 등록 이벤트 후속 처리 | [Auth 공개 계약](auth.md#패키지와-공개-계약) | `shared::error`, `shared::openapi`, `user` |
| `organization` | 골격 — 조직·소속·참여 요청 관리 예정 | 없음 | 없음 |
| `schedule` | 작업 생애주기 — `domain`(Work 애그리게잇·값객체·배정 이력·완료보고·일정 겹침 정책)과 Application(작업 생애주기 유즈케이스 — 목록은 `application/port/in` 패키지, 출력 포트·오류 코드), 기사 일정 잠금 어댑터(PostgreSQL advisory lock), 임시 출력 어댑터(메모리 저장소·모두 거부 행위자, `local`·`test`에서만 등록 — 그 밖의 프로필은 현재 기동하지 않음) | 없음 | `shared::error` |
| `notification` | 골격 — 이벤트 기반 알림 관리 예정 | 없음 | 없음 |

골격 모듈(`organization`, `notification`)은 `package-info.java`만 존재합니다. 골격 두 모듈은 `allowedDependencies = {}`로 모듈 의존성을 허용하지 않습니다. `schedule`은 공개 계약이 없고 오류 코드를 위해 `shared::error`만 허용합니다. 실제 공개 계약을 사용하는 구현을 추가할 때 필요한 의존성과 이 지도를 함께 갱신합니다.

<a id="노션-기능명세"></a>
## 노션 기능명세

기능 설명·정책·화면 흐름·수용 기준의 원본(SSOT)은 노션 [Dino 기획 문서 DB](https://www.notion.so/3cdf190e116680a7af2deda1422b4c95)입니다. 레포 문서는 코드 기준의 모듈 책임·공개 계약·모듈 관계만 다루며 기능 설명을 복사하지 않습니다. 작업 계획과 PR에는 해당 기능명세 페이지 링크를 첨부합니다.

DB에서 `문서유형 = 기능명세`, `유효여부 = O`, 아래 `구분(도메인)` 값으로 현재 페이지를 찾습니다. 버전이 오르면 새 페이지가 생기므로 이 표에 버전별 링크를 두지 않습니다. 같은 DB에 정책·화면설계·QA·시나리오 문서도 있습니다.

| 코드 모듈 | 노션 `구분(도메인)` |
| --- | --- |
| `organization` | 조직·계정 |
| `user`, `auth` | 조직·계정(계정·인증), 공통 |
| `schedule` | 작업, 스케줄·배정, 현장 수행, 현황 파악 |
| `notification` | 알림 |

새 모듈을 추가하거나 노션 도메인 구분이 바뀔 때만 이 표를 갱신합니다.

## 작업 경로와 추가 지침

대상 경로의 지침을 먼저 확인한 뒤 관련 계약 절을 읽습니다. 루트 세션에서 파일 목록에 지침이 보였다는 사실만으로 본문을 읽었다고 간주하지 않습니다. `AGENTS.override.md`가 있으면 같은 디렉터리의 기본 지침보다 우선합니다.

| 대상 경로 (저장소 루트 기준) | 추가 지침 → 계약 원본 | 확인할 내용 |
| --- | --- | --- |
| `src/main/java/com/orbit/user/**` | [user 지침](../../src/main/java/com/orbit/user/AGENTS.md) → [User](user.md) | 불변식·공개 정보·발행 시점과 auth 영향 |
| `src/main/java/com/orbit/auth/**` | [auth 지침](../../src/main/java/com/orbit/auth/AGENTS.md) → [Auth](auth.md) | 예제 한계·소유 모델 변환·커밋 후 처리 |
| `src/main/java/com/orbit/shared/**` | [shared 지침](../../src/main/java/com/orbit/shared/AGENTS.md) → [공개 타입·소비자](#모듈별-책임과-공개-계약)·[공개 경계 규칙](../../.claude/skills/dino-architecture/references/architecture.md#shared) | named interface와 내부 구현, 소비 모듈 영향 |
| `src/main/java/com/orbit/organization/**` | 하위 지침 없음 → [조직 설계](bounded-contexts.md#organization)·[설계 결정](bounded-contexts.md#organization-design)·[차이·공백](bounded-contexts.md#organization-gaps) | 골격만 존재; 조직·소속·참여 요청 소유권, 총관리자 복수 지정(O-01) |
| `src/main/java/com/orbit/schedule/**` | [schedule 지침](../../src/main/java/com/orbit/schedule/AGENTS.md) → [schedule 문서](schedule.md#schedule)의 [구현 결정](schedule.md#schedule-implementation)·[차이·공백](schedule.md#schedule-gaps) | `domain`, `application` 일부, 기사 일정 잠금 어댑터, 임시 출력 어댑터 구현; 공통 오류 순서, 상태 전이·배정 이력 불변식, 도메인 예외 변환, organization ID 참조 경계, 임시 어댑터 교체 조건 |
| `src/main/java/com/orbit/notification/**` | 하위 지침 없음 → [알림 설계](bounded-contexts.md#notification) | 골격만 존재; 이벤트 소비 경계 |
| `src/test/java/com/orbit/**` | [테스트 지침](../../src/test/java/com/orbit/AGENTS.md) → 위 대상 소스 모듈 지침·계약 | 테스트가 다루는 소유 모듈·소비 경계 |

모듈 목록·책임 요약·허용 의존성은 이 지도, 공개 타입·필드·동작은 개별 모듈 문서가 소유합니다. 새 모듈은 지도에 진입점을 추가하고 상세 계약은 해당 문서에 기록합니다. 같은 타입 목록을 지도·하위 지침에 복제하지 않습니다. 별도 문서가 없는 shared의 공개 타입 목록은 [모듈별 책임과 공개 계약](#모듈별-책임과-공개-계약) 표가 소유합니다.

검증할 때는 [집중 검사 표](../../.claude/skills/dino-testing/references/verification.md#selection)를 적용합니다. `user`·`auth`는 구조를 보여주는 예제이며, 새 프로젝트에서는 실제 유스케이스와 데이터 소유권에 맞춰 교체합니다.
