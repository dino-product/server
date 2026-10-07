# 영속성·트랜잭션·이벤트·프로필

필요한 절만 읽습니다.

<a id="jpa"></a>
## JPA 모델과 생성물

Entity는 `{module}/adapter/out/persistence`에만 두고 Domain 모델과 분리합니다. 테이블·컬럼의 원본은 팀 ERD([ERDCloud](../../dino-erd/references/erdcloud.md))이며, Entity는 ERD를 따르되 아래 규칙으로 옮깁니다.

- **구조**: 기본 생성자는 `protected`, 연관관계는 지연 로딩이 기본입니다. 공개 `@Setter`를 두지 않고 변환기가 쓰는 의미 있는 변경 메서드만 둡니다. `equals`/`hashCode`는 정의하지 않습니다(영속화 전 식별자가 없고 컬렉션은 Hibernate가 관리).
- **이름**: 테이블·컬럼은 소문자 snake_case입니다. ERDCloud 표시명의 대소문자(`Work_Assignments` 등)는 따르지 않습니다. Entity 클래스는 `{Name}JpaEntity`, Spring Data 저장소는 `SpringData{Name}Repository`, 출력 Port 구현은 `{Name}PersistenceAdapter`입니다.
- **감사 컬럼**: 모든 Entity는 `shared::persistence`의 `BaseTimeEntity`를 상속해 `created_at`·`updated_at`을 가지며 두 컬럼을 직접 선언하지 않습니다. ERD에 두 컬럼이 빠진 테이블이 있으면 Entity를 기준으로 ERD에 추가합니다(ERD의 감사 컬럼 유무는 테이블마다 달라 원본으로 삼지 않음). 값은 `shared`의 JPA 감사 설정이 [주입 `Clock`](#transactions)으로 채우며 Domain으로 변환하지 않습니다. 등록 시각·완료 시각처럼 업무 규칙이 쓰는 시각은 Domain이 정한 값을 별도 컬럼에 저장합니다. "누가"(등록자·처리자 등)는 테이블마다 이름·뜻이 달라 공통 부모에 두지 않고, Domain 값으로 각 Entity의 컬럼에 둡니다. 이 규약 전에 만든 `user`·`auth`의 Entity는 아직 상속하지 않으며 해당 모듈 변경 때 맞춥니다.
- **값객체**: `@Embeddable` 대신 컬럼으로 펼칩니다. 같은 값객체가 여러 테이블에 들어가도 각 Entity가 자기 컬럼을 가집니다(ERD가 그렇게 그려져 있음).
- **모듈 간 참조**: 다른 모듈이 소유한 테이블의 식별자는 `Long` 컬럼으로만 둡니다. `@ManyToOne`·FK를 걸지 않습니다(ERD 메모 "모듈 간 참조는 관계선 없이 논리 참조"). 같은 모듈 안의 부모–자식만 연관관계로 매핑하고 자식은 부모의 `cascade = ALL, orphanRemoval = true`로 생명주기를 함께합니다.
- **타입**: 시각은 UTC `Instant`(`timestamptz`), 소요시간은 ERD가 `INTERVAL`이면 `Duration`에 `@JdbcTypeCode(SqlTypes.INTERVAL_SECOND)`를 붙입니다. enum은 `@Enumerated(EnumType.STRING)`이고 길이는 ERD 코멘트를 따릅니다. 금액은 원 단위 `bigint`입니다.
- **값 검증**: Entity는 값 불변식을 검증하지 않습니다. 길이·null 허용은 컬럼 제약(`nullable`, `length`)으로만 선언하고 Bean Validation을 붙이지 않습니다. 값 검증의 원본은 Domain이며, 컬럼 길이는 Domain 상수(예: `CustomerInfo.MAX_NAME_LENGTH`)와 같게 둡니다. Domain의 입력 제한을 좁힐 때는 저장된 데이터의 복원 실패를 막기 위해 기존 데이터 이관을 함께 봅니다.
- **예외**: Entity·Adapter가 던지는 예외는 두 종류뿐입니다. 생명주기·호출 전제 위반(이미 식별자가 있는 새 Entity 저장, 트랜잭션 없는 잠금 등)은 `IllegalArgumentException`/`IllegalStateException`(프로그래밍 오류)이고, DB 예외는 Application이 처리할 수 있는 출력 Port의 계약 예외로 변환합니다(예: 유니크 위반 → `DuplicateIdentityException`). Domain 오류 코드로 직접 바꾸지 않습니다.
- **변환**: Domain → Entity는 `static {Name}JpaEntity.from(domain)`, Entity → Domain은 `toDomain()`이 기본이며 Entity 안에 둡니다. 애그리게잇에 자식 Entity·값객체가 여럿이라 Entity가 비대해지면 패키지 private `{Name}Mapper`로 변환을 빼고 Entity는 필드·생성자·변경 메서드만 남깁니다. 복원은 항상 Domain의 `reconstitute`로 하고, 복원 검증 실패는 변환하지 않고 그대로 전파합니다(저장값 손상은 프로그래밍·데이터 오류).
- **갱신**: 기존 애그리게잇의 저장은 같은 트랜잭션에서 읽은 managed Entity를 제자리에서 갱신합니다. 새 Entity를 만들어 `merge`하지 않습니다(`@Version` 추적을 잃고 자식 행을 전부 삭제·재삽입함). 추가 전용 이력(배정 이력 등)은 기존 행을 갱신하고 새 행만 추가하며, 순서가 외부 계약이면 순서 컬럼으로 저장하고 시각으로 정렬하지 않습니다.
- **조회 결과**: 출력 Port가 돌려주는 Domain 객체는 영속 상태와 분리된 사본입니다. 호출자가 `save`하지 않은 변경은 커밋돼도 저장되지 않아야 하며, 이를 실제 커밋으로 확인하는 Adapter 테스트를 둡니다.
- **생성물**: QueryDSL 생성물은 `build/generated/querydsl`에 두며 직접 수정하지 않습니다.

<a id="transactions"></a>
## 트랜잭션과 시간

- 변경 UseCase는 `@Transactional`, 조회 UseCase는 `@Transactional(readOnly = true)`를 적용합니다.
- 예외: DB를 쓰지 않고 Redis·외부 검증기 같은 다른 저장소만 쓰는 UseCase(예: auth의 nonce 발급·Access Token 인증·로그아웃)는 트랜잭션을 붙이지 않고 서비스 Javadoc에 그 사실을 적습니다. JPA 트랜잭션은 시작할 때 DB 커넥션을 잡으므로, 매 요청 토큰 인증처럼 DB가 필요 없는 경로가 커넥션을 점유하거나 DB 장애에 함께 실패하지 않게 하기 위해서입니다. DB 접근이 추가되면 위 규칙을 적용합니다.
- 날짜·시각은 UTC `Instant`, 테스트 가능한 시간은 주입받은 `Clock`을 사용합니다. 주입되는 `Clock`은 저장소(PostgreSQL) 정밀도인 마이크로초 단위로 끊으므로, 시각 컬럼도 마이크로초 정밀도로 매핑합니다. JPA 감사 컬럼(`BaseTimeEntity`)도 같은 `Clock`에서 시각을 받습니다(`shared.internal.config.JpaAuditingConfig`).

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
- 운영은 `SPRING_PROFILES_ACTIVE=prod`로 실행합니다. `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`는 기본값 없이 받아 누락 시 시작에 실패하게 합니다. `KAKAO_ALLOWED_AUDIENCES`, `AUTH_JWT_SECRET`은 모든 프로필에서 기본값 없이 받습니다([Auth 설정](../../../../docs/domain/auth.md#설정)).
- `prod,local` 동시 지정에도 로컬 설정이 활성화되지 않도록 프로필 표현식으로 보호합니다. 운영 OpenAPI UI는 비활성화합니다.
- 설정은 `application-{profile}.yml`로 분리하고 공통 설정을 중복하지 않습니다.
- 현재 마이그레이션 도구는 없습니다. 운영 배포 전에 마이그레이션 전략을 ADR로 결정합니다.

실행 명령: [빠른 시작](../../../../README.md#빠른-시작). 참고: [영속성 결정](../../../../docs/adr/001-backend-architecture.md#영속성과-이벤트).
