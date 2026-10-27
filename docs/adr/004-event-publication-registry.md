# ADR-004: 이벤트 발행 기록과 실패 발행 재전송

- 상태: Accepted
- 기준일: 2026-10-27
- 범위: 모듈 간 이벤트의 발행 기록·완료 보관·실패 재전송·멈춘 발행 처리·감시와, 그 때문에 생기는 리스너·이벤트 계약 규칙. 실행 규칙은 [트랜잭션 이벤트](../../.claude/skills/dino-architecture/references/persistence.md#events)가 소유합니다. 알림 생성·발신 대기열은 범위 밖입니다.

## 배경과 목적

모듈 간 이벤트는 변경 트랜잭션에서 발행하고 커밋 후 비동기 리스너로 소비합니다([ADR-001](001-backend-architecture.md#영속성과-이벤트)). 리스너가 실패하거나 처리 중에 인스턴스가 멈추면 이벤트가 그대로 사라졌고 다시 보낼 수단이 없었습니다. 알림은 다른 모듈의 이벤트를 받아 만들어지는데, [알림] 기능명세 v1.2 §10과 정책 v1.3 §1은 모든 트리거가 수신자에게 도달해야 한다고 정합니다. 메시지 브로커를 두지 않고 PostgreSQL 안에서 이 보장을 만듭니다(HM-189 설계).

## 결정

- **Spring Modulith의 JDBC 발행 기록을 씁니다.** 이벤트를 발행한 트랜잭션 안에서 리스너마다 `event_publication`에 행을 남기므로, 커밋하면 기록도 남고 롤백하면 둘 다 없습니다. 이미 쓰는 `@ApplicationModuleListener`와 맞물리고 실패 발행 재전송 API가 있어 아웃박스 테이블과 폴러를 직접 만들지 않습니다.
- **모듈 리스너(`@ApplicationModuleListener`)로 가는 이벤트만 기록합니다**(`spring.modulith.events.registry-trigger-annotation`). Modulith 기본값은 커밋 후 리스너 전부를 기록합니다. 다시 보낼 의미가 있는 것은 별도 트랜잭션에서 비동기로 도는 모듈 리스너입니다. 모듈 리스너가 없는 이벤트는 기록되지 않으므로 이 테이블은 이벤트 저장소가 아닙니다.
- **처리를 마친 발행은 보관 테이블(`event_publication_archive`)로 옮기고 기한 없이 둡니다**(`completion-mode: archive`). 이벤트 원문은 각 모듈 테이블에 남지 않아서(예: `UserRegistered`는 로그만 남김) 지우면 무엇을 발행했는지 사라집니다. 기본값인 삭제와 같은 테이블에 완료 표시만 하는 방식을 검토했습니다. 정리 작업을 두지 않는 대신 이벤트에 개인정보를 넣지 않습니다.
- **실패한 발행만 1분마다 다시 보내고, 최초 처리를 포함해 최대 10회까지 보냅니다**(`app.events.resubmission.*`, `shared.internal.event.FailedEventPublicationResubmitter`). Modulith는 실패 발행을 오래된 순으로 읽어서, 읽는 개수를 제한하면 상한에 닿은 옛 발행이 앞자리를 채워 새 실패가 재전송되지 못합니다. 그래서 개수 제한 없이 읽고 시도 횟수로 거릅니다. HM-278 설계 DG-03의 "1분 지난 미완료 발행 전체 재제출"은 아직 처리 중인 리스너를 한 번 더 돌리고 횟수 상한이 없어 택하지 않았습니다.
- **멈춘 발행은 5분 뒤 실패로 바꿔 재전송 대상에 넣습니다**(`spring.modulith.events.staleness.*`, 1분마다 검사). 인스턴스 중단 등으로 대기·처리·재전송 중(PUBLISHED·PROCESSING·RESUBMITTED)에 머문 발행을 구합니다. 재기동 때 미완료 발행을 한꺼번에 다시 보내는 옵션(`republish-outstanding-events-on-restart`)은 여러 인스턴스가 같은 발행을 겹쳐 보내므로 끕니다.
- **같은 이벤트가 한 리스너에 두 번 이상 갈 수 있음을 받아들이고, 모듈 리스너를 멱등하게 만듭니다.** Modulith 2.1.1의 멈춤 판단은 최초 발행 시각 기준이라, 최초 발행에서 5분이 지난 발행은 재전송 직후 처리 중이어도 다음 검사에서 다시 실패로 바뀌어 한 번 더 실행될 수 있습니다. 여러 인스턴스가 함께 돌 때 재전송 표시(`STATUS != 'RESUBMITTED'` 조건부 갱신)는 같은 발행을 동시에 집는 일만 줄이고, 처리가 시작되거나 다시 실패한 뒤에는 앞서 목록을 읽은 다른 인스턴스가 또 보낼 수 있습니다. 커밋 `943e77e` 본문의 "한 인스턴스에만 넘긴다"는 이 보장보다 강한 표현입니다. 멈춤 기준을 30분으로 늘리는 안은 중단된 인스턴스의 발행 복구까지 늦추면서 중복을 없애지 못하고, 마지막 재전송 시각 기준으로 직접 검사하는 안은 Modulith 내부 테이블을 직접 다뤄 버전을 올릴 때 깨지기 쉽습니다. 재전송 방식에서 중복의 근본 대책은 멱등이므로 5분을 유지합니다.
- **시도를 다 쓴 실패 발행은 지우지 않고 지표로 감시합니다.** `/actuator/prometheus`에 `event_publications_failed`(실패로 남은 발행 수)와 `event_publications_exhausted`(그중 최대 처리 시도 횟수를 다 써 더 보내지 않는 발행 수)를 내보냅니다. 지우거나 따로 옮기면 처리 완료를 보관하는 이유(이벤트 내용 보존)와 어긋납니다. Modulith의 기록 저장소 인터페이스는 컴파일 경로에 없어서 의존성을 늘리지 않고 기록 테이블을 COUNT 쿼리로 셉니다. 모든 인스턴스가 같은 테이블을 세므로 인스턴스끼리 더하지 않고 최댓값으로 봅니다.
- **멈춤 검사 로거(`DefaultEventPublicationRegistry`)는 WARN으로 올립니다.** 멈춘 발행이 없어도 상태마다 INFO를 남겨 인스턴스마다 1분에 3줄이 쌓이기 때문입니다. 리스너 실패는 비동기 실행기가 ERROR로 남겨 그대로 보입니다. 같은 로거의 재전송 중 오류(INFO)는 함께 가려지지만, 그런 발행은 재전송마다 실패해 결국 `event_publications_exhausted`에 잡힙니다.
- **이벤트 record는 기록 행의 계약입니다.** 기록 행은 이벤트 타입 이름과 JSON 원문을 저장하므로 이벤트에는 식별자·상태·시각만 담고 이름·연락처 같은 개인정보는 넣지 않습니다. Modulith 2.1.1에서 이벤트 타입의 이름·패키지를 바꾸면 남은 행은 타입을 찾지 못해 재전송에서 빠진 채 기록 테이블에 남고 읽을 때마다 WARN 로그가 납니다. 필드 구조를 바꿔 역직렬화가 실패하면 그 발행만 재전송마다 실패해 시도 상한까지 소진되고 다른 발행의 재전송은 막지 않습니다. 그래서 바꾸기 전에 미완료 발행을 확인하거나 이전 타입을 남깁니다.
- **스케줄링은 `shared.internal.config.SchedulingConfig`가 직접 켭니다.** 지금은 spring-modulith-moments 자동 설정이 `@EnableScheduling`을 대신 켜고 있어서, 그 의존성이 빠지면 재전송과 멈춤 검사가 조용히 멈춥니다.

## 영향과 검증

- **운영 스키마:** 기록 테이블은 다른 테이블과 같이 Flyway 마이그레이션(`V1__init.sql`, [ADR-005](005-schema-migration.md))이 만들고, Modulith의 자동 생성(`spring.modulith.events.jdbc.schema-initialization.enabled`)은 모든 프로필에서 끕니다. 정의는 spring-modulith-events-jdbc 2.1.1 jar 안의 `org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql`·`schema-postgresql-archive.sql`과 같습니다. 테이블이 없으면 모듈 리스너로 가는 이벤트를 발행하는 업무 트랜잭션이 실패할 것으로 봅니다(운영 DB로 확인하지 않음). Modulith 버전을 올릴 때 스키마 버전이 바뀌었으면 새 버전 마이그레이션으로 따라갑니다.
- **수동 처리:** 시도를 다 쓴 발행은 원인을 고친 뒤 그 행의 `completion_attempts`를 상한보다 낮추면 다음 재전송 주기에 다시 보내집니다. 타입을 찾지 못해 남은 행은 이전 타입을 되살려 배포하거나 내용을 확인한 뒤 지웁니다.
- **커밋 후 리스너 전체:** Modulith는 기록 대상과 무관하게 모든 커밋 후 리스너(`@TransactionalEventListener`)를 감싸 실행 전에 별도 트랜잭션으로 발행 상태를 갱신하려 합니다. 기록하지 않는 리스너도 DB 연결을 쓰고, 그 갱신이 실패하면 리스너가 돌지 않습니다.
- **검증:** `EventPublicationRegistryIntegrationTest`(Testcontainers PostgreSQL)가 기록·보관·롤백·실패·재전송·시도 상한·멈춘 발행·지표를, `FailedEventPublicationResubmitSchedulerTest`가 주기 작업의 예약 실행을, `ApplicationProfileConfigurationTest`가 모든 프로필에서 Modulith 스키마 초기화가 꺼져 있는지와 재전송 활성화·로그 수준을 확인합니다. 테스트의 기록 테이블은 V1이 만듭니다. 여러 인스턴스 동시 실행, 운영 DB의 마이그레이션 적용, 실제 Prometheus 수집은 실행으로 확인하지 않았습니다.
- **남은 일:** 같은 이벤트를 두 번 받았을 때 알림이 중복되지 않게 하는 소비 측 멱등 키(HM-277, DG-04)와 발신 대기열(HM-280)은 알림 구현에서 다룹니다.
