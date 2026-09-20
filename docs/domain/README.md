# 도메인과 모듈 지도

현재 Application Module로 등록된 예제 모듈과 제품 골격의 책임·공개 계약·허용 의존성을 찾는 출발점입니다. 대상 모듈만 아래 문서·지침으로 이어서 읽습니다. 제품 골격의 설계와 향후 확장은 [바운디드 컨텍스트 지도](bounded-contexts.md#bounded-contexts), 유즈케이스·미결정 정책은 [기획 초안](../planning/use-cases.md)을 참고합니다. 계획된 관계는 현재 허용 의존성과 구분합니다.

## 모듈별 책임과 공개 계약

| 모듈 | 소유 책임 | 공개 계약 | 허용 의존성 |
| --- | --- | --- | --- |
| `shared` | 오류 기반, OpenAPI 오류 문서화, 공통 응답 구현, 설정과 보안 | `shared::error` (`BaseCode`, `BusinessException`, `CommonErrorCode`), `shared::openapi` (`ApiErrorCodes`, `ApiErrorCodesGroup`) | 없음 |
| `user` | 사용자 등록과 사용자 요약 조회 | [User 공개 계약](user.md#공개-계약) | `shared::error`, `shared::openapi` |
| `auth` | subject 조회 예제와 등록 이벤트 후속 처리 | [Auth 공개 계약](auth.md#패키지와-공개-계약) | `shared::error`, `shared::openapi`, `user` |
| `organization` | 골격 — 조직·소속·초대 관리 예정 | 없음 | 없음 |
| `schedule` | 골격 — 작업 생애주기 관리 예정 | 없음 | 없음 |
| `notification` | 골격 — 이벤트 기반 알림 관리 예정 | 없음 | 없음 |

골격 모듈은 `package-info.java`만 존재하며 `allowedDependencies = {}`로 모듈 의존성을 허용하지 않습니다. 실제 공개 계약을 사용하는 구현을 추가할 때 필요한 의존성과 이 지도를 함께 갱신합니다.

## 작업 경로와 추가 지침

대상 경로의 지침을 먼저 확인한 뒤 관련 계약 절을 읽습니다. 루트 세션에서 파일 목록에 지침이 보였다는 사실만으로 본문을 읽었다고 간주하지 않습니다. `AGENTS.override.md`가 있으면 같은 디렉터리의 기본 지침보다 우선합니다.

| 대상 경로 (저장소 루트 기준) | 추가 지침 → 계약 원본 | 확인할 내용 |
| --- | --- | --- |
| `src/main/java/com/orbit/user/**` | [user 지침](../../src/main/java/com/orbit/user/AGENTS.md) → [User](user.md) | 불변식·공개 정보·발행 시점과 auth 영향 |
| `src/main/java/com/orbit/auth/**` | [auth 지침](../../src/main/java/com/orbit/auth/AGENTS.md) → [Auth](auth.md) | 예제 한계·소유 모델 변환·커밋 후 처리 |
| `src/main/java/com/orbit/shared/**` | [shared 지침](../../src/main/java/com/orbit/shared/AGENTS.md) → [공개 타입·소비자](#모듈별-책임과-공개-계약)·[공개 경계 규칙](../conventions/architecture/shared.md#shared) | named interface와 내부 구현, 소비 모듈 영향 |
| `src/main/java/com/orbit/organization/**` | 하위 지침 없음 → [조직 설계](bounded-contexts.md#organization) | 골격만 존재; 조직·소속·초대 소유권과 미결정 정책 |
| `src/main/java/com/orbit/schedule/**` | 하위 지침 없음 → [작업 설계](bounded-contexts.md#schedule) | 골격만 존재; 작업 생애주기·배정 경계 |
| `src/main/java/com/orbit/notification/**` | 하위 지침 없음 → [알림 설계](bounded-contexts.md#notification) | 골격만 존재; 이벤트 소비 경계 |
| `src/test/java/com/orbit/**` | [테스트 지침](../../src/test/java/com/orbit/AGENTS.md) → 위 대상 소스 모듈 지침·계약 | 테스트가 다루는 소유 모듈·소비 경계 |

모듈 목록·책임 요약·허용 의존성은 이 지도, 공개 타입·필드·동작은 개별 모듈 문서가 소유합니다. 새 모듈은 지도에 진입점을 추가하고 상세 계약은 해당 문서에 기록합니다. 같은 타입 목록을 지도·하위 지침에 복제하지 않습니다. 별도 문서가 없는 shared의 공개 타입 목록은 [모듈별 책임과 공개 계약](#모듈별-책임과-공개-계약) 표가 소유합니다.

검증할 때는 [집중 검사 표](../conventions/testing/selection.md#selection)를 적용합니다. `user`·`auth`는 구조를 보여주는 예제이며, 새 프로젝트에서는 실제 유스케이스와 데이터 소유권에 맞춰 교체합니다.
