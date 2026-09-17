<a id="selection"></a>
# 집중 검사 선택

`./gradlew test --tests '<정확한 클래스명>'`으로 변경 범위를 먼저 검사합니다. 아래 표와 대상 모듈 `AGENTS.md`에서 관련 테스트를 선택합니다.

DB·컨테이너 검사는 전용 자원도 삭제할 수 있어 [DB 승인](../workflow/approvals/database.md#database)을 따릅니다. `test`와 간접 실행하는 `build`·`check` 모두 실제 task graph·테스트 구성을 확인합니다. 클래스 이름·`--tests`만으로 DB 삭제가 없다고 판단하지 않습니다. DB 없는 집중 검사는 추가 승인 없이 수행합니다.

| 변경 | 테스트 클래스 |
| --- | --- |
| 모듈 경계·공개 계약 | `com.example.ModularityTest` |
| 내부 의존성·JPA Entity 위치 | `com.example.ArchitectureTest` |
| 등록 트랜잭션·이벤트·비동기 설정 | `com.example.UserRegistrationEventIntegrationTest` |
| HTTP·응답·예외·보안 | `com.example.ApiWorkflowIntegrationTest` |
| Bean 등록·생성자 주입·공통 Security·설정과 소비 모듈 의존성 | 영향받는 `com.example.user.UserModuleTest`, `com.example.auth.AuthModuleTest`와 위 API 검사 |
| OpenAPI 계약 | `com.example.OpenApiDocumentationIntegrationTest` |

- `@Service`·`@Bean` 추가, 생성자 주입·프로필·공통 설정 변경은 해당 모듈과 영향받는 소비 모듈 조립 검사를 같은 커밋 단위에 포함합니다. mock 주입 서비스·전체 API·정적 의존 검사만으로 실제 Bean 존재·생성 가능성을 보증하지 않습니다.
- 새 등록 진입점도 기존 이벤트·트랜잭션 계약을 검사합니다. 환경값·스키마 변경은 `.env.example`·실행 안내·운영 적용 자산·복구 제약을 대조합니다. 테스트 프로필·공통 fixture 변경은 기존 격리 설정 삭제의 이유·영향을 확인합니다.
- [ModularityTest](../../../src/test/java/com/example/ModularityTest.java)의 `ApplicationModules.verify()`는 순환·허용 의존성·내부 패키지 침범을 CI에서 차단합니다.
- [ArchitectureTest](../../../src/test/java/com/example/ArchitectureTest.java)는 Domain의 Application·Adapter·Spring·Jakarta·Hibernate·QueryDSL 의존, Application의 Adapter·영속 기술 의존, 입력 Adapter의 출력 Port·Persistence·`@Service` 구현 의존과 JPA Entity 위치를 검사합니다.
- 새 아키텍처 규칙은 의도적 위반이 실제로 실패하는지 확인한 뒤 위반 코드를 제거합니다.
