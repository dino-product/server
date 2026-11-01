<a id="design"></a>
# 테스트 설계

테스트 수정은 [테스트 지침](../../../../src/test/java/com/orbit/AGENTS.md)과 대상 모듈 지침을 함께 적용합니다.

- 새 기능·버그 수정은 기대 행동·재현 조건의 의도한 실패부터 확인합니다. 구현 복제나 문서·포맷 변경만을 위한 무의미한 테스트는 추가하지 않습니다.
- 구현 전에 정상·거부·경계 조건과 검사 수단을 정합니다. 테스트 수·mock 호출 횟수만으로 완성을 판단하지 않습니다. 거부·철회 검사의 토큰·세션·데이터는 해당 조건 외에는 유효해야 합니다.
- Domain/Application 규칙·흐름은 Spring Context 없는 단위 테스트, 독립 모듈 조립은 `@ApplicationModuleTest`로 검증합니다.
- DB·영속성·전체 API는 H2 대체 없이 PostgreSQL Testcontainers를 사용합니다. 핵심 API는 MockMvc로 공통 응답·보안·트랜잭션·영속성을 함께 검증합니다.
- 전체 API 통합 테스트에서 실제 Access Token으로 다른 모듈 API를 부르면 계정이 가입을 마쳐야 합니다. `com.orbit.support.SignupTestSupport#signUpNewAccount`로 만들고, 계정 식별자로 토큰을 직접 서명하는 테스트는 테스트 인스턴스마다 둔 `SignupTestSupport.SignupOnce`로 그 계정의 가입을 마칩니다([가입 공통 검사](../../../../docs/domain/profile.md#profile-signup-gate)). `@ApplicationModuleTest`에는 이 검사가 포함되지 않습니다.
- 전체 API·이벤트·모듈 통합 테스트에 클래스 수준 `@Transactional`을 붙이지 않습니다. 매핑용 `@DataJpaTest`의 기본 롤백은 허용하되 커밋·이벤트 검증을 대체하지 않습니다.
- 내부 시간은 고정 `Clock`/`Instant`로 검증합니다. 저장소 TTL 등 외부 시계 검사는 현재 시각으로 두 시계를 맞추며 고정 달력 날짜로 실시간 만료 키를 만들지 않습니다. 비동기는 제한 시간 있는 조건 기반 대기를 사용하고 데이터는 공유 상태·실행 순서와 격리합니다.
- 테스트용 fake는 `src/test`에 두고 `@Component` 등 스캔 대상 애너테이션을 붙이지 않습니다.
- 다른 모듈 계약과 ACL 출력 Port는 단위 테스트에서 fake나 mock으로, 모듈 조립 테스트(`@ApplicationModuleTest`)에서 `@MockitoBean`이나 명시적으로 등록한 fake로 대체합니다. 이 fake는 테스트가 결과를 직접 넣고 아무것도 넣지 않으면 빈 결과를 돌려줍니다. 계약의 반환 형태와 계약이 허용하는 실패(빈 결과, 계약에 정의된 예외)만 흉내 내며, 호출 전제 검사와 테스트 준비 편의 동작 외에 제공 모듈의 업무 규칙은 구현하지 않습니다.
- 리스너 직접 호출·mock은 위임·발행 계약만 검증합니다. 커밋 전 미처리·커밋 후 별도 스레드/트랜잭션·롤백 시 미처리는 이벤트 통합 테스트로 확인합니다.
- 저장소 원자 연산·TTL은 실제 저장소 통합 검사, 인증 토큰 claim은 실제 검증기로 확인합니다. 외부 제공자 응답은 고정할 수 있으나 대체 경계를 명시하고 실제 제공자 E2E로 보고하지 않습니다.
- 통과를 위해 assertion·아키텍처 규칙·CI 검사를 약화하지 않습니다. 실패는 제품·테스트·환경 문제로 구분합니다.
- 계약 불일치는 오류가 가리키는 선언·구현·호출부를 함께 비교하고 원인 수정 후 집중 검사를 재실행합니다. 한쪽 선언만 추측으로 바꾸며 반복 실행하지 않습니다.

<a id="fixtures"></a>
## 공통 준비물과 컨텍스트 재사용

Spring 테스트 시간은 테스트 본문보다 컨텍스트 생성이 좌우합니다. 같은 준비물을 쓰는 테스트는 컨텍스트 하나를 함께 씁니다.

| 대상 | 상속·구현 | 비고 |
| --- | --- | --- |
| 전체 API·이벤트 통합 | `support/IntegrationTestSupport` | MockMvc 포함. 클래스마다 `@AutoConfigureMockMvc`를 다시 붙이지 않음 |
| 영속 어댑터(`@DataJpaTest`) | `support/PersistenceAdapterTestSupport` | 모든 모듈의 `adapter.out.persistence` 패키지와 감사 설정·`Clock`을 함께 올림. 대상 어댑터를 `@Import`하지 않음 |
| 인증 API(Apple·Kakao 스텁) | `auth/adapter/in/web/AuthApiTestSupport` | 스텁은 JVM마다 한 번 뜨고 세 클래스가 공유 |
| Spring 없는 Redis 저장소 | `support/RedisAdapterTestSupport` | 공유 Redis를 클래스 시작 때 비움 |
| 모듈 조립(`@ApplicationModuleTest`) 등 직접 조립 | `support/SharedContainers` 구현 | 컨테이너만 공유 |

- **컨테이너는 JVM마다 한 번만 띄웁니다.** PostgreSQL·Redis는 `SharedContainers`의 static 필드이고 `@ServiceConnection`으로 연결합니다. 컨테이너를 `@Bean`이나 클래스별 `@Container`로 새로 선언하지 않습니다.
- **격리는 클래스 단위입니다.** 최상위 테스트 클래스가 시작할 때 DB의 모든 테이블(`flyway_schema_history` 제외)과 Redis가 비워집니다. `@Nested` 클래스는 바깥 클래스의 데이터를 이어 쓰고, Redis는 Redis Bean이 있는 컨텍스트에서만 비웁니다. 같은 클래스 안의 테스트끼리는 고유 값을 쓰거나 직접 정리합니다.
- **컨텍스트를 새로 만드는 설정은 꼭 필요할 때만 씁니다.** 클래스마다 다른 `@Import`·`@MockitoBean`·`@TestPropertySource`·`@DynamicPropertySource`·프로필은 새 컨텍스트를 만듭니다. 여러 클래스가 같은 대체가 필요하면 공통 베이스로 올리고, 대체 범위가 다른 테스트의 의도를 바꾸면 따로 둡니다.
- **`@DirtiesContext`를 쓰지 않습니다.** 닫힌 컨텍스트는 캐시에서 빠져 같은 설정의 다음 클래스가 컨텍스트를 다시 띄우므로 테스트 시간이 늘어납니다. 스키마는 공유 DB에 Flyway가 한 번 적용하고 컨텍스트가 닫혀도 지우지 않습니다.
- **로컬에서는 테스트를 CPU 코어 수의 절반, 최대 2개의 JVM으로 나눠 실행합니다**(`gradle/testing.gradle.kts`의 `maxParallelForks`). CI 러너(4코어)는 JVM끼리 CPU를 나눠 쓰면 더 느려 1개로 실행합니다. fork마다 컨테이너와 컨텍스트가 따로 뜨므로, JVM 전역 상태(static 필드·시스템 속성)에 기대는 테스트는 fork 배정과 실행 순서에 의존하지 않게 씁니다.
