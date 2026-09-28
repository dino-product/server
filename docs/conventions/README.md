<a id="conventions"></a>
# 공통 개발 계약

작업별 파일·앵커는 [읽기 경로](../../AGENTS.md#읽기-경로)에서 선택합니다. 이 디렉터리는 사람·구현자·검토자·CI가 함께 쓰는 기준을 유지합니다. 탐색·검사 실행·게시물 초안 작성 절차는 [담당 역할](../../.codex/agents/)이 소유합니다.

- Java·모듈·계층·영속성·HTTP·OpenAPI·테스트 계약은 해당 디렉터리에 둡니다. Git·승인·이슈·PR 기준은 [workflow](workflow/README.md#workflow)에서 찾습니다.
- 현재 책임·공개 타입은 [도메인 지도](../domain/README.md), 선택 이유는 [ADR](../adr/README.md), 미확정 제품 정책은 [기획 초안](../planning/use-cases.md)이 원본입니다. 초안을 현재 구현·확정 계약으로 취급하지 않습니다.
- 빌드·버전 값은 [빌드](../../build.gradle.kts)·[버전 카탈로그](../../gradle/libs.versions.toml), 문서 소유권은 [문서 관리](../agents/documents/maintenance.md#maintenance)를 따릅니다.
