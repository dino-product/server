<a id="responses"></a>
# OpenAPI Operation과 응답

- 모든 공개 API에 짧은 `summary`와 행위를 설명하는 `description`을 작성합니다.
- 성공 응답은 HTTP 상태·설명·`application/json`·실제 Response 타입을 명시합니다.
- Docs에는 실제 결과 타입만 선언합니다. 공통 커스터마이저가 런타임과 같은 `success`, `code`, `message`, `result` 구조로 감쌉니다. `result`는 `null`이면 응답에서 생략되므로 선택 필드로 문서화합니다.
- FQN 스키마 이름을 사용하므로 모듈별로 같은 Request/Response 이름을 사용할 수 있습니다.
- 문서화할 ErrorCode enum은 `BaseCode`를 구현합니다. `@ApiErrorCodes`의 `enumClass`와 `includes`에 실제 enum 상수 이름을 지정합니다. 한 Operation에 여러 번 선언할 수 있습니다.
- 공통 입력 오류는 `CommonErrorCode`, 모듈 오류는 해당 모듈의 ErrorCode를 사용합니다. `BaseCode`·`CommonErrorCode`는 `shared::error`, `ApiErrorCodes`는 `shared::openapi` 공개 계약입니다.
- 없는 enum 상수 이름은 OpenAPI 생성 실패로 처리합니다.
- 커스터마이저가 HTTP 상태별 실패 구조와 ErrorCode의 코드·메시지 예시를 생성합니다. ControllerDocs에서 JSON 예시를 중복 작성하지 않습니다.

```java
@Operation(summary = "예제 조회", description = "식별자로 예제를 조회합니다.")
@ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ExampleResponse.class)
        )
)
@ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "BAD_REQUEST")
@ApiErrorCodes(enumClass = ExampleErrorCode.class, includes = "NOT_FOUND")
ExampleResponse getExample(Long exampleId);
```
