<a id="conventions"></a>
# 개발 규칙 경로

[루트 지침](../../AGENTS.md#읽기-경로)에서 현재 작업의 파일·앵커로 직접 진입합니다. 이 목차는 탐색용이며 하위 문서 전체를 읽는 순서가 아닙니다.

| 변경 대상 | 규칙 원본 |
| --- | --- |
| Java 포맷·명명 | [스타일](java/style.md#style) |
| 모듈 / 내부 계층 / 외부 정보 / Shared / 오류 | [모듈](architecture/modules.md#modules) / [계층](architecture/layers.md#layers) / [소비 모델](architecture/external-models.md#external-models) / [Shared](architecture/shared.md#shared) / [오류](architecture/errors.md#errors) |
| JPA / 트랜잭션 / 이벤트 / 프로필 | [JPA](persistence/jpa.md#jpa) / [트랜잭션](persistence/transactions.md#transactions) / [이벤트](persistence/events.md#events) / [프로필](runtime/profiles.md#profiles) |
| HTTP / DTO | [HTTP](web/http.md#http) / [DTO](web/dto.md#dto) |
| OpenAPI 문서 / 응답 / 파라미터 / 검사 | [Docs](web/openapi/controllers.md#controllers) / [응답](web/openapi/responses.md#responses) / [파라미터](web/openapi/parameters.md#parameters) / [검사](web/openapi/verification.md#verification) |
| 테스트 설계 / 선택 / 실행 / 완료 / 리뷰 근거 | [설계](testing/design.md#design) / [선택](testing/selection.md#selection) / [실행](testing/execution.md#execution) / [완료](testing/completion.md#completion) / [근거](testing/evidence.md#evidence) |
| 계획·커밋·승인·이슈·PR·리뷰 | [작업 조건별 경로](workflow/README.md#workflow) |

- 현재 모듈 책임·계약·소스/테스트 지침은 [도메인 지도](../domain/README.md#작업-경로와-추가-지침), 빌드·버전 값은 [빌드](../../build.gradle.kts)·[버전 카탈로그](../../gradle/libs.versions.toml)가 원본입니다.
- 설계 결정·이유는 [ADR](../adr/README.md), 제품 유즈케이스·미결정 정책은 [기획 초안](../planning/use-cases.md)에 있습니다. 초안을 현재 구현·확정 규칙으로 취급하지 않습니다. 불일치는 동기화 누락인지 미결정인지 확인합니다.
- 문서 변경은 [문서 관리](../agents/documents/maintenance.md#maintenance), 반복 오류 조사는 [트러블슈팅](../troubleshooting/README.md#사례), 실행 문제는 [빠른 시작](../../README.md#빠른-시작)을 적용합니다.
- 에이전트 로딩 / 협업 / 승인 모드 점검에만 [탐색](../agents/context.md#에이전트-문서-탐색) / [인계](../agents/collaboration.md#역할-선택과-인계) / [실행 정책](../agents/execution-policy.md#실행-정책을-검토할-때)을 읽습니다.

등록 불변식·이벤트는 User와 Auth 소비 경계, subject 예제 API는 Auth·HTTP·OpenAPI 규칙을 선택합니다. 제품 모듈 분리안은 기획·현재 구현·아키텍처·ADR을 구분합니다. 검사 클래스·명령은 [집중 검사](testing/selection.md#selection)와 대상 지침이 소유하며, 필터링 검사를 전체 통과로 보고하지 않습니다.
