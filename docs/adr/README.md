# 현재 프로젝트 결정

| ADR | 주제와 범위 |
| --- | --- |
| [ADR-001: 백엔드 아키텍처](001-backend-architecture.md) | 배포·모듈 경계, 카카오·Apple OIDC 인증·계정·토큰 폐기·Apple 토큰 보관, 가입 프로필(`profile`)·가입 공통 검사, 탈퇴(profile → auth 계정 폐쇄, 외부 연결 해제의 전달 보장), Application·DTO, 모듈 간 통신(요구 인터페이스 포함, 탈퇴 차단 판정·뒤처리 이벤트), user·auth 예제, HTTP·오류, 영속성·이벤트, 포맷·검증 |
| [ADR-002: 에이전틱 코딩 규칙](002-agentic-coding-rules.md) | 지침·스킬 소유권과 강제 수단, PR 종류·단위, 커밋·검증, 승인·외부 작업 |
| [ADR-003: 기획 원본과 레포 문서의 역할 분리](003-planning-source-of-truth.md) | 제품 결정(노션 정책서·기능명세)·구현 결정(레포)의 원본, 기획 참조·차이 관리·동기화, #plan 결정 요청 |
| [ADR-004: 이벤트 발행 기록과 알림 발신 보장](004-event-publication-registry.md) | 모듈 리스너 이벤트의 발행 기록·완료 보관, 횟수 제한 없는 실패 재전송·멈춘 발행 처리, 리스너 멱등·이벤트 계약, 운영 스키마, 메시지 브로커를 두지 않는 이유와 도입 기준 |
| [ADR-005: 스키마 마이그레이션](005-schema-migration.md) | 스키마 원본(Flyway SQL)·`ddl-auto: validate`, 기동 시 적용, 파일 번호·수정 금지, 제약 이름·enum CHECK 미사용, expand-contract 호환성 |
| [ADR-006: 웹 실시간 갱신(SSE와 Redis Pub/Sub)](006-web-realtime-sse.md) | 사무실 웹 변경 신호의 전송 방식(SSE, 식별자만 담고 재조회), 연결 상한·유지 신호·수명·종료, 인스턴스 사이 전달(Redis Pub/Sub) |

각 ADR은 해당 주제의 현재 결정과 선택 이유를 유지하며, 이전 내용은 Git 이력에서 확인합니다. 보완·신규 주제의 구분과 갱신 기준은 [문서와 ADR 관리](../maintenance.md#maintenance)를 따릅니다.
