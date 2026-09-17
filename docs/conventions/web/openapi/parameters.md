<a id="parameters"></a>
# OpenAPI 파라미터와 보안

- Path/Query에 의미·예시·필수 여부를 작성합니다.
- Request Body 필드 설명·예시는 Request record의 `@Schema`에 둡니다.
- Bearer 인증 스키마는 전역 등록하되 모든 API에 강제하지 않습니다. 인증이 필요한 Operation에만 `@SecurityRequirement(name = "Bearer Authentication")`을 선언합니다.
- 인증 처리용 내부 파라미터는 `@Parameter(hidden = true)`로 숨깁니다.
