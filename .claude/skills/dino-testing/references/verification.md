# 검사 선택·완료 검증·근거

필요한 절만 읽습니다.

<a id="selection"></a>
## 집중 검사 선택

아래 표와 대상 모듈 `AGENTS.md`에서 변경 범위에 맞는 테스트를 선택합니다.

DB·컨테이너 검사는 전용 자원도 삭제할 수 있어 [DB 승인](database-approvals.md#database)을 따릅니다.

| 변경 | 테스트 클래스 |
| --- | --- |
| 모듈 경계·공개 계약 | `com.orbit.ModularityTest` |
| 내부 의존성·JPA Entity 위치 | `com.orbit.ArchitectureTest` |
| 등록 트랜잭션·이벤트·비동기 설정 | `com.orbit.UserRegistrationEventIntegrationTest` |
| HTTP·응답·예외·보안 | `com.orbit.ApiWorkflowIntegrationTest` |
| Bean 등록·생성자 주입·공통 Security·설정과 소비 모듈 의존성 | 영향받는 `com.orbit.user.UserModuleTest`, `com.orbit.auth.AuthModuleTest`와 위 API 검사 |
| OpenAPI 계약 | `com.orbit.OpenApiDocumentationIntegrationTest` |

- `@Service`·`@Bean` 추가, 생성자 주입·프로필·공통 설정 변경은 해당 모듈과 영향받는 소비 모듈 조립 검사를 같은 커밋 단위에 포함합니다. mock 주입 서비스·전체 API·정적 의존 검사만으로 실제 Bean 존재·생성 가능성을 보증하지 않습니다.
- 새 등록 진입점도 기존 이벤트·트랜잭션 계약을 검사합니다. 환경값·스키마 변경은 `.env.example`·실행 안내·운영 적용 자산·복구 제약을 대조합니다. 테스트 프로필·공통 fixture 변경은 기존 격리 설정 삭제의 이유·영향을 확인합니다.
- [ModularityTest](../../../../src/test/java/com/orbit/ModularityTest.java)의 `ApplicationModules.verify()`는 순환·허용 의존성·내부 패키지 침범을 CI에서 차단합니다.
- [ArchitectureTest](../../../../src/test/java/com/orbit/ArchitectureTest.java)는 Domain의 Application·Adapter·Spring·Jakarta·Hibernate·QueryDSL 의존, Application의 Adapter·영속 기술 의존, 입력 Adapter의 출력 Port·Persistence·`@Service` 구현 의존과 JPA Entity 위치를 검사합니다.
- 새 아키텍처 규칙은 의도적 위반이 실제로 실패하는지 확인한 뒤 위반 코드를 제거합니다.

<a id="completion"></a>
## 완료 검증과 CI

완료 명령·문서-only 예외는 [루트 검증 기준](../../../../AGENTS.md#검증과-완료)을 따릅니다.

- 각 PR의 변경·알려진 수정을 마친 최종 상태는 전체 검증을 통과해야 합니다. 중간 커밋에는 [단위별 집중 검사·계약 검토](../../dino-commit/references/commits.md#checkpoints)가 필요합니다. 완료 검증에 `--tests` 필터를 쓰지 않습니다.
- 현재 [CI](../../../../.github/workflows/ci.yml)는 PR 최종 병합 결과를 검사하며 내부 커밋을 순회하지 않습니다. 커밋별 조립·실행 가능성은 해당 상태의 검사 근거로 확인합니다. `verify` 뒤의 [자동 리뷰](../../dino-review/references/automation.md#automation) 잡은 결과를 인용해 게시하며 병합을 막지 않고, 제목·라벨·양식·문서 링크는 별도 [PR 규약 검사](../../../../.github/workflows/pr-conventions.yml)가 판정합니다. [GitHub PR 이벤트](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#pull_request)
- 로컬 `./gradlew test`는 Docker가 없으면 컨테이너 테스트를 건너뜁니다. 전체 실행은 `./gradlew test -PrequireAllTests=true`를 사용합니다.
- `CI=true` 또는 `-PrequireAllTests=true`는 0건 실행·건너뛴 테스트가 있으면 실패합니다. `-PrequireAllTests=false`로 CI 정책을 해제할 수 없습니다. 이 옵션은 `--tests` 필터를 감지하지 않으므로 전체 실행 여부는 실제 명령도 확인합니다.
- Docker 부재로 건너뛴 검사가 있으면 전체 통과가 아닙니다. 성공·실패·건너뛰기·필터 여부와 검사 대상 상태를 구분합니다.
- CI는 Docker를 먼저 확인하고 성공 여부와 관계없이 테스트·Checkstyle·JaCoCo 보고서를 `verification-reports` artifact로 14일간 보관합니다.
- [DB 승인](database-approvals.md#database)은 에이전트의 로컬 검사에 적용하며 기존 CI 실행 조건·테스트 정리 동작은 변경하지 않습니다. 푸시 등으로 CI를 유발하면 승인 요약에 CI의 DB 테스트·자원 정리도 포함합니다.

<a id="evidence"></a>
## 검사 근거와 리뷰

검사 통과는 해당 명령·옵션·대상 상태·설정·환경과 검출 범위 안에서만 근거가 됩니다. 테스트·assertion·검사 규칙 변경의 타당성, 요구사항 충족·계약 의미·누락 시나리오는 별도 검토가 필요합니다.

| 대상 | 검사·설정 | 남는 판단 |
| --- | --- | --- |
| 포맷·import·명명 형태 | [Spotless](../../../../build.gradle.kts), [Checkstyle 설정](../../../../gradle/quality.gradle.kts)·[규칙](../../../../config/checkstyle/checkstyle.xml) | `get`/`find` 의미·DTO 소유권은 [컨벤션](../../dino-architecture/references/style.md#style)과 리뷰로 확인 |
| 모듈·계층 경계 | [집중 검사](#selection)의 `ModularityTest`, `ArchitectureTest` | 비즈니스 책임·공개 정보의 적절성, 새 규칙의 검사 필요성 |
| HTTP·OpenAPI·이벤트 | [집중 검사](#selection)의 통합 테스트 | 작성한 시나리오만 검증하며 새 API·정책을 자동 보장하지 않음 |
| 프로필 | [ApplicationProfileConfigurationTest](../../../../src/test/java/com/orbit/shared/internal/config/ApplicationProfileConfigurationTest.java) | 실제 배포 환경의 인증·접근·마이그레이션 정책 |
| 전체 테스트·보고서 | [테스트 설정](../../../../gradle/testing.gradle.kts), [CI](../../../../.github/workflows/ci.yml) | 필터 없는 실행 여부; JaCoCo는 보고서 생성이며 최소 커버리지 게이트는 없음 |
| Markdown·ADR·PR 작성 | [문서 검증](../../../../AGENTS.md#검증과-완료), [ADR 관리](../../../../docs/agents/documents/maintenance.md#maintenance), [PR 규약 검사](../../../../.github/workflows/pr-conventions.yml)의 제목·라벨·양식·변경 문서 링크/앵커 | ADR 상태·크기 적정성·커밋별 완결성·검증 절의 실제성은 리뷰로 확인 |
| PR 리뷰 | [자동 리뷰](../../dino-review/references/automation.md#automation)가 verify·규약 검사 결과를 인용해 비차단 게시 | 결함·의미 판단의 채택과 병합 여부는 사람 |

선택 배경: [포맷과 검증 결정](../../../../docs/adr/001-backend-architecture.md#포맷과-검증).
