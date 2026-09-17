<a id="http"></a>
# HTTP 계약

- URI는 `/api/v{version}/{resource}`로 시작하고 리소스명은 복수형을 사용합니다.
- Request Body에는 `@Valid`, 경로·쿼리 제약에는 `@Validated`를 적용합니다.
- Adapter에서 Request를 Command/Query로 변환합니다. Web DTO를 Domain/Application과 공유하거나 JPA Entity를 반환하지 않습니다. DTO 변경은 [구성 규칙](dto.md#dto)을 따릅니다.
- 정상 응답은 `success`, `code`, `message`, `result`로 감쌉니다. Application의 비즈니스 오류는 모듈별 ErrorCode와 `BusinessException`으로 표현합니다. Domain 불변식 오류는 [오류 계약](../architecture/errors.md#errors)에 따라 변환합니다.
- Controller는 `{module}.adapter.in.web.docs`의 `*ControllerDocs`를 구현합니다. [Controller 문서 계약](openapi/controllers.md#controllers)에 따라 성공 결과 타입·실제 `BaseCode` enum 오류를 선언하고 공통 커스터마이저가 래퍼를 반영합니다.
- 공개 HTTP 계약 변경 시 해당 [응답](openapi/responses.md#responses)·[파라미터와 보안](openapi/parameters.md#parameters) 규칙을 적용하고 [생성 계약을 검증](openapi/verification.md#verification)합니다.
