<a id="controllers"></a>
# OpenAPI Controller 문서 계약

- 각 Controller의 문서 계약은 `{module}.adapter.in.web.docs`의 `{ControllerName}Docs` 인터페이스에 둡니다. Controller는 이를 구현하고 HTTP 매핑·검증·유스케이스 호출만 담당합니다.
- `@Operation`, `@Tag`, Swagger `@ApiResponse`, `@Parameter`는 Docs 인터페이스에, Spring MVC의 `@RequestMapping`, `@RequestBody`, `@PathVariable`은 Controller에 둡니다.
- Docs 메서드의 Path/Query 제약은 Bean Validation 오버라이드 규칙에 따라 인터페이스에 한 번만 선언합니다. Request Body 필드 제약은 Request record에 둡니다.
- Request/Response는 `{module}.adapter.in.web` 또는 [기능별 하위 패키지](../dto.md#dto)에 두고 `docs`에서 참조하도록 `public`으로 선언합니다. 모듈 루트가 아니므로 Spring Modulith의 모듈 공개 계약에는 포함되지 않습니다.
