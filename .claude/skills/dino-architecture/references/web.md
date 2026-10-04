# HTTP·Web DTO·OpenAPI

필요한 절만 읽습니다.

<a id="http"></a>
## HTTP 계약

- URI는 `/api/v{version}/{resource}`로 시작하고 리소스명은 복수형을 사용합니다.
- Request Body에는 `@Valid`, 경로·쿼리 제약에는 `@Validated`를 적용합니다.
- Adapter에서 Request를 Command/Query로 변환합니다. Web DTO를 Domain/Application과 공유하거나 JPA Entity를 반환하지 않습니다. DTO 변경은 [구성 규칙](#dto)을 따릅니다.
- 정상 응답은 `success`, `code`, `message`, `result`로 감쌉니다. Application의 비즈니스 오류는 모듈별 ErrorCode와 `BusinessException`으로 표현합니다. Domain 불변식 오류는 [오류 계약](architecture.md#errors)에 따라 변환합니다.
- Controller는 `{module}.adapter.in.web.docs`의 `*ControllerDocs`를 구현합니다. [Controller 문서 계약](#controllers)에 따라 성공 결과 타입·실제 `BaseCode` enum 오류를 선언하고 공통 커스터마이저가 래퍼를 반영합니다.
- 공개 HTTP 계약 변경 시 해당 [응답](#responses)·[파라미터와 보안](#parameters) 규칙을 적용하고 [생성 계약을 검증](#verification)합니다.

<a id="dto"></a>
## Web DTO 구성

- Request/Response는 Java `record`를 우선합니다. `{module}.adapter.in.web`에 두고 타입이 적으면 평면, 독립 유스케이스가 늘면 `registration`, `profile`, `search` 등 함께 변경되는 기능별 하위 패키지로 묶습니다.
- 모듈 루트의 `dto`, `request`, `response` 기술 유형 패키지와 `UserDtos` 같은 독립 DTO 모음 타입은 금지합니다.
- 독립적으로 이름 붙일 수 있는 Request/Response는 파일당 최상위 `record` 하나로 선언합니다.
- 중첩 `record`는 상위 Request/Response 전용이며 밖에서 독립 의미가 없을 때만 사용합니다.
- 여러 API의 의미·노출 정책·변경 주기가 같으면 가장 가까운 공통 기능 패키지의 최상위 `record`로 추출합니다. 필드만 같고 의미·노출 정책이 다르면 별도 타입을 유지합니다.
- Web DTO는 모듈 간 공유하지 않습니다. 다른 모듈의 루트/named interface 계약을 받아 자신의 DTO로 변환합니다.

한 응답 전용 부분 모델:

```java
public record UserDetailResponse(Long id, Profile profile) {

    public record Profile(String name, String profileImageUrl) {}
}
```

여러 API가 같은 간이 프로필을 공유한다면 별도 파일의 `UserProfileSummaryResponse(String name, String profileImageUrl)` record로 추출합니다.

<a id="controllers"></a>
## OpenAPI Controller 문서 계약

- 각 Controller의 문서 계약은 `{module}.adapter.in.web.docs`의 `{ControllerName}Docs` 인터페이스에 둡니다. Controller는 이를 구현하고 HTTP 매핑·검증·유스케이스 호출만 담당합니다.
- `@Operation`, `@Tag`, Swagger `@ApiResponse`, `@Parameter`는 Docs 인터페이스에, Spring MVC의 `@RequestMapping`, `@RequestBody`, `@PathVariable`은 Controller에 둡니다.
- Docs 메서드의 Path/Query 제약은 Bean Validation 오버라이드 규칙에 따라 인터페이스에 한 번만 선언합니다. Request Body 필드 제약은 Request record에 둡니다.
- Request/Response는 `{module}.adapter.in.web` 또는 [기능별 하위 패키지](#dto)에 두고 `docs`에서 참조하도록 `public`으로 선언합니다. 모듈 루트가 아니므로 Spring Modulith의 모듈 공개 계약에는 포함되지 않습니다.

<a id="responses"></a>
## OpenAPI Operation과 응답

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

<a id="parameters"></a>
## OpenAPI 파라미터와 보안

- Path/Query에 의미·예시·필수 여부를 작성합니다.
- Request Body 필드 설명·예시는 Request record의 `@Schema`에 둡니다.
- Bearer 인증 스키마는 전역 등록하되 모든 API에 강제하지 않습니다. 인증이 필요한 Operation에만 `@SecurityRequirement(name = "Bearer Authentication")`을 선언합니다.
- 인증 처리용 내부 파라미터는 `@Parameter(hidden = true)`로 숨깁니다.

<a id="verification"></a>
## OpenAPI 계약 검증

- `/docs-json` 계약 테스트에서 경로·summary·성공 응답 래퍼·대표 오류 코드를 확인합니다.
- ControllerDocs·스키마·커스터마이저 변경은 Swagger UI와 생성 JSON 계약을 검증합니다. Markdown 설명만 바뀌면 루트의 문서 검증 기준을 적용합니다.
- ControllerDocs는 자기 모듈의 ErrorCode와 `shared::error` 공개 오류 계약을 사용합니다. 다른 모듈의 내부 오류 타입에 접근하지 않는지 `ModularityTest`로 확인합니다.
