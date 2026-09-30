---
name: dino-architecture
description: src/main/java 아래 Java 코드를 작성·수정·설계할 때 항상 사용합니다. 대상은 도메인 모델·값객체, 유즈케이스 포트·서비스, 입출력 어댑터, 컨트롤러·Request/Response, JPA 엔티티·리포지토리, 이벤트·리스너, 오류 코드, OpenAPI Docs, 설정입니다. Spring Modulith 모듈 경계, 헥사고널 계층, DDD 규칙과 포맷·명명 규칙을 적용합니다.
---

# 백엔드 구조 규칙

대상 경로의 `AGENTS.md`(모듈별 추가 규칙)가 자동 로딩되면 그 내용이 우선합니다. 새 파일은 **같은 모듈의 가장 비슷한 기존 코드 하나를 먼저 읽고** 패키지·이름·예외 처리·테스트 모양을 맞춥니다.

## 항상 지킬 것

- **패키지**: `com.orbit.{module}/`
  - `domain`
  - `application/{service, port/in/{command,query}, port/out, error}`
  - `adapter/{in/web, in/event, out}`
  - 최상위에 기술 계층 패키지(`controller`, `service`, `dto`…)를 만들지 않습니다.
- **Domain**: Spring·JPA·Web 타입과 `Clock`에 의존하지 않습니다. 시각은 UTC `Instant`로 받습니다. 상태는 업무 메서드로만 바꿉니다.
- **계층**:
  - 입력 Adapter는 입력 Port만 호출합니다.
  - Service는 Domain·Port에만 의존합니다.
  - Controller는 Repository를 호출하거나 JPA Entity를 반환하지 않습니다.
  - Request는 Adapter에서 Command/Query로 변환합니다.
- **모듈 간**: 모듈 루트 공개 타입·이벤트로만 주고받습니다. 다른 모듈의 `domain`·`application`·`adapter` 접근과 Entity 공유를 금지합니다. `allowedDependencies`는 실제로 쓰는 것만 둡니다.
- **이름**: `Request`→`Command`/`Query`→`Info`→`Response`, 메서드는 `get`(없으면 예외)·`find`(`Optional`)·`list`·`exists`.
- **트랜잭션**: 변경 UseCase는 `@Transactional`, 조회는 `@Transactional(readOnly = true)`.
- **오류**: 모듈 오류는 `{module}.application.error`의 `BaseCode` enum(`{MODULE}-{번호}`)과 `BusinessException`으로 만듭니다. Domain 예외는 Application 경계에서 변환합니다.
- **노션**: 기능 규칙은 노션 명세를 따르되 코드·Javadoc·문서에 명세를 복사하지 않습니다.

## 필요한 절만 읽기

| 변경 | 원본 |
| --- | --- |
| 모듈 경계·공개 계약 | [architecture.md#modules](references/architecture.md#modules) |
| 계층·포트·DTO 위치 | [architecture.md#layers](references/architecture.md#layers) |
| 다른 모듈 정보 소비 | [architecture.md#external-models](references/architecture.md#external-models) |
| shared 변경 | [architecture.md#shared](references/architecture.md#shared) |
| 오류 코드 | [architecture.md#errors](references/architecture.md#errors) |
| JPA·트랜잭션·이벤트·프로필 | [persistence.md](references/persistence.md) |
| HTTP·Web DTO·OpenAPI | [web.md](references/web.md) |
| 포맷·명명 상세 | [style.md](references/style.md) |

## 마친 뒤

- 포맷과 검사: `./gradlew spotlessApply` → `./gradlew spotlessCheck checkstyleMain checkstyleTest` → `./gradlew compileJava compileTestJava`. 테스트는 `dino-testing` 스킬을 따릅니다.
- 모듈 루트 공개 타입·named interface·이벤트·`allowedDependencies`·Domain/Application 의존성을 바꿨다면 구현 뒤 `module-reviewer` 서브에이전트의 독립 검토가 **필수**입니다.
- 구조·정책을 바꿨다면 [도메인 지도](../../../docs/domain/README.md)와 관련 ADR을 함께 갱신합니다. 기능 추가만으로 문서를 늘리지 않습니다.
