# ADR-004: 스키마 마이그레이션

- 상태: Accepted
- 기준일: 2026-10-18
- 범위: DB 스키마의 원본과 변경 방식, 마이그레이션 실행 시점, 파일·호환성 규칙. JPA 매핑 규칙은 [영속성](../../.claude/skills/dino-architecture/references/persistence.md#jpa), 배포 인프라는 `dino-product/infra` 레포가 소유합니다.

## 배경과 목적

local·test는 Hibernate `create-drop`으로 스키마를 만들고, 공통·`prod`는 `ddl-auto: none`이라 운영 스키마를 만들 수단이 없었습니다. JPA로 표현할 수 없는 부분 유일 인덱스는 `create-drop`에서만 도는 별도 SQL에 있어 운영 DDL에 따로 옮겨야 했습니다. dev 배포(RDS `orbit_dev`, 앱 기동 시 스키마 준비)를 시작하기 전에 스키마 변경을 코드로 남기고 dev와 prod가 같은 순서로 적용하게 합니다.

## 결정

- **도구와 원본:** Flyway를 씁니다. 스키마의 원본은 `src/main/resources/db/migration`의 SQL 파일이고, Entity는 그 스키마에 대한 매핑입니다. 모든 프로필의 `ddl-auto`는 `validate`라 Hibernate는 스키마를 바꾸지 않고 매핑과 스키마가 다르면 기동에 실패합니다. 부분 인덱스처럼 PostgreSQL 기능을 그대로 쓰려고 SQL 마이그레이션을 고르고, DB 독립 추상화(Liquibase의 XML·YAML)는 PostgreSQL만 쓰는 지금 이득이 없어 고르지 않았습니다.
- **실행 시점:** 앱이 기동할 때 Flyway가 미적용 버전을 적용합니다. 여러 인스턴스가 동시에 떠도 Flyway가 PostgreSQL advisory lock으로 직렬화합니다. 레플리카가 늘거나 마이그레이션이 길어 기동 probe를 넘기면 별도 실행(배포 전 Job)과 DDL 전용 DB 역할 분리를 다시 검토합니다.
- **파일 규칙:** 이름은 `V{정수}__{설명}.sql`이고 정수는 1씩 늘립니다. develop에 머지된 파일은 고치지 않습니다(dev DB가 이미 적용했고 Flyway가 체크섬 불일치로 기동을 막음). 고칠 것은 새 버전으로 추가합니다. 스키마 변경은 그 스키마를 쓰는 Entity 변경과 같은 PR에 넣습니다. 다른 PR이 같은 번호를 먼저 머지하면 develop 최신에 맞춰 번호를 올립니다. develop 머지가 dev 자동 배포로 이어지면 PR CI에 "새 버전은 base 브랜치의 최신 버전보다 크다" 검사를 추가합니다.
- **스키마 표기:** 제약·인덱스는 `pk_`·`uk_`·`fk_`·`ix_` 접두어로 이름을 붙입니다. 코드가 위반한 제약 이름으로 오류를 판정하는 곳이 있어(예: 유형 이름 부분 유일 인덱스) 이름이 바뀌지 않아야 합니다. enum 컬럼에는 CHECK 제약을 두지 않습니다. 값 검증의 원본은 Domain이고, 값 추가마다 마이그레이션이 필요해지는 것을 피합니다. 모듈 간 참조에 FK를 두지 않는 규칙은 [ADR-001](001-backend-architecture.md#영속성과-이벤트)을 따릅니다.
- **호환성:** 롤링 배포 중에는 이전 버전 앱이 새 스키마에서 돌고, prod에는 dev에서 이미 마이그레이션을 거친 같은 이미지를 올립니다. 그래서 각 마이그레이션은 직전 버전 앱과 호환돼야 합니다. 컬럼 삭제·이름 변경·타입 축소는 추가 → 코드 전환 → 다음 릴리스에서 삭제로 나눕니다(expand-contract). Flyway undo는 쓰지 않고 앞으로 고치는 마이그레이션을 추가합니다. 되돌릴 수 없는 변경을 prod에 올리기 전에는 DB 스냅샷을 뜹니다.
- **초기 상태:** `V1__init.sql`은 도입 시점의 Entity 매핑과 같은 스키마에 부분 유일 인덱스를 더한 것입니다. 2026-10-18 기준 데이터가 있는 dev·prod DB가 없어 baseline 없이 V1부터 적용합니다.

## 영향과 검증

- Testcontainers 테스트는 컨텍스트마다 빈 PostgreSQL에 V1부터 적용한 뒤 Hibernate validate를 거치므로, 마이그레이션과 매핑의 불일치(테이블·컬럼·타입·nullable)를 CI에서 잡습니다. validate는 인덱스·제약·기본값을 비교하지 않아 이것은 리뷰로 확인합니다.
- 로컬 DB도 마이그레이션으로 만들어지고 재시작해도 데이터가 남습니다. 처음부터 다시 만들려면 Compose 볼륨을 지웁니다([빠른 시작](../../README.md#빠른-시작)). 이전 `create-drop`이 남긴 테이블이 있으면 Flyway가 기록 없는 스키마로 보고 기동을 막습니다.
- 테스트 클래스패스에만 있는 Entity의 테이블은 `src/test/resources/db/test-migration`의 반복 마이그레이션에 두고 test 프로필만 읽습니다.
- Entity의 `@Table`·`@Index`·`@UniqueConstraint` 선언은 스키마를 만들지 않으므로, 인덱스·제약을 바꿀 때는 마이그레이션이 기준입니다.
