<a id="modules"></a>
# 모듈 경계

`com.orbit` 최상위는 비즈니스 책임별 Application Module로 나눕니다. 최상위에 `controller`, `service`, `repository`, `entity`, `dto` 기술 계층 패키지를 만들지 않습니다. 현재 목록·책임·허용 의존성은 [도메인 지도](../../domain/README.md#모듈별-책임과-공개-계약)가 소유합니다.

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
- 즉시 응답은 공개 인터페이스, 완료 사실 전파는 공개 이벤트를 사용합니다.

참고: [배포와 모듈 경계 결정](../../adr/001-backend-architecture.md#배포와-모듈-경계).
