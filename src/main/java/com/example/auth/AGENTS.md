# Auth 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 구현 범위·공개 계약·흐름은 [Auth 문서](../../../../../../docs/domain/auth.md)의 관련 절이 원본입니다.

- 예제 subject나 사용자 식별자만으로 인증 성공·토큰·세션을 만들지 않습니다.
- user의 공개 조회 결과는 `adapter/out/user`, 공개 이벤트는 `adapter/in/event`에서 auth 소유 값으로 변환합니다. 이벤트 입력은 auth의 입력 Port에 위임합니다. user 내부 타입·Repository를 참조하거나 Application·Domain에서 user 타입을 참조하지 않습니다.
- 등록 커밋 후 별도 스레드·트랜잭션에서 소비하며 롤백 시 처리하지 않습니다. 로그는 `userId`, `occurredAt`만 기록합니다.
- 사용자 원본은 user가 소유합니다. 영속 모델·재처리 확장은 [영속성·이벤트](../../../../../../docs/conventions/persistence/events.md#events)에 따라 설계하고 Auth 문서에 반영합니다.

## 인증·세션 구현 시

아래는 해당 기능을 추가·변경할 때 적용하며 현재 구현의 존재를 뜻하지 않습니다.

- OAuth 요청의 state/nonce는 시작 브라우저와 연결하고 콜백에서 일회성·만료·연결을 검증합니다. 다른 브라우저의 유효한 콜백을 수용하지 않는지 확인합니다.
- JWT는 실제 검증기로 서명·시간과 계약에서 정한 발급자·대상·토큰 종류를 확인합니다. 유효한 서명의 잘못된 claim도 거부되는지 검사합니다.
- Refresh 회전은 실제 저장소에서 정상 회전·동시 호출·재사용 철회와 합의한 만료 정책을 확인합니다. 전체 세션 철회를 제공하면 수명이 다른 세션이 공존할 때도 모두 철회되는지 검사합니다. 시계와 mock의 검증 범위는 [테스트 규칙](../../../../../../docs/conventions/testing/design.md#design)을 따릅니다.

## 집중 검증

- Domain: `com.example.auth.domain.AuthSubjectTest`.
- 조회: `com.example.auth.application.service.GetAuthSubjectServiceTest`.
- 경계 변환: `com.example.auth.adapter.out.user.UserSubjectAdapterTest`, `com.example.auth.adapter.in.event.UserRegisteredListenerTest`.
- 모듈 조립: `com.example.auth.AuthModuleTest`.
- HTTP·OpenAPI·이벤트·모듈 경계는 [공통 검사 표](../../../../../../docs/conventions/testing/selection.md#selection)를 따릅니다. 실제 커밋·롤백·비동기 검증을 리스너 직접 호출로 대체하지 않습니다.
