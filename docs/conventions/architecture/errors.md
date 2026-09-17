<a id="errors"></a>
# 오류 코드와 불변식

- 모듈 전용 오류는 `{module}.application.error`의 내부 타입으로 유지합니다. 모듈 루트 공개 계약이나 `CommonErrorCode`에 추가하지 않습니다.
- `CommonErrorCode`에는 여러 모듈의 공통 입력·인증·시스템 오류만 둡니다.
- Application은 `BusinessException(BaseCode)`로 실패를 전달하고 전역 처리기가 HTTP 응답을 만듭니다. 응답·OpenAPI의 코드 일치를 위해 Application 오류의 `HttpStatus` 결합을 허용합니다.
- Domain 불변식 오류는 `BaseCode`, `BusinessException`, `HttpStatus`에 의존하지 않습니다. 필요한 API 오류 변환은 Application 또는 Web 경계에서 명시적으로 처리합니다.
- 신규 코드 문자열은 HTTP 상태와 독립적인 `{MODULE}-{일련번호}`(예: `AUTH-001`)를 사용합니다. 중복·재사용하지 않으며 HTTP 상태가 바뀌어도 식별자를 유지합니다.
- 기존 `COMMON-400` 등의 응답 코드는 호환성을 위해 유지합니다. 신규 명명 규칙을 기존 코드의 변경 사유로 삼지 않습니다.
