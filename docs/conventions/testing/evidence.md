<a id="evidence"></a>
# 검사 근거와 리뷰

- 포맷·구문·경로/앵커 존재·집계 등 기계적 판정은 기존 명령이나 필요한 범위의 스크립트로 확인합니다. LLM 추정·수작업 계산을 통과 근거로 쓰지 않습니다.
- 통과 결과의 명령·옵션·대상 SHA 또는 현재 diff·관련 설정·환경이 현재 대상과 일치하면 재사용합니다. 필터·건너뛰기·검출 범위를 넘어 일반화하지 않으며 명령의 존재만으로 검증 완료로 보지 않습니다.
- 유효한 검사 결과가 판정한 동일 조건을 원문 재집계·다른 에이전트로 반복 판정하지 않습니다. 위임 시 완료한 검사 근거·미검증 범위를 전달합니다.
- LLM은 요구사항 충족·계약 의미·누락 시나리오·검사의 적절성을 검토합니다. 테스트·assertion·검사 규칙 변경은 통과 결과만으로 타당성을 보장하지 않습니다.
- 새 변경·실패·검사 범위나 관련 환경 변화·구체적인 검사 신뢰성 우려가 있으면 영향 범위를 재확인합니다. 필수 집중·전체 검사, DB 승인·커밋 직전 상태 확인은 기존 기준을 따릅니다.

| 대상 | 검사·설정 | 남는 판단 |
| --- | --- | --- |
| 포맷·import·명명 형태 | [Spotless](../../../build.gradle.kts), [Checkstyle 설정](../../../gradle/quality.gradle.kts)·[규칙](../../../config/checkstyle/checkstyle.xml) | `get`/`find` 의미·DTO 소유권은 [컨벤션](../java/style.md#style)과 리뷰로 확인 |
| 모듈·계층 경계 | [집중 검사](selection.md#selection)의 `ModularityTest`, `ArchitectureTest` | 비즈니스 책임·공개 정보의 적절성, 새 규칙의 검사 필요성 |
| HTTP·OpenAPI·이벤트 | [집중 검사](selection.md#selection)의 통합 테스트 | 작성한 시나리오만 검증하며 새 API·정책을 자동 보장하지 않음 |
| 프로필 | [ApplicationProfileConfigurationTest](../../../src/test/java/com/orbit/shared/internal/config/ApplicationProfileConfigurationTest.java) | 실제 배포 환경의 인증·접근·마이그레이션 정책 |
| 전체 테스트·보고서 | [테스트 설정](../../../gradle/testing.gradle.kts), [CI](../../../.github/workflows/ci.yml) | 필터 없는 실행 여부; JaCoCo는 보고서 생성이며 최소 커버리지 게이트는 없음 |
| Markdown·ADR·PR 작성 | [문서 검증](../../../AGENTS.md#검증과-완료), [ADR 관리](../../agents/documents/maintenance.md#maintenance), [GitHub 가이드](../workflow/README.md#workflow) | 현재 링크·ADR 상태·PR 제목/라벨·크기·커밋별 완결성을 자동 차단하는 저장소 CI는 없음 |

선택 배경: [포맷과 검증 결정](../../adr/001-backend-architecture.md#포맷과-검증).
