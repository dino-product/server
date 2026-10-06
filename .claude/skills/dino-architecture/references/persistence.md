# 영속성·트랜잭션·이벤트·프로필

필요한 절만 읽습니다.

<a id="jpa"></a>
## JPA 모델과 생성물

- JPA Entity는 Domain 모델과 분리하고 Persistence Adapter에서 변환합니다. 기본 생성자는 `protected`, 연관관계는 지연 로딩이 기본입니다.
- Entity에 공개 `@Setter`를 두지 않고 의미 있는 도메인 메서드로 상태를 변경합니다.
- QueryDSL 생성물은 `build/generated/querydsl`에 두며 직접 수정하지 않습니다.

<a id="transactions"></a>
## 트랜잭션과 시간

- 변경 UseCase는 `@Transactional`, 조회 UseCase는 `@Transactional(readOnly = true)`를 적용합니다.
- 예외: DB를 쓰지 않고 Redis·외부 검증기 같은 다른 저장소만 쓰는 UseCase(예: auth의 nonce 발급·Access Token 인증·로그아웃)는 트랜잭션을 붙이지 않고 서비스 Javadoc에 그 사실을 적습니다. JPA 트랜잭션은 시작할 때 DB 커넥션을 잡으므로, 매 요청 토큰 인증처럼 DB가 필요 없는 경로가 커넥션을 점유하거나 DB 장애에 함께 실패하지 않게 하기 위해서입니다. DB 접근이 추가되면 위 규칙을 적용합니다.
- 예외: 외부 HTTP 호출(예: auth의 Apple 토큰 교환)을 기다리는 로그인 UseCase는 그동안 DB 커넥션을 잡지 않도록 메서드 트랜잭션을 두지 않습니다. 대신 각 저장은 저장소 단위 트랜잭션(별도 트랜잭션 등록·멱등 upsert)으로 확정하고, 중간에 실패해도 다시 시도하면 같은 결과가 되도록 설계해 서비스 Javadoc에 적습니다.
- 날짜·시각은 UTC `Instant`, 테스트 가능한 시간은 주입받은 `Clock`을 사용합니다. 주입되는 `Clock`은 저장소(PostgreSQL) 정밀도인 마이크로초 단위로 끊으므로, 시각 컬럼도 마이크로초 정밀도로 매핑합니다.

<a id="events"></a>
## 트랜잭션 이벤트

- 완료 이벤트는 변경 트랜잭션 안에서 발행하고 `@ApplicationModuleListener`로 커밋 후 소비합니다. 롤백된 트랜잭션의 이벤트는 소비하지 않습니다.
- `shared.internal.config.AsyncConfig`의 `@EnableAsync`와 Boot TaskExecutor를 사용합니다. 리스너는 발행자와 다른 스레드·별도 트랜잭션에서 실행합니다.
- 발생 시각은 발행 시점의 `Clock` 값이며 커밋 시각이 아닙니다. 후속 처리는 API 응답과 독립적이며 소비 실패가 이미 커밋된 등록을 되돌리지 않습니다.
- 전달 보장이 필요한 확장은 저장소·재처리·멱등성과 ADR을 함께 설계합니다. 현재 구현은 [Auth의 이벤트 흐름](../../../../docs/domain/auth.md#이벤트-흐름)을 참고합니다.
- 실제 커밋·롤백·소비 스레드를 통합 테스트로 검증합니다. 테스트 전체를 트랜잭션으로 감싸지 않습니다.

참고: [영속성과 이벤트 결정](../../../../docs/adr/001-backend-architecture.md#영속성과-이벤트).

<a id="profiles"></a>
## 프로필과 실행 환경

- 패키징된 설정은 기본 활성 프로필을 선택하지 않습니다. 환경의 `SPRING_PROFILES_ACTIVE`로 지정합니다.
- 공통·`prod`의 `ddl-auto`는 `none`, `create-drop`은 로컬 예제·테스트에서만 허용합니다. 로컬 DB에 보존할 데이터를 넣지 않습니다.
- 운영은 `SPRING_PROFILES_ACTIVE=prod`로 실행합니다. `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`는 기본값 없이 받아 누락 시 시작에 실패하게 합니다. `KAKAO_ALLOWED_AUDIENCES`, `AUTH_JWT_SECRET`, `APPLE_ALLOWED_AUDIENCES`, `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`, `APPLE_REFRESH_TOKEN_ENCRYPTION_KEY`, `APPLE_SERVICES_ID`, `APPLE_WEB_REDIRECT_URI`, `APPLE_WEB_RETURN_URI`, `APPLE_ANDROID_RETURN_URI`는 모든 프로필에서 기본값 없이 받습니다([Auth 설정](../../../../docs/domain/auth.md#설정)).
- `prod,local` 동시 지정에도 로컬 설정이 활성화되지 않도록 프로필 표현식으로 보호합니다. 운영 OpenAPI UI는 비활성화합니다.
- 설정은 `application-{profile}.yml`로 분리하고 공통 설정을 중복하지 않습니다.
- 현재 마이그레이션 도구는 없습니다. 운영 배포 전에 마이그레이션 전략을 ADR로 결정합니다.

실행 명령: [빠른 시작](../../../../README.md#빠른-시작). 참고: [영속성 결정](../../../../docs/adr/001-backend-architecture.md#영속성과-이벤트).
