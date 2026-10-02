# 아키텍처 계약

모듈 경계·계층·Shared·오류 계약입니다. 다른 모듈과의 통신은 [모듈 간 통신](communication.md#communication)이 원본입니다. 필요한 절만 읽습니다.

<a id="modules"></a>
## 모듈 경계

`com.orbit` 최상위는 비즈니스 책임별 Application Module로 나눕니다. 최상위에 `controller`, `service`, `repository`, `entity`, `dto` 기술 계층 패키지를 만들지 않습니다. 현재 목록·책임·허용 의존성은 [도메인 지도](../../../../docs/domain/README.md#모듈별-책임과-공개-계약)가 소유합니다.

```text
com.orbit
├── shared
│   ├── error          # named interface
│   ├── openapi        # named interface
│   └── internal
└── {business-module}
    ├── package-info.java
    ├── PublicApi.java
    ├── domain
    ├── application
    │   ├── service
    │   └── port/{in/{command,query},out}
    └── adapter/{in,out}
```

- 비즈니스 모듈의 공개 계약은 모듈 루트, `shared`는 명시적 `@NamedInterface`에 둡니다. 다른 모듈의 `domain`, `application`, `adapter` 접근과 모듈 간 JPA Entity 공유는 금지합니다.
- `package-info.java`의 `allowedDependencies`는 실제 의존성만 `shared::error`처럼 한정합니다. `shared::*` 일괄 허용은 금지합니다.
- 내부 타입 공개로 검증을 우회하지 않고 필요한 최소 계약을 설계합니다.
- 다른 모듈과의 통신 방식과 선택 기준은 [모듈 간 통신](communication.md#communication)을 따릅니다.

참고: [배포와 모듈 경계 결정](../../../../docs/adr/001-backend-architecture.md#배포와-모듈-경계).

<a id="layers"></a>
## 모듈 내부 계층

아래 경로는 `src/main/java/com/orbit/{module}` 기준입니다.

- Adapter는 Application Port와 Domain, 필요한 다른 모듈의 공개 계약에 의존합니다. `domain`은 식별자·값의 불변식을 보장하며 Application/Adapter 및 Spring·JPA·Web 타입·annotation에 의존하지 않습니다.
- 입력 Adapter는 입력 Port를 호출합니다. Application Service 구현·출력 Port·Persistence 직접 호출은 금지합니다. HTTP는 `adapter/in/web`, 이벤트 소비는 `adapter/in/event`에 둡니다.
- `application/service`는 트랜잭션 흐름을 조율하며 Domain·Port와 다른 모듈의 공개 계약([모듈 간 통신](communication.md#communication))에 의존합니다. Adapter·영속 기술에 직접 의존하지 않습니다. 구현 증가로 탐색·책임 구분이 필요할 때만 `service/command`, `service/query`로 나눕니다.
- 외부 기술 계약은 `application/port/out`, 이를 구현하는 JPA·외부 연동 Adapter는 `adapter/out`에 둡니다.
- 입력 Port는 상태 변경·후속 처리를 `application/port/in/command`, 조회를 `application/port/in/query`로 나눕니다. 빈 책임의 패키지는 만들지 않습니다.
- 입력·결과 DTO는 접미사가 아닌 소유 유스케이스에 따라 `command/dto` 또는 `query/dto`에 둡니다. 등록 결과 `RegisteredUserInfo`는 `command/dto` 소유입니다.
- Port·계약 DTO는 서비스 구현에 의존하지 않습니다. 조회·변경 패키지 분리는 별도 DB나 CQRS 인프라를 요구하지 않습니다.
- 모듈 간 공개 계약은 내부 분류와 별개로 모듈 루트에 유지합니다. 다른 모듈이 내부 Port를 참조하지 않습니다.
- Request는 Adapter에서 Command/Query로 변환합니다. Controller의 Repository/Persistence Adapter 직접 호출과 JPA Entity 반환은 금지합니다.
- 트랜잭션·시간 변경은 [트랜잭션](persistence.md#transactions), Entity 변경은 [JPA](persistence.md#jpa), 오류 변경은 [오류 계약](#errors)을 적용합니다.
- `ModularityTest`는 모듈 간 계약, `ArchitectureTest`는 내부 의존성·Domain의 다른 모듈 참조·JPA Entity 위치를 검사합니다. 실행 선택은 [집중 검사](../../dino-testing/references/verification.md#selection)를 따릅니다.

참고: [Application과 모델 분리](../../../../docs/adr/001-backend-architecture.md#application과-모델-분리), [예제 모듈 결정](../../../../docs/adr/001-backend-architecture.md#예제-모듈).

<a id="shared"></a>
## Shared 공개 계약

- 오류는 `shared::error`, OpenAPI는 `shared::openapi`로 공개합니다. 공개 타입·소비 모듈 원본은 [도메인 지도](../../../../docs/domain/README.md#모듈별-책임과-공개-계약)입니다.
- 설정·공통 응답·예외 처리·보안 구현은 `src/main/java/com/orbit/shared/internal`에 두고 공개하지 않습니다.
- 여러 모듈이 실제 재사용하는 작고 안정적인 계약만 `shared`에 둡니다. 한 모듈 전용·사용처 없는 타입은 이동하지 않습니다.
- 페이지 요청·응답은 소유 모듈의 Web Adapter에 먼저 둡니다. 둘 이상 모듈의 의미·노출 정책·변경 주기가 같아질 때만 기술 중립적인 공통 계약을 설계합니다.

<a id="errors"></a>
## 오류 코드와 불변식

- 모듈 전용 오류는 `{module}.application.error`의 내부 타입으로 유지합니다. 모듈 루트 공개 계약이나 `CommonErrorCode`에 추가하지 않습니다.
- `CommonErrorCode`에는 여러 모듈의 공통 입력·인증·시스템 오류만 둡니다.
- Application은 `BusinessException(BaseCode)`로 실패를 전달하고 전역 처리기가 HTTP 응답을 만듭니다. 응답·OpenAPI의 코드 일치를 위해 Application 오류의 `HttpStatus` 결합을 허용합니다.
- Domain 불변식 오류는 `BaseCode`, `BusinessException`, `HttpStatus`에 의존하지 않습니다. 필요한 API 오류 변환은 Application 또는 Web 경계에서 명시적으로 처리합니다.
- 신규 코드 문자열은 HTTP 상태와 독립적인 `{MODULE}-{일련번호}`(예: `AUTH-001`)를 사용합니다. 중복·재사용하지 않으며 HTTP 상태가 바뀌어도 식별자를 유지합니다.
- 기존 `COMMON-400` 등의 응답 코드는 호환성을 위해 유지합니다. 신규 명명 규칙을 기존 코드의 변경 사유로 삼지 않습니다.
