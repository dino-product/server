# ADR-001: 백엔드 아키텍처

- 상태: Accepted
- 기준일: 2026-09-14
- 범위: 초기 백엔드의 구조·기술 선택·예제 계약과 검증 체계. 에이전트 작업 방식은 [ADR-002](002-agentic-coding-rules.md)가 다룹니다.

## 배경과 목적

새 Java 백엔드 프로젝트가 실행·모듈 경계·검증 체계를 갖춘 상태에서 시작하도록 합니다. 하나의 배포 단위로 운영 비용을 낮추면서 비즈니스 책임과 기술 의존성을 분리합니다. 제품 기능이나 세팅 과정의 실행 기록은 포함하지 않습니다.

## 배포와 모듈 경계

- Java 21·Spring Boot·Spring Modulith를 사용하고 Gradle의 단일 실행 JAR로 배포합니다. 버전 원본은 `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle Wrapper입니다.
- 비즈니스 책임별 패키지를 Application Module로 구분합니다. 초기에는 별도 Gradle 모듈이나 서비스로 나누지 않고 `ApplicationModules.verify()`로 경계·순환 의존성을 검증합니다.
- 비즈니스 모듈의 공개 계약은 모듈 루트에 둡니다. `shared`의 오류·OpenAPI 계약은 named interface로 나누어 필요한 계약만 허용합니다. 내부 패키지 공개로 검증을 우회하지 않습니다.

모듈별 책임·공개 타입·허용 의존성은 [도메인 지도](../domain/README.md), 패키지와 수정 위치는 [아키텍처 규칙](../conventions/architecture/modules.md#modules)이 원본입니다.

## Application과 모델 분리

- Domain, Application Port, Adapter를 분리합니다. Domain은 기술에 의존하지 않고 Application은 Port를 통해 외부 기술을 사용합니다. 입력 Adapter는 입력 Port에 위임합니다.
- 입력 Port는 상태 변경·후속 처리의 `application/port/in/command`와 조회의 `application/port/in/query`로 나눕니다. DTO는 사용하는 계약의 `command/dto`, `query/dto`에 둡니다. 등록 결과 `RegisteredUserInfo`도 command가 소유하며 접미사만으로 위치를 정하지 않습니다.
- 서비스 구현은 `application/service`에 둡니다. 구현이 늘어 책임별 탐색이 필요할 때만 `service/command`, `service/query`로 세분화하고 빈 패키지를 미리 만들지 않습니다.
- Domain 모델·JPA Entity·Web DTO를 분리합니다. 모듈 간 공개 계약은 내부 Port·DTO 패키지로 옮기지 않습니다.
- 같은 대상도 모듈별 정보·의미·규칙이 다르면 소비 모듈이 자체 Domain 모델을 소유하고 공개 DTO·이벤트를 Port·Adapter 경계에서 변환합니다. 단순 표시·조회에는 별도 Domain 모델을 강제하지 않으며, 모델 분리는 테이블 복제나 원본 데이터 소유권 이전을 뜻하지 않습니다.

상세 기준은 [소비 모듈의 모델과 외부 정보 변환](../conventions/architecture/external-models.md#external-models)을 따릅니다.

## 예제 모듈

- `user`는 등록·표시 이름 불변식·순수 Domain·JPA Adapter·공개 요약 조회·이벤트 발행을 보여 줍니다.
- `auth` 예제는 공개 조회 결과와 이벤트를 Adapter에서 자체 값으로 변환하여 소비 모듈의 독립성을 보여 줍니다. Application과 Domain은 user 타입을 참조하지 않습니다.
- 예제 subject에는 인증 의미를 부여하지 않습니다. 실제 인증 확장은 별도 자격 증명 검증과 접근 정책을 설계해야 합니다.

현재 모듈 목록은 [도메인 지도](../domain/README.md), API·구현 범위는 [User](../domain/user.md)·[Auth](../domain/auth.md)가 소유합니다. 예제는 변환 코드가 늘어나는 비용을 감수하여 원본 모델 공유 없이 소비 모듈의 경계를 보여 주도록 선택했습니다.

## HTTP와 오류 계약

- Web DTO와 Application DTO를 분리합니다. 공통 인프라가 응답·오류 처리와 OpenAPI 래퍼 생성을 담당하고, 각 모듈의 ControllerDocs는 실제 결과 타입과 오류 계약을 선언합니다.
- 모듈 전용 오류는 `application.error`가 소유하고 `CommonErrorCode`에는 공통 오류만 둡니다. `BusinessException(BaseCode)`와 전역 처리를 유지합니다. 응답·OpenAPI 매핑 중복을 줄이기 위해 Application 오류에는 HTTP 상태를 허용하지만 Domain 불변식 오류에는 이 웹 지향 계약을 사용하지 않습니다.
- 신규 오류 식별자는 HTTP 상태와 독립적인 모듈별 일련번호를 사용합니다. 상태가 바뀌어도 식별자를 유지하며 기존 응답 코드는 호환성을 위해 보존합니다.

세부 계약은 [Web API](../conventions/web/http.md#http)·[OpenAPI](../conventions/web/openapi/controllers.md#controllers)·[아키텍처](../conventions/architecture/modules.md#modules)를 따릅니다.

## 영속성과 이벤트

- PostgreSQL·JPA·QueryDSL을 사용하고 DB 검증은 PostgreSQL Testcontainers로 수행합니다. H2로 대체하지 않아 SQL·매핑 차이를 실제 DB에서 확인합니다.
- 공통·운영 설정은 스키마를 자동 변경하지 않습니다. 로컬 예제·테스트만 임시 스키마를 사용하고 활성 프로필·운영 DB 접속 정보는 실행 환경에서 지정합니다.
- 즉시 결과가 필요한 모듈 간 조회는 공개 API로 호출하고, 등록 완료 후속 처리는 커밋 후 비동기 이벤트로 실행합니다. 시간은 주입받은 `Clock`과 UTC `Instant`를 사용합니다.
- 전달 보장이 필요한 서비스는 영속 저장소·재처리·멱등성 정책을 함께 설계합니다. 최소 예제의 후속 처리는 모듈 경계를 보여 주는 데 한정하며 전달 보장을 암시하지 않습니다.
- 마이그레이션 도구와 실제 인증 수단은 제품 요구에 따라 결정합니다. 운영 적용 전 스키마 준비와 API·Actuator 접근 정책을 구성해야 합니다.

프로필·트랜잭션·이벤트의 실행 규칙은 [영속성·이벤트](../conventions/persistence/transactions.md#transactions), 실행 준비는 [빠른 시작](../../README.md#빠른-시작)을 따릅니다.

## 포맷과 검증

- Spotless의 Palantir Java Format으로 Java 레이아웃을 통일하고 Checkstyle로 명명·코드 규칙을 검사합니다.
- `ModularityTest`는 모듈 경계, `ArchitectureTest`는 내부 의존성과 JPA Entity 위치를 검사합니다. API·OpenAPI 통합 테스트는 공개 계약을, `UserRegistrationEventIntegrationTest`는 실제 커밋·롤백·스레드·트랜잭션 경계를 확인합니다.
- CI는 Docker와 필터 없는 전체 테스트를 요구하고 건너뛰기를 실패로 처리합니다. 로컬 집중 검사는 빠른 피드백에 사용하며 전체 검증을 대체하지 않습니다.

포맷 규칙은 [포맷·명명](../conventions/java/style.md#style), 테스트 설계·CI 정책은 [테스트](../conventions/testing/design.md#design), 완료 명령은 [루트 지침](../../AGENTS.md#검증과-완료)이 원본입니다.
