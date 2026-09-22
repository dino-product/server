# Spring Modulith Backend Template

새 백엔드 프로젝트의 출발점으로 사용하는 단일 JAR 모듈러 모놀리스 템플릿입니다. `user`·`auth` 예제로 DDD 모듈 경계, 내부 Hexagonal Architecture, 공개 API와 비동기 이벤트 연동을 보여줍니다.

## 기술 스택

Java 21 · Spring Boot · Spring Modulith · Gradle Kotlin DSL. 정확한 버전은 [버전 카탈로그](gradle/libs.versions.toml)와 [빌드 설정](build.gradle.kts)에서 관리합니다.
Spring MVC/Validation/Security/Actuator/OpenAPI, Spring Data JPA/QueryDSL/PostgreSQL을 사용합니다.
테스트는 JUnit Jupiter(Boot BOM 관리), Mockito, Modulith Test, Testcontainers입니다.

## 빠른 시작

JDK 21과 Docker/Compose가 필요합니다. 별도 Gradle 설치 없이 Wrapper를 사용합니다.

에이전트가 아래 앱을 실행할 때는 `local`의 `create-drop`에 따른 DB 생성·삭제 범위를 먼저 확인받습니다. [승인 절차](docs/conventions/workflow/approvals/README.md#approvals)를 따릅니다.

```bash
cp .env.example .env
docker compose --env-file .env up -d postgres
set -a && source .env && set +a
./gradlew bootRun
```

활성 프로필은 자동 선택하지 않습니다. 위 명령은 `.env`의 `SPRING_PROFILES_ACTIVE=local`을 적용합니다. 로컬/테스트는 `create-drop`이므로 보존할 데이터를 넣지 마세요.

기본 포트: API `8080`, Actuator `9090`.

- OpenAPI UI: `http://localhost:8080/docs`
- Health: `http://localhost:9090/actuator/health`
- Prometheus: `http://localhost:9090/actuator/prometheus`

예제 API와 Health는 공개이고 Prometheus·Info는 인증이 필요합니다. 실행 전 [Auth의 인증 지원 범위](docs/domain/auth.md#책임과-범위)를 확인하고 운영 수집기의 인증·접근 정책을 구성해야 합니다.

## 예제 API

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"displayName":"홍길동"}'

curl http://localhost:8080/api/v1/auth/examples/subjects/1
```

## 아키텍처

```text
src/main/java/com/orbit/     애플리케이션과 모듈
src/test/                   단위·모듈·API·아키텍처 테스트
gradle/                     버전·의존성·품질·테스트 설정과 Wrapper
config/checkstyle/          코드 검사 규칙
docker/                    실행 JAR용 컨테이너 이미지
.github/                   CI·이슈/PR 양식·라벨 정의·기여 안내
.codex/agents/             선택형 모듈·검증 검토 역할
.codex/rules/              신뢰한 프로젝트에서 로딩하는 명령 실행 규칙
docs/
├── adr/                    아키텍처·에이전트 규칙 결정 이력
├── agents/                 상황별 에이전트 탐색·협업·실행 정책 안내
├── conventions/            주제별 개발 규칙
├── domain/                 모듈 책임과 공개 계약
├── planning/               제품 유즈케이스·미결정 정책 초안
└── troubleshooting/        반복 조사에서 얻은 문제 해결 사례
```

모듈별 소유권·공개 계약·구현 범위는 [도메인 지도](docs/domain/README.md), 의존 규칙은 [아키텍처](docs/conventions/architecture/modules.md#modules)가 원본입니다. 운영 적용 준비는 [프로필·마이그레이션](docs/conventions/runtime/profiles.md#profiles), 이벤트 경계는 [트랜잭션 이벤트](docs/conventions/persistence/events.md#events)를 확인합니다.

## 검증

[전체 검증 명령](AGENTS.md#검증과-완료)을 사용합니다. 전체 테스트에는 Docker가 필요하며 일부 건너뛰기를 통과로 보지 않습니다. [집중 검사](docs/conventions/testing/selection.md#selection)와 [CI 보고서 정책](docs/conventions/testing/completion.md#completion)은 해당 절에서 확인합니다. PR에는 CI `verify`, 변경 문서의 링크·앵커를 보는 [PR conventions](.github/workflows/pr-conventions.yml), 비차단 [자동 리뷰](docs/conventions/workflow/review/automation.md#automation)가 실행됩니다.

## 문서 안내

- 작업별 규칙·원본·검증 경로: [컨벤션 목차](docs/conventions/README.md#conventions)
- API 스키마: [OpenAPI](docs/conventions/web/openapi/controllers.md#controllers)
- AI 작업 방식: [AGENTS.md](AGENTS.md)
- Codex 협업 역할·설정: [협업 안내](docs/agents/collaboration.md#codex-협업-설정)
- 커밋·브랜치·승인·이슈·라벨·PR과 AI 리뷰: [GitHub 작업 가이드](docs/conventions/workflow/README.md#workflow)
- 구조 선택의 이유와 제약: [현재 ADR 요약](docs/adr/README.md)에서 유효한 결정 확인
- 제품 유즈케이스·미결정 정책: [기획 초안](docs/planning/use-cases.md); 현재 구현·확정 정책과 구분
