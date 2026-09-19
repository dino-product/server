# 개발자 온보딩

이 템플릿을 새 프로젝트에 적용하는 개발자를 위한 안내입니다. 식별자 교체·첫 실행·기능 추가 순서를 다루며, 에이전트 작업의 상황별 읽기 경로는 [AGENTS.md](../../AGENTS.md#읽기-경로)에서 관리합니다.

## 준비물

JDK 21, Docker와 Docker Compose가 필요합니다. Gradle은 저장소 Wrapper를 사용합니다.

## 새 프로젝트로 시작

이 저장소를 새 저장소의 초기 코드로 사용한 뒤 아래 순서로 적용합니다.

1. 코드를 바꾸기 전에 [빠른 시작](../../README.md#빠른-시작)으로 등록·조회 예제가 동작하는지 확인합니다.
2. 아래 식별자를 새 프로젝트 이름과 기본 패키지로 변경합니다. main·test의 패키지 경로, package 선언, import와 애플리케이션 클래스 참조를 함께 바꿉니다.
3. [도메인 지도](../domain/README.md)에서 현재 구현 범위를 확인하고 제품 유스케이스에 맞춥니다. 모듈을 추가·제거하면 `allowedDependencies`, `ModularityTest`의 모듈 목록, 모듈별 테스트와 문서 진입점을 함께 갱신합니다. 상세 계약은 모듈 문서, 추가 작업 규칙은 하위 지침에 유지합니다.
4. `.env.example`과 실행 환경의 DB·포트·CORS 값을 맞춥니다. 실제 환경값은 `.env` 또는 배포 환경에 두며 커밋하지 않습니다.
5. [전체 검증](../../AGENTS.md)을 실행한 뒤 프로젝트의 초기 기준점으로 삼습니다. 소스의 `port/out`·`adapter/out`과 테스트도 Git에 포함되어 있어야 합니다.
6. [GitHub 초기 설정](github-setup.md)에 따라 새 저장소에 라벨을 적용하고 기본 브랜치의 이슈·PR 양식을 확인합니다.

| 식별자 | 변경 위치 |
| --- | --- |
| Gradle 프로젝트 이름 | `settings.gradle.kts`의 `rootProject.name` |
| 그룹·버전·설명 | `build.gradle.kts`의 `group`, `version`, `description` |
| 애플리케이션 이름 | `src/main/resources/application.yml`의 `spring.application.name`, `logback-spring.xml`의 이름 기본값 |
| 기본 패키지·애플리케이션 클래스 | `src/main/java/com/orbit`, `src/test/java/com/orbit`, `OrbitApplication` 참조 |
| Modulith 시스템 이름 | 애플리케이션 클래스의 `@Modulithic(systemName)` |
| 문자열로 지정한 패키지 | `ArchitectureTest`의 `importPackages`, 로깅 설정의 `com.orbit` |

패키지 경로를 옮기면 모듈·테스트 `AGENTS.md`의 상대 링크와 문서의 코드·테스트 경로도 갱신합니다. 운영 적용 전에는 인증·인가, 스키마 마이그레이션과 이벤트 전달 보장 요구를 [백엔드 ADR](../adr/001-backend-architecture.md)에 따라 결정합니다.

## 로컬 실행

[README 빠른 시작](../../README.md#빠른-시작)을 실행합니다. `.env.example`의 `SPRING_PROFILES_ACTIVE=local`을 불러와야 합니다. 로컬은 시작 시 스키마를 만들고 종료 시 제거하므로 컨테이너 볼륨에도 보존할 데이터를 넣지 마세요.

운영은 `SPRING_PROFILES_ACTIVE=prod`만 지정합니다. 필수 DB 환경변수·프로필 보호·운영 OpenAPI 제한은 [프로필 규칙](../conventions/runtime/profiles.md#profiles)을 따릅니다.

운영 배포 전 스키마 준비는 [현재 마이그레이션 상태와 규칙](../conventions/runtime/profiles.md#profiles)을 확인합니다. 인증과 후속 처리 지원 범위는 [Auth 문서](../domain/auth.md#책임과-범위)를 확인합니다.

## 첫 확인

```bash
curl http://localhost:9090/actuator/health
```

이후 [README API 예제](../../README.md#예제-api)로 등록·조회를 확인합니다. 정상 응답은 `success`, `code`, `message`, `result` 구조입니다. 등록 커밋 후 별도 스레드에 `userId`·발생 시각 로그가 남으며, API 응답 시점에 로그 처리가 끝났다고 가정하지 않습니다.

## 기능 추가 순서

1. [도메인 지도](../domain/README.md)에서 소유 모듈을 정하고 해당 모듈 지침을 읽습니다. [stacked PR](../conventions/workflow/pull-requests/planning.md#planning)의 검토 단위·의존 순서와 각 PR 안의 작은 커밋을 계획합니다.
2. Domain/Application 테스트로 기대 행동의 실패를 확인하고 내부 Port·구현을 추가합니다.
3. 다른 모듈에 필요한 최소 계약만 루트 또는 shared의 책임별 named interface로 공개합니다.
4. Adapter를 연결하고 모듈·API 통합 테스트를 추가합니다. Web 변경은 [DTO](../conventions/web/http.md#http)와 [ControllerDocs](../conventions/web/openapi/controllers.md#controllers) 규칙을 따릅니다.
5. 바뀐 책임·정책의 도메인 문서와 해당 주제 ADR의 갱신을 변경 단위에 포함합니다. 독립적인 새 주제는 [ADR 관리 규칙](../agents/documents/maintenance.md#maintenance)에 따릅니다. 단위마다 [집중 검사](../conventions/testing/design.md#design)·[계약 검토·커밋](../conventions/workflow/checkpoints.md#checkpoints)을 마치고, 각 PR의 변경과 알려진 수정을 완료한 뒤 전체 검증을 수행합니다.

## 자주 쓰는 명령

```bash
./gradlew test --tests 'com.orbit.user.*'
./gradlew test --tests com.orbit.UserRegistrationEventIntegrationTest -PrequireAllTests=true
./gradlew spotlessApply
```

전체 명령은 [루트 검증 기준](../../AGENTS.md)을 따릅니다. IDE도 [같은 Spotless 설정](../conventions/java/style.md#style)을 사용합니다. CI 보고서는 `verification-reports` artifact에서 확인합니다.

## 에이전트 문서 탐색

Codex의 지침·스킬 로딩을 확인하려면 [탐색 안내](../agents/context.md)를 참고합니다.

## Codex 협업 설정

검토 역할을 사용하거나 설정하려면 [협업 안내](../agents/collaboration.md)를 참고합니다.

### 실행 정책을 검토할 때

프로젝트 명령 규칙의 적용과 승인 모드 충돌은 [실행 정책 안내](../agents/execution-policy.md)를 참고합니다.

## 문제 해결

반복 조사로 확인된 상세 사례는 [트러블슈팅 색인](../troubleshooting/README.md)을 확인합니다.

- `All tests must run` 또는 컨테이너 테스트 건너뛰기: Docker daemon과 비활성화된 테스트를 확인합니다. 세부 [전체 실행 정책](../conventions/testing/design.md#design)을 우회하지 않습니다.
- DB 연결 실패: `.env`의 포트와 `docker compose ps`를 비교합니다.
- QueryDSL 타입 누락: `./gradlew compileJava` 후 IDE의 Gradle 모델을 다시 불러옵니다.
- 포맷 실패: `./gradlew spotlessApply` 후 `./gradlew spotlessCheck checkstyleMain checkstyleTest`. 빈 record·체인의 줄바꿈을 수동 복원하지 않습니다.
- 모듈 검증 실패: 예외를 추가하기 전에 다른 모듈 내부 패키지 import를 확인합니다.
