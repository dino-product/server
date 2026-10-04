# ADR-001: 백엔드 아키텍처

- 상태: Accepted
- 기준일: 2026-10-04
- 범위: 초기 백엔드의 구조·기술 선택·카카오·Apple OIDC 인증과 계정·예제 계약과 검증 체계. 에이전트 작업 방식은 [ADR-002](002-agentic-coding-rules.md)가 다룹니다.

## 배경과 목적

새 Java 백엔드 프로젝트가 실행·모듈 경계·검증 체계를 갖춘 상태에서 시작하도록 합니다. 하나의 배포 단위로 운영 비용을 낮추면서 비즈니스 책임과 기술 의존성을 분리합니다. 제품 기능이나 세팅 과정의 실행 기록은 포함하지 않습니다.

## 배포와 모듈 경계

- Java 21·Spring Boot·Spring Modulith를 사용하고 Gradle의 단일 실행 JAR로 배포합니다. 버전 원본은 `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle Wrapper입니다.
- 비즈니스 책임별 패키지를 Application Module로 구분합니다. 초기에는 별도 Gradle 모듈이나 서비스로 나누지 않고 `ApplicationModules.verify()`로 경계·순환 의존성을 검증합니다.
- 비즈니스 모듈의 공개 계약은 모듈 루트에 둡니다. `shared`의 오류·OpenAPI·Security 확장점 계약은 named interface로 나누어 필요한 계약만 허용합니다. 내부 패키지 공개로 검증을 우회하지 않습니다.

모듈별 책임·공개 타입·허용 의존성은 [도메인 지도](../domain/README.md), 패키지와 수정 위치는 [아키텍처 규칙](../../.claude/skills/dino-architecture/references/architecture.md#modules)이 원본입니다.

## 제품 모듈 골격

- 제품 구현의 책임 경계를 준비하기 위해 `organization`, `schedule`, `notification`을 Application Module 골격으로 등록합니다. `user` 예제와 `shared`는 유지하고 `auth`는 실제 인증 모듈로 구현합니다.
- 조직·소속·초대, 작업 생애주기, 알림을 각각의 책임으로 계획합니다. 각 모듈의 현재 구현 범위와 허용 의존성은 [도메인 지도](../domain/README.md#모듈별-책임과-공개-계약)가 원본이며 이 결정 기록에 복제하지 않습니다.
- 실제 사용 전 의존성을 열어 두지 않도록 골격 모듈(`package-info.java`만 있는 모듈)의 `allowedDependencies`는 빈 배열로 명시합니다. 구현이 공개 계약을 사용할 때 필요한 항목만 추가하고 도메인 지도를 함께 갱신합니다.
- [도메인 지도](../domain/README.md#모듈별-책임과-공개-계약)는 골격을 포함한 현재 모듈 구성의 원본입니다. [BC 설계 초안](../domain/bounded-contexts.md#bounded-contexts)의 애그리게잇·컨텍스트 관계·향후 확장은 현재 구현 계약과 구분합니다.
- `ModularityTest`는 6개 모듈 구성과 경계를 검사합니다. 골격 등록만으로 제품 기능이나 계획된 모듈 간 연동이 구현되었다고 보지 않습니다.

## 인증과 계정

- 소셜 로그인은 카카오·Apple OIDC를 함께 지원하고, 제공자·클라이언트별로 증명을 얻는 경로만 다르게 둡니다. 카카오 앱과 Apple iOS는 네이티브 SDK가 돌려준 id_token을 제출하고, Apple 웹·Android는 서버가 authorize·callback으로 code와 id_token을 받습니다(카카오 웹 흐름은 후속 구현). 모든 경로는 공통 골격(JWKS 서명·발급자·허용 클라이언트·만료)의 제공자별 id_token 검증기와 서버 발급 nonce로 합류하며 제공자 토큰을 API 인증에 쓰지 않습니다. iOS 앱이 카카오 같은 타사 로그인을 제공하면 App Store 심사 지침 4.8에 따라 개인정보 보호형 대체 로그인이 필요하므로 Apple을 두 번째 제공자로 붙였고, 제품 결정은 Notion 반영을 기다립니다([A-10](../domain/bounded-contexts.md#auth-gaps)).
- nonce는 서버가 발급하고 한 번만 소비하므로 이미 쓴 id_token은 다시 제출할 수 없습니다. Apple은 앱이 nonce의 SHA-256 hex를 요청에 넣어 id_token에 해시가 실리므로 서버는 raw를 발급하고 해시로 보관·원자 소비합니다. 앱 흐름은 브라우저 세션이 없어 nonce를 발급 주체와 따로 묶지 않으며 그 이유는 [Auth 로그인 흐름](../domain/auth.md#카카오-로그인-흐름)에 둡니다. 웹 흐름은 state/nonce를 시작 브라우저의 연결 쿠키와 묶고, 콜백 뒤 Access Token 대신 60초 일회성 교환 코드만 복귀 주소에 실어 토큰이 URL에 남지 않게 합니다.
- 계정 원본은 `auth`가 소유합니다. `Account`는 제공자별 외부 식별(`ExternalIdentity`)과 등록 시각만 가지며 `accounts`·`oauth_credentials` 테이블에 저장합니다. 첫 로그인에 계정을 만들고 이후 같은 제공자의 같은 `sub`는 같은 계정입니다. 카카오 계정과 Apple 계정은 별개이며, Apple 비공개 릴레이 이메일·카카오 이메일 미동의 때문에 이메일 매칭 자동 연결은 불안정하고 탈취 경로가 되므로 하지 않습니다. 이름·연락처 같은 프로필과 조직 역할은 auth에 두지 않으며 소유 컨텍스트는 [BC-001](../domain/bounded-contexts.md#미해결-설계-이슈)에서 결정합니다. `user` 예제는 그대로 둡니다.
- 자체 자격증명은 HS256 Access JWT(`iss`, `sub`=accountId, `jti`, `exp`, `token_use=access`)입니다. 로그아웃은 토큰의 `jti`를 남은 유효 시간 동안 Redis에 기억해 폐기하며, Refresh 회전은 후속 결정입니다.
- Apple 계정 삭제 시 토큰 철회가 요구되므로 iOS·웹 로그인 모두 authorization code를 교환해 refresh token을 받아 `apple_refresh_tokens`에 AES-256-GCM으로 암호화해 보관합니다. 교환 실패는 로그인 실패로, Apple이 거절한 code는 401(AUTH-005), Apple 장애·설정 오류는 502(AUTH-006)로 구분해 클라이언트가 재로그인과 재시도를 고를 수 있게 합니다. 저장 키 교체·보관 정책은 후속 결정입니다. Apple 토큰 API를 기다리는 로그인 유즈케이스는 DB 커넥션을 잡지 않도록 메서드 트랜잭션을 두지 않습니다.
- 인증 실패 응답 정책: 토큰이 없으면 401, 토큰이 있는데 폐기·만료·위조됐으면 카카오·Apple 로그인 경로를 뺀 어느 경로든 실제 존재하지 않는 자원과 같은 404 본문으로 응답해 보호 자원의 존재와 실패 이유를 드러내지 않습니다.
- 인증 필터와 공개 경로는 `shared::security`의 `SecurityFilterChainCustomizer`로 공통 SecurityFilterChain에 덧붙입니다. shared는 비즈니스 모듈에 의존하지 않고 모듈은 별도 SecurityFilterChain을 만들지 않습니다.

상세 흐름·설정·오류 코드는 [Auth](../domain/auth.md)가 소유합니다.

## Application과 모델 분리

- Domain, Application Port, Adapter를 분리합니다. Domain은 기술에 의존하지 않고 Application은 Port를 통해 외부 기술을 사용합니다. 입력 Adapter는 입력 Port에 위임합니다.
- 입력 Port는 상태 변경·후속 처리의 `application/port/in/command`와 조회의 `application/port/in/query`로 나눕니다. DTO는 사용하는 계약의 `command/dto`, `query/dto`에 둡니다. 등록 결과 `RegisteredUserInfo`도 command가 소유하며 접미사만으로 위치를 정하지 않습니다.
- 서비스 구현은 `application/service`에 둡니다. 구현이 늘어 책임별 탐색이 필요할 때만 `service/command`, `service/query`로 세분화하고 빈 패키지를 미리 만들지 않습니다.
- Domain 모델·JPA Entity·Web DTO를 분리합니다. 모듈 간 공개 계약은 내부 Port·DTO 패키지로 옮기지 않습니다.
- 같은 대상도 모듈별 정보·의미·규칙이 다르면 소비 모듈이 자체 Domain 모델을 소유합니다. 단순 표시·조회에는 별도 Domain 모델을 강제하지 않으며, 모델 분리는 테이블 복제나 원본 데이터 소유권 이전을 뜻하지 않습니다.

## 모듈 간 통신

- 다른 비즈니스 모듈과는 제공 모듈이 모듈 루트에 둔 공개 인터페이스 호출과 공개 이벤트로만 통신합니다. 결과가 즉시 필요하면 공개 인터페이스를 호출하고, 일어난 사실을 알리고 후속 처리를 맡기는 부수 효과는 커밋 후 비동기 이벤트로 처리합니다. 모든 통신을 이벤트로 통일하지 않습니다.
- 소비 모듈의 Application Service는 제공 모듈의 공개 인터페이스를 주입받아 호출하는 것을 기본으로 합니다. Domain에는 계약 DTO 대신 필요한 값만 넘깁니다.
- 소비 쪽 출력 Port·Adapter(ACL)는 두 모듈의 모델이 크게 달라 소비 모델을 보호해야 하는 관계에서만 둡니다. 호출마다가 아니라 모듈 관계마다 정합니다.

필요 이상의 추상화를 피한다는 팀 원칙에 따라 모든 모듈 간 호출에 소비 쪽 Port·Adapter를 두지 않기로 했습니다. 제공 모듈의 공개 계약이 이미 인터페이스이므로 테스트 대체와 경계 검증은 유지되고, Domain이 다른 모듈 타입을 모르므로 모듈을 분리할 때의 영향은 Application 계층에서 멈춥니다. Spring Modulith도 공개 Spring Bean과 이벤트를 모듈이 제공하는 계약으로 봅니다. 대신 Application이 계약 DTO에 의존하는 결합은 감수하며, 모델 차이가 큰 관계에서만 ACL 변환 비용을 들입니다. Domain의 다른 비즈니스 모듈 타입 참조는 `ArchitectureTest`가 막습니다.

방식 선택과 세부 규칙은 [모듈 간 통신](../../.claude/skills/dino-architecture/references/communication.md#communication)을 따릅니다.

## 예제 모듈

- `user`는 등록·표시 이름 불변식·순수 Domain·JPA Adapter·공개 요약 조회·이벤트 발행을 보여 줍니다.
- `auth`의 subject 조회·등록 이벤트 후속 처리는 ACL 관계를 보여 주는 예제입니다. user의 공개 조회 결과는 출력 Port·Adapter에서, 이벤트는 리스너에서 auth 소유 값으로 변환하며 Application과 Domain은 user 타입을 참조하지 않습니다.
- 예제 subject에는 인증 의미를 부여하지 않습니다. 실제 인증은 [인증과 계정](#인증과-계정)의 결정을 따릅니다.

현재 모듈 목록은 [도메인 지도](../domain/README.md), API·구현 범위는 [User](../domain/user.md)·[Auth](../domain/auth.md)가 소유합니다. 예제는 변환 코드가 늘어나는 비용을 감수하여 ACL 방식의 경계를 보여 주도록 선택했습니다. 기본 방식(공개 인터페이스 직접 주입)의 예제는 아닙니다.

## HTTP와 오류 계약

- Web DTO와 Application DTO를 분리합니다. 공통 인프라가 응답·오류 처리와 OpenAPI 래퍼 생성을 담당하고, 각 모듈의 ControllerDocs는 실제 결과 타입과 오류 계약을 선언합니다.
- 모듈 전용 오류는 `application.error`가 소유하고 `CommonErrorCode`에는 공통 오류만 둡니다. `BusinessException(BaseCode)`와 전역 처리를 유지합니다. 응답·OpenAPI 매핑 중복을 줄이기 위해 Application 오류에는 HTTP 상태를 허용하지만 Domain 불변식 오류에는 이 웹 지향 계약을 사용하지 않습니다.
- 신규 오류 식별자는 HTTP 상태와 독립적인 모듈별 일련번호를 사용합니다. 상태가 바뀌어도 식별자를 유지하며 기존 응답 코드는 호환성을 위해 보존합니다.

세부 계약은 [Web API](../../.claude/skills/dino-architecture/references/web.md#http)·[OpenAPI](../../.claude/skills/dino-architecture/references/web.md#controllers)·[아키텍처](../../.claude/skills/dino-architecture/references/architecture.md#modules)를 따릅니다.

## 영속성과 이벤트

- PostgreSQL·JPA·QueryDSL을 사용하고 DB 검증은 PostgreSQL Testcontainers로 수행합니다. H2로 대체하지 않아 SQL·매핑 차이를 실제 DB에서 확인합니다.
- 공통·운영 설정은 스키마를 자동 변경하지 않습니다. 로컬 예제·테스트만 임시 스키마를 사용하고 활성 프로필·운영 DB 접속 정보는 실행 환경에서 지정합니다.
- 모듈 간 이벤트는 변경 트랜잭션에서 발행하고 커밋 후 비동기로 소비합니다. 시간은 주입받은 `Clock`과 UTC `Instant`를 사용합니다.
- 전달 보장이 필요한 서비스는 영속 저장소·재처리·멱등성 정책을 함께 설계합니다. 최소 예제의 후속 처리는 모듈 경계를 보여 주는 데 한정하며 전달 보장을 암시하지 않습니다.
- nonce·폐기 토큰처럼 만료가 있는 임시 상태는 Redis에 두고 PostgreSQL에는 원본 데이터만 저장합니다. 마이그레이션 도구는 제품 요구에 따라 결정합니다. 운영 적용 전 스키마 준비와 Actuator 접근 정책을 구성해야 합니다.

프로필·트랜잭션·이벤트의 실행 규칙은 [영속성·이벤트](../../.claude/skills/dino-architecture/references/persistence.md#transactions), 실행 준비는 [빠른 시작](../../README.md#빠른-시작)을 따릅니다.

## 포맷과 검증

- Spotless의 Palantir Java Format으로 Java 레이아웃을 통일하고 Checkstyle로 명명·코드 규칙을 검사합니다.
- `ModularityTest`는 모듈 경계, `ArchitectureTest`는 내부 의존성·Domain의 다른 비즈니스 모듈 참조와 JPA Entity 위치를 검사합니다. API·OpenAPI 통합 테스트는 공개 계약을, `UserRegistrationEventIntegrationTest`는 실제 커밋·롤백·스레드·트랜잭션 경계를 확인합니다.
- CI는 Docker와 필터 없는 전체 테스트를 요구하고 건너뛰기를 실패로 처리합니다. 로컬 집중 검사는 빠른 피드백에 사용하며 전체 검증을 대체하지 않습니다.

포맷 규칙은 [포맷·명명](../../.claude/skills/dino-architecture/references/style.md#style), 테스트 설계·CI 정책은 [테스트](../../.claude/skills/dino-testing/references/design.md#design), 완료 명령은 [루트 지침](../../AGENTS.md#검증과-완료)이 원본입니다.
