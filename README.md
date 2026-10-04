# Spring Modulith Backend Template

새 백엔드 프로젝트의 출발점으로 사용하는 단일 JAR 모듈러 모놀리스 템플릿입니다. `user` 예제와 `auth`의 카카오·Apple OIDC 로그인으로 DDD 모듈 경계, 내부 Hexagonal Architecture, 공개 API와 비동기 이벤트 연동을 보여줍니다.

## 기술 스택

Java 21 · Spring Boot · Spring Modulith · Gradle Kotlin DSL. 정확한 버전은 [버전 카탈로그](gradle/libs.versions.toml)와 [빌드 설정](build.gradle.kts)에서 관리합니다.
Spring MVC/Validation/Security/Actuator/OpenAPI, Spring Data JPA/QueryDSL/PostgreSQL, Spring Data Redis를 사용합니다.
테스트는 JUnit Jupiter(Boot BOM 관리), Mockito, Modulith Test, Testcontainers입니다.

## 빠른 시작

JDK 21과 Docker/Compose가 필요합니다. 별도 Gradle 설치 없이 Wrapper를 사용합니다.

에이전트가 아래 앱을 실행할 때는 `local`의 `create-drop`에 따른 DB 생성·삭제 범위를 먼저 확인받습니다. [승인 절차](AGENTS.md#approvals)를 따릅니다.

```bash
cp .env.example .env
docker compose --env-file .env up -d postgres redis
set -a && source .env && set +a
./gradlew bootRun
```

활성 프로필은 자동 선택하지 않습니다. 위 명령은 `.env`의 `SPRING_PROFILES_ACTIVE=local`을 적용합니다. 로컬/테스트는 `create-drop`이므로 보존할 데이터를 넣지 마세요. 카카오 로그인을 실제로 쓰려면 `.env`의 `KAKAO_ALLOWED_AUDIENCES`를 카카오 개발자 콘솔의 앱 키로, `AUTH_JWT_SECRET`을 무작위 값으로 바꿉니다. Apple 로그인은 `APPLE_*` 값(클라이언트 ID 목록, Team ID·키 ID·`.p8` 개인키, refresh token 암호화 키, Services ID·콜백·복귀 주소)을 Apple 개발자 콘솔 값으로 바꿉니다. 자리표시자로도 기동은 되지만 Apple 토큰 교환은 실패합니다([Auth 설정](docs/domain/auth.md#설정)).

기본 포트: API `8080`, Actuator `9090`.

- OpenAPI UI: `http://localhost:8080/docs`
- Health: `http://localhost:9090/actuator/health`
- Prometheus: `http://localhost:9090/actuator/prometheus`

예제 API·카카오·Apple 로그인 경로·Health는 공개이고 그 밖의 API와 Prometheus·Info는 Bearer Access Token이 필요합니다. 실행 전 [Auth의 인증 지원 범위](docs/domain/auth.md#책임과-범위)를 확인하고 운영 수집기의 인증·접근 정책을 구성해야 합니다.

## 예제 API

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"displayName":"홍길동"}'

curl http://localhost:8080/api/v1/auth/examples/subjects/1

# 카카오 SDK 로그인 직전 nonce 발급 → id_token 제출 → Bearer 사용
curl -X POST http://localhost:8080/api/v1/auth/kakao/nonces
curl -X POST http://localhost:8080/api/v1/auth/kakao/login \
  -H 'Content-Type: application/json' \
  -d '{"idToken":"<카카오 id_token>"}'
curl http://localhost:8080/api/v1/auth/me -H 'Authorization: Bearer <accessToken>'

# Apple iOS: raw nonce 발급 → 앱이 SHA-256 hex를 Apple 요청에 넣음 → id_token·authorization code 제출
curl -X POST http://localhost:8080/api/v1/auth/apple/nonces
curl -X POST http://localhost:8080/api/v1/auth/apple/login \
  -H 'Content-Type: application/json' \
  -d '{"idToken":"<Apple id_token>","authorizationCode":"<Apple authorization code>"}'

# Apple 웹·Android: 브라우저로 시작 → 콜백 뒤 복귀 주소의 code를 교환
open 'http://localhost:8080/api/v1/auth/apple/authorize?client=web'
curl -X POST http://localhost:8080/api/v1/auth/apple/exchange \
  -H 'Content-Type: application/json' \
  -d '{"code":"<복귀 주소의 code>"}'
```

## 아키텍처

```text
src/main/java/com/orbit/     애플리케이션과 모듈
src/test/                   단위·모듈·API·아키텍처 테스트
gradle/                     버전·의존성·품질·테스트 설정과 Wrapper
config/checkstyle/          코드 검사 규칙
docker/                    실행 JAR용 컨테이너 이미지
.github/                   CI·이슈/PR 양식·라벨 정의·기여 안내
.claude/skills/            팀 공통 Claude 스킬과 공통 개발 계약(references/)
.claude/agents/            모듈 경계 독립 검토 서브에이전트
.claude/settings.json      팀 공통 권한·커밋 제목 hook
docs/
├── adr/                    아키텍처·에이전트 규칙 결정 이력
├── domain/                 모듈 책임과 공개 계약
├── planning/               기획 원본(노션 정책서·기능명세) 색인·기준 버전
├── troubleshooting/        반복 조사에서 얻은 문제 해결 사례
└── maintenance.md          문서·ADR 원본 위치와 관리 규칙
```

모듈별 소유권·공개 계약·구현 범위는 [도메인 지도](docs/domain/README.md), 의존 규칙은 [아키텍처](.claude/skills/dino-architecture/references/architecture.md#modules)가 원본입니다. 운영 적용 준비는 [프로필·마이그레이션](.claude/skills/dino-architecture/references/persistence.md#profiles), 이벤트 경계는 [트랜잭션 이벤트](.claude/skills/dino-architecture/references/persistence.md#events)를 확인합니다.

## 검증

[전체 검증 명령](AGENTS.md#검증과-완료)을 사용합니다. 전체 테스트에는 Docker가 필요하며 일부 건너뛰기를 통과로 보지 않습니다. [집중 검사](.claude/skills/dino-testing/references/verification.md#selection)와 [CI 보고서 정책](.claude/skills/dino-testing/references/verification.md#completion)은 해당 절에서 확인합니다. PR에는 CI `verify`와 [PR 규약 검사](.github/workflows/pr-conventions.yml)가 실행됩니다. 리뷰는 PR 전에 로컬 [`dino-review`](.claude/skills/dino-review/references/procedure.md#procedure)로 하고, 규약 검사는 본문의 [리뷰 마커](.claude/skills/dino-review/references/report.md#marker)가 PR head와 같은지 확인합니다.

## 문서 안내

- 작업별 규칙·원본·검증 경로: [컨벤션 목차](AGENTS.md#읽기-경로)
- API 스키마: [OpenAPI](.claude/skills/dino-architecture/references/web.md#controllers)
- AI 작업 방식: [AGENTS.md](AGENTS.md) (Claude Code 스킬 목록은 [스킬과 위임](AGENTS.md#delegation))
- 커밋·브랜치·승인·이슈·라벨·PR과 AI 리뷰: [GitHub 작업 가이드](AGENTS.md#읽기-경로)
- 구조 선택의 이유와 제약: [현재 ADR 요약](docs/adr/README.md)에서 유효한 결정 확인
- 제품 정책·기능 흐름: [기획 원본 색인](docs/planning/README.md#planning)에서 노션 정책서·기능명세와 기준 버전 확인. 레포에 복사하지 않음
