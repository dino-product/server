<a id="verification"></a>
# OpenAPI 계약 검증

- `/docs-json` 계약 테스트에서 경로·summary·성공 응답 래퍼·대표 오류 코드를 확인합니다.
- ControllerDocs·스키마·커스터마이저 변경은 Swagger UI와 생성 JSON 계약을 검증합니다. Markdown 설명만 바뀌면 루트의 문서 검증 기준을 적용합니다.
- ControllerDocs는 자기 모듈의 ErrorCode와 `shared::error` 공개 오류 계약을 사용합니다. 다른 모듈의 내부 오류 타입에 접근하지 않는지 `ModularityTest`로 확인합니다.
