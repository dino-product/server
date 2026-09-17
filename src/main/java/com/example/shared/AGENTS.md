# Shared 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 아래 경로는 이 모듈 기준이며 책임·소비자는 [도메인 지도](../../../../../../docs/domain/README.md)를 확인합니다.

- 작고 안정적인 횡단 관심사만 소유합니다. 루트는 메타데이터, 공개 계약은 `error`/`openapi` named interface에 둡니다.
- 소비자는 `shared::*` 대신 실제 named interface를 `allowedDependencies`에 선언합니다.
- 설정·응답·예외 처리·보안은 `internal`, 비동기 활성화는 `internal/config/AsyncConfig`가 담당합니다.
- 한 모듈 전용이거나 사용처 없는 ErrorCode·DTO·유틸리티를 이동하지 않습니다. 공통 코드·계약 변경은 모든 영향받는 소비 모듈과 회귀 테스트를 확인합니다.

## 집중 검증

계약·응답·보안·OpenAPI·비동기 변경은 [공통 검사 표](../../../../../../docs/conventions/testing/selection.md#selection)의 해당 테스트를 실행합니다. 공통 Security·Bean 의존성 변경은 소비 모듈의 격리 조립 검사도 같은 단위에서 확인합니다. 프로필 변경은 `com.example.shared.internal.config.ApplicationProfileConfigurationTest`도 포함합니다.
