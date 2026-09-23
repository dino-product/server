# Schedule 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 책임·애그리게잇은 [작업 설계](../../../../../../docs/domain/bounded-contexts.md#schedule), 상태 전이·배정·취소·권한 규칙은 [작업 상태·배정 정책](../../../../../../docs/domain/bounded-contexts.md#schedule-policies)이 원본입니다. 여기에 타입 목록·전이표를 복제하지 않습니다.

- `domain`과 `application`(출력 포트·오류 코드)이 있고 Adapter·모듈 루트 공개 계약은 없습니다. `allowedDependencies`는 `shared::error`입니다. 추가할 때 도메인 지도와 허용 의존성을 함께 갱신합니다.
- 도메인 불변식 예외는 서비스가 `DomainRuleViolations`로 감싸 오류 코드로 바꿉니다. 람다에는 도메인 호출만 넣습니다.
- Domain은 Spring·JPA·Web 타입과 `Clock`에 의존하지 않습니다. 시각은 호출자가 UTC `Instant`로 넘깁니다.
- 상태는 업무별 메서드로만 바꾸고 상태만 주입하는 경로를 두지 않습니다. 새 전이는 정책 절과 전이 규칙·테스트를 함께 바꿉니다.
- 배정 이력은 추가만 합니다. 수락·거절로 확정된 결과는 바꾸지 않고 현재 배정은 최신 이력입니다. 저장값 복원도 결과·시각·사유의 정합성을 검증합니다.
- 조직과 조직이 소유한 소속·작업 유형은 ID 값객체로만 참조하고 organization 내부 타입에 의존하지 않습니다.

## 집중 검증

- Domain: `com.orbit.schedule.domain` 패키지의 관련 테스트.
- Application: `com.orbit.schedule.application` 패키지의 관련 테스트.
- 모듈 경계: `com.orbit.ModularityTest`, `com.orbit.ArchitectureTest`.
- 그 밖의 선택은 [공통 검사 표](../../../../../../docs/conventions/testing/selection.md#selection)를 따릅니다.
