# Schedule 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 책임·애그리게잇은 [작업 설계](../../../../../../docs/domain/bounded-contexts.md#schedule), 상태 전이·배정·취소·권한 규칙은 [작업 상태·배정 정책](../../../../../../docs/domain/bounded-contexts.md#schedule-policies)이 원본입니다. 여기에 타입 목록·전이표를 복제하지 않습니다.

- `domain`, `application`(작업 등록·기본정보 수정·배정·재배정·일정 변경·배정 해제 유즈케이스·출력 포트·오류 코드), `adapter/out`(기사 일정 잠금 어댑터·임시 출력 어댑터)이 있고 모듈 루트 공개 계약은 없습니다. `allowedDependencies`는 `shared::error`입니다. 추가할 때 도메인 지도와 허용 의존성을 함께 갱신합니다.
- 출력 포트 구현은 임시입니다: `adapter/out/memory/InMemoryWorkRepository`는 JPA 어댑터(HM-234), `adapter/out/organization/DenyingActorAdapter`(모두 거부)는 organization 소속 조회 계약으로 교체한 뒤 삭제합니다. 둘 다 `local`·`test` 프로필에서만 등록합니다. 이 포트를 쓰는 서비스(`CreateWorkService` 등)가 있으므로 현재 그 밖의 프로필(`prod`, 프로필 없음)은 Bean 부재로 기동하지 않으며, 이것이 의도입니다. 실제 어댑터를 추가하고 임시 구현을 지우지 않으면 Bean 중복으로 실패하니 `@Primary`로 덮지 않습니다.
- JPA 어댑터는 `WorkRepository` 계약을 지킵니다: 조회 결과는 영속 상태와 분리된 사본이라 `save`하지 않은 변경은 커밋돼도 저장되지 않아야 하고(배정·재배정·일정 변경의 미확인 겹침이 여기에 기댑니다), 이를 실제 트랜잭션 커밋으로 검증하는 테스트를 둡니다.
- 기사 일정을 차지하거나 옮기는 유즈케이스는 반드시 `ScheduleChanges`를 거칩니다. 여기서 `LockTechnicianSchedulePort`로 기사를 잠근 뒤 활성 작업을 읽어, 같은 기사를 동시에 바꾸는 요청을 한 줄로 세웁니다. 구현(`PostgresTechnicianScheduleLockAdapter`)은 PostgreSQL 트랜잭션 advisory lock이라 임시 어댑터와 달리 모든 프로필에서 등록되고, 저장소와 같은 트랜잭션 연결에서만 동작합니다. 대기 한도는 `app.schedule.technician-lock.wait-limit`(기본 2초)입니다.
- 잠금은 잠근 뒤의 조회가 앞선 커밋을 볼 때만 유효합니다. JPA 어댑터는 READ COMMITTED를 유지하고, 기사 활성 작업 조회에 쿼리 캐시·2차 캐시를 쓰지 않습니다. 잠그기 전에 같은 기사의 다른 작업을 영속성 컨텍스트에 올려 두면 잠근 뒤 조회가 그 오래된 인스턴스를 돌려주므로, 잠금 전에는 대상 작업만 읽습니다.
- 서비스 단위 테스트용 fake는 `src/test`에 두고 `@Component` 등 스캔 대상 애너테이션을 붙이지 않습니다.
- 도메인 불변식 예외는 서비스가 `DomainRuleViolations`로 감싸 오류 코드로 바꿉니다. 람다에는 도메인 호출만 넣습니다.
- Domain은 Spring·JPA·Web 타입과 `Clock`에 의존하지 않습니다. 시각은 호출자가 UTC `Instant`로 넘깁니다.
- 상태는 업무별 메서드로만 바꾸고 상태만 주입하는 경로를 두지 않습니다. 새 전이는 정책 절과 전이 규칙·테스트를 함께 바꿉니다.
- 배정 이력은 추가만 합니다. 수락·거절로 확정된 결과는 바꾸지 않고 현재 배정은 최신 이력입니다. 저장값 복원도 결과·시각·사유의 정합성을 검증합니다.
- 조직과 조직이 소유한 소속·작업 유형은 ID 값객체로만 참조하고 organization 내부 타입에 의존하지 않습니다.

## 집중 검증

- Domain: `com.orbit.schedule.domain` 패키지의 관련 테스트.
- Application: `com.orbit.schedule.application` 패키지의 관련 테스트.
- Adapter: `com.orbit.schedule.adapter` 패키지의 관련 테스트(잠금 어댑터는 Docker의 PostgreSQL 필요).
- 모듈 조립: `com.orbit.schedule.ScheduleModuleTest`.
- 모듈 경계: `com.orbit.ModularityTest`, `com.orbit.ArchitectureTest`.
- 그 밖의 선택은 [공통 검사 표](../../../../../../docs/conventions/testing/selection.md#selection)를 따릅니다.
