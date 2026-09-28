<a id="evidence"></a>
# 검사 근거와 리뷰

검사 통과는 해당 명령·옵션·대상 상태·설정·환경과 검출 범위 안에서만 근거가 됩니다. 테스트·assertion·검사 규칙 변경의 타당성, 요구사항 충족·계약 의미·누락 시나리오는 별도 검토가 필요합니다.

| 대상 | 검사·설정 | 남는 판단 |
| --- | --- | --- |
| 포맷·import·명명 형태 | [Spotless](../../../build.gradle.kts), [Checkstyle 설정](../../../gradle/quality.gradle.kts)·[규칙](../../../config/checkstyle/checkstyle.xml) | `get`/`find` 의미·DTO 소유권은 [컨벤션](../java/style.md#style)과 리뷰로 확인 |
| 모듈·계층 경계 | [집중 검사](selection.md#selection)의 `ModularityTest`, `ArchitectureTest` | 비즈니스 책임·공개 정보의 적절성, 새 규칙의 검사 필요성 |
| HTTP·OpenAPI·이벤트 | [집중 검사](selection.md#selection)의 통합 테스트 | 작성한 시나리오만 검증하며 새 API·정책을 자동 보장하지 않음 |
| 프로필 | [ApplicationProfileConfigurationTest](../../../src/test/java/com/orbit/shared/internal/config/ApplicationProfileConfigurationTest.java) | 실제 배포 환경의 인증·접근·마이그레이션 정책 |
| 전체 테스트·보고서 | [테스트 설정](../../../gradle/testing.gradle.kts), [CI](../../../.github/workflows/ci.yml) | 필터 없는 실행 여부; JaCoCo는 보고서 생성이며 최소 커버리지 게이트는 없음 |
| Markdown·ADR·PR 작성 | [문서 검증](../../../AGENTS.md#검증과-완료), [ADR 관리](../../agents/documents/maintenance.md#maintenance), [PR 규약 검사](../../../.github/workflows/pr-conventions.yml)의 제목·라벨·양식·변경 문서 링크/앵커 | ADR 상태·크기 적정성·커밋별 완결성·검증 절의 실제성은 리뷰로 확인 |
| PR 리뷰 | [자동 리뷰](../workflow/review/automation.md#automation)가 verify·규약 검사 결과를 인용해 비차단 게시 | 결함·의미 판단의 채택과 병합 여부는 사람 |

선택 배경: [포맷과 검증 결정](../../adr/001-backend-architecture.md#포맷과-검증).
