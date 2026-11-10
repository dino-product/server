# Orbit Server

Orbit(A/S·설치·점검 업체의 기사-사무실 공유 스케줄링 서비스)의 백엔드입니다. 하나의 JAR로 배포하는 Spring Modulith 모듈러 모놀리스이며, 비즈니스 모듈은 DDD 바운디드 컨텍스트로 나누고 모듈 안은 헥사고널 계층으로 나눕니다([아키텍처](#아키텍처)).

- **기반**: Java 21, Spring Boot, Spring Modulith, Gradle Kotlin DSL
- **웹·보안**: Spring MVC, Validation, Security, Actuator, springdoc OpenAPI
- **데이터**: Spring Data JPA, QueryDSL, PostgreSQL, Flyway, Spring Data Redis
- **테스트**: JUnit Jupiter, Mockito, Modulith Test, Testcontainers

버전은 [버전 카탈로그](gradle/libs.versions.toml)와 [빌드 설정](build.gradle.kts)이 관리합니다.

## 빠른 시작

JDK 21과 Docker(Compose 포함)가 필요합니다. Gradle은 Wrapper를 씁니다.

```bash
cp .env.example .env
docker compose --env-file .env up -d postgres redis localstack
set -a && source .env && set +a
./gradlew bootRun
```

| 주소 | 용도 |
| --- | --- |
| `http://localhost:8080/docs` | OpenAPI UI |
| `http://localhost:9090/actuator/health` | Health. probe는 `/actuator/health/liveness`, `/actuator/health/readiness` |
| `http://localhost:9090/actuator/prometheus` | Prometheus 수집 |

- **프로필**: 자동으로 고르지 않습니다. 위 명령은 `.env`의 `SPRING_PROFILES_ACTIVE=local`을 씁니다. 프로필별 규칙은 [프로필과 실행 환경](.claude/skills/dino-architecture/references/persistence.md#profiles)에 있습니다.
- **스키마**: 기동할 때 Flyway가 `src/main/resources/db/migration`을 대상 DB에 적용합니다([ADR-005](docs/adr/005-schema-migration.md)). 에이전트가 로컬 Compose DB가 아닌 DB로 실행할 때는 [승인 절차](AGENTS.md#approvals)를 따릅니다.
- **사진 저장소**: 완료보고·현장 사진은 Compose의 LocalStack(S3 호환, `http://localhost:4566`)에 올라갑니다. 버킷 `orbit-media-local`은 컨테이너가 준비될 때 만들어집니다. 발급받은 업로드 주소로 `PUT`할 때는 응답의 `requiredHeaders`를 그대로 붙입니다([ADR-007](docs/adr/007-file-storage.md)).
- **데이터 초기화**: 로컬 DB는 재시작해도 데이터가 남습니다. 처음부터 다시 만들려면 `docker compose down -v`로 볼륨을 지우고 다시 띄웁니다. 예전 `create-drop`이 남긴 테이블 때문에 Flyway가 기동을 막을 때도 같습니다. 에이전트는 볼륨을 지우기 전에 [데이터·볼륨 삭제 승인](.claude/skills/dino-testing/references/database-approvals.md#database)을 받습니다.

### 로그인·토큰 환경값

`.env.example`의 자리표시자로도 기동은 됩니다. 아래 값은 기본값이 없고 빈 값이면 기동에 실패합니다. 줄을 지우면 `${변수명}` 문자열이 그대로 값이 되어 실패 없이 뜰 수 있으니 줄은 지우지 않습니다. 실제 키는 커밋하지 않으며, 변수별 설명은 [Auth 설정](docs/domain/auth.md#설정)과 `.env.example` 주석에 있습니다.

실제 로그인에 쓰는 값:

| 변수 | 넣을 값 | 자리표시자일 때 |
| --- | --- | --- |
| `KAKAO_ALLOWED_AUDIENCES` | 카카오 개발자 콘솔의 네이티브 앱 키와 REST API 키 | 실제 앱의 id_token을 거부 |
| `KAKAO_ADMIN_KEY` | 같은 카카오 앱의 서비스 앱 어드민 키 | 탈퇴 시 카카오 연결 끊기가 실패해 재시도하다 7일 뒤 포기 |
| `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET` | 같은 카카오 앱의 REST API 키와 그 클라이언트 시크릿. REST API 키는 현장 주소 좌표 조회(카카오 로컬 API)에도 씀 | 웹 카카오 로그인 실패, 현장 좌표 없이 저장 |
| `KAKAO_WEB_REDIRECT_URI`, `KAKAO_WEB_RETURN_URI` | 카카오 앱에 등록한 이 서버 콜백(`/api/v1/auth/kakao/callback`) 주소와 로그인 뒤 돌아갈 웹 주소 | 웹 카카오 로그인 실패 |
| `APPLE_ALLOWED_AUDIENCES` | Apple 클라이언트 목록(iOS Bundle ID, 웹 Services ID) | 실제 id_token을 거부 |
| `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY` | Team ID, Sign in with Apple 키 ID, 그 키의 `.p8` 내용 | Apple 토큰 교환 실패 |
| `APPLE_SERVICES_ID`, `APPLE_WEB_REDIRECT_URI` | 웹용 Services ID, 그리고 그 Services ID에 등록한 이 서버 콜백(`/api/v1/auth/apple/callback`)의 HTTPS 주소 | 웹 Apple 로그인 실패 |
| `APPLE_WEB_RETURN_URI` | 로그인 뒤 돌아갈 웹 주소 | `.env.example`의 로컬 주소(`http://localhost:3000/login/apple`)로 돌아감 |

- 카카오 키는 같은 카카오 앱의 것만 넣습니다. 다른 앱의 키를 섞으면 서로 다른 사용자가 한 계정으로 합쳐질 수 있습니다. Apple 클라이언트도 같은 개발자 팀의 것만 넣습니다.
- `KAKAO_REST_API_KEY`는 `KAKAO_ALLOWED_AUDIENCES`에, `APPLE_SERVICES_ID`는 `APPLE_ALLOWED_AUDIENCES`에도 넣어야 하며, 빠지면 기동에 실패합니다.
- Apple은 HTTPS 공개 도메인의 콜백만 받으므로 로컬에서 웹 Apple 로그인을 하려면 HTTPS 터널 등이 필요합니다. Android 앱은 Apple 로그인을 노출하지 않습니다.

자리표시자로도 동작하지만 저장소에 공개된 값이라 배포 환경에서는 반드시 바꿀 비밀값:

| 변수 | 넣을 값 |
| --- | --- |
| `AUTH_JWT_SECRET` | 32바이트 이상 무작위 값. 짧으면 기동 실패 |
| `APPLE_REFRESH_TOKEN_ENCRYPTION_KEY` | `openssl rand -base64 32`로 만든 값. base64 32바이트가 아니면 기동 실패 |

## 인증과 접근

- 인증 없이 부를 수 있는 경로는 카카오·Apple 로그인 경로, 토큰 재발급·로그아웃, 약관 조회(`GET /api/v1/terms`), [구조 예제](docs/adr/001-backend-architecture.md#예제-모듈) API, OpenAPI 문서(`prod`에서는 꺼짐), Health·Prometheus입니다. 그 밖의 API와 Info는 Bearer Access Token이 필요합니다.
- 로그인 호출 순서는 [카카오](docs/domain/auth.md#카카오-로그인-흐름)·[Apple](docs/domain/auth.md#apple-로그인-흐름) 로그인 흐름, 지원 범위는 [Auth 책임과 범위](docs/domain/auth.md#책임과-범위)에 있습니다.
- 가입을 마치지 않았거나 개정된 필수 약관에 다시 동의하지 않은 계정은 허용 목록(프로필·인증·회사 확인 API) 밖에서 403 `PROFILE-005`를 받습니다([가입 공통 검사](docs/domain/profile.md#profile-signup-gate)).
- Health·Prometheus는 Actuator 포트(`9090`)에만 있습니다. 운영에서는 이 포트를 외부로 라우팅하지 않고 클러스터 안의 수집기·probe만 접근하게 둡니다([접근 정책](docs/adr/001-backend-architecture.md#인증과-계정)).

## 프로젝트 구조

```text
src/main/java/com/orbit/          애플리케이션과 모듈
src/main/resources/db/migration/  Flyway 마이그레이션
src/test/                         단위·모듈·API·아키텍처 테스트
docs/adr/                         아키텍처·에이전트 규칙 결정
docs/domain/                      모듈 책임과 공개 계약
docs/planning/                    기획 원본(Notion) 색인과 기준 버전
docs/troubleshooting/             반복 조사에서 얻은 문제 해결 사례
gradle/                           버전·의존성·품질·테스트 설정과 Wrapper
config/checkstyle/                코드 검사 규칙
docker/                           실행 JAR용 컨테이너 이미지
.github/                          CI, 이슈·PR 양식, 라벨 정의, 기여 안내
.claude/                          팀 공통 스킬(개발 계약은 references/), 서브에이전트, 권한·hook
```

## 아키텍처

`com.orbit` 바로 아래 패키지 하나가 Spring Modulith 모듈 하나입니다. 비즈니스 모듈은 업무 책임(바운디드 컨텍스트)으로 나누고, `shared`는 여러 모듈이 함께 쓰는 오류·OpenAPI·보안·영속성 계약만 `@NamedInterface`로 공개하고, 설정·공통 구현은 `shared/internal`에 둡니다. 모듈 목록은 [도메인 지도](docs/domain/README.md)에 있습니다.

비즈니스 모듈 하나는 아래처럼 생겼습니다.

```text
{module}/
├── package-info.java   모듈 선언과 허용 의존성
├── *.java              다른 모듈에 공개하는 계약(인터페이스·이벤트·계약 DTO)
├── domain/             애그리게잇·값객체와 불변식
├── application/
│   ├── port/in/        유즈케이스(command: 상태 변경, query: 조회)
│   ├── port/out/       저장소·외부 연동 계약
│   ├── service/        유즈케이스 구현과 트랜잭션 조율
│   └── error/          모듈 오류 코드
└── adapter/
    ├── in/             HTTP, 이벤트·메시지 채널 구독, 주기 작업, 다른 모듈이 선언한 요구 인터페이스 구현 등
    └── out/            JPA 저장소, Redis, 외부 API, ACL로 정한 다른 모듈 호출 등
```

화살표는 의존 방향입니다. 포트와 어댑터도 `domain` 타입을 쓰지만 그림에서는 생략했습니다.

```text
adapter/in ──▶ port/in ◀── service ──▶ port/out ◀── adapter/out
                              │
                              ├──▶ domain
                              └──▶ 다른 모듈의 공개 계약
```

다른 비즈니스 모듈과는 상대 모듈 루트의 공개 계약으로만 통신합니다. 지켜야 할 규칙은 아래 원본에 있습니다.

| 원본 | 내용 |
| --- | --- |
| [모듈 경계](.claude/skills/dino-architecture/references/architecture.md#modules)·[모듈 내부 계층](.claude/skills/dino-architecture/references/architecture.md#layers) | 패키지 구조, 계층별 의존 규칙, `ModularityTest`·`ArchitectureTest`가 검사하는 범위 |
| [모듈 간 통신](.claude/skills/dino-architecture/references/communication.md#communication) | 공개 인터페이스 호출·공개 이벤트·요구 인터페이스·ACL 중 무엇을 쓸지 |
| [트랜잭션 이벤트](.claude/skills/dino-architecture/references/persistence.md#events) | 이벤트를 언제, 어떤 트랜잭션에서 소비하는지와 전달 보장 |
| [ADR-001](docs/adr/001-backend-architecture.md) | 이 구조를 고른 이유 |

## 검증

- 전체 검증 명령은 [검증과 완료](AGENTS.md#검증과-완료)에 있습니다. 전체 테스트는 Docker가 필요하며, Docker가 없어 건너뛰거나 `--tests`로 일부만 고른 실행은 전체 통과로 보지 않습니다.
- `./gradlew test`는 Testcontainers로 테스트 DB를 만들고 지우므로 에이전트는 실행 전에 [DB 승인](.claude/skills/dino-testing/references/database-approvals.md#database)을 받습니다.
- 변경에 맞는 검사만 고를 때는 [집중 검사 표](.claude/skills/dino-testing/references/verification.md#selection)를 봅니다. CI 검사 범위와 보고서 보관은 [완료 검증과 CI](.claude/skills/dino-testing/references/verification.md#completion)에 있습니다.
- PR에서는 CI `verify`와 [PR 규약 검사](.github/workflows/pr-conventions.yml)가 실행됩니다. 리뷰는 PR 전에 로컬 [`dino-review`](.claude/skills/dino-review/references/procedure.md#procedure)로 하고, 규약 검사는 본문의 [리뷰 마커](.claude/skills/dino-review/references/report.md#marker)가 PR head와 같은지 확인합니다.

## 문서

| 찾는 내용 | 문서 |
| --- | --- |
| 제품 규칙(권한·상태·입력 제한) | [기획 원본 색인](docs/planning/README.md#planning) — Notion 정책서·기능명세와 기준 버전 |
| 모듈 목록·책임·공개 계약·허용 의존성 | [도메인 지도](docs/domain/README.md) |
| 구조를 그렇게 정한 이유 | [ADR 색인](docs/adr/README.md) |
| 작업별 개발 규칙·AI 작업 방식 | [AGENTS.md 읽기 경로](AGENTS.md#읽기-경로), Claude Code 스킬은 [스킬과 위임](AGENTS.md#delegation) |
| 커밋·이슈·PR 기여 | [기여 안내](.github/CONTRIBUTING.md) |
| 문서 원본 위치와 관리 규칙 | [문서 관리](docs/maintenance.md#maintenance) |
| 반복 문제 해결 사례 | [트러블슈팅](docs/troubleshooting/README.md) |
