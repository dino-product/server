# Auth 모듈 지침

[루트 지침](../../../../../../AGENTS.md)에 추가 적용합니다. 구현 범위·공개 계약·흐름은 [Auth 문서](../../../../../../docs/domain/auth.md)의 관련 절이 원본입니다.

- 예제 subject나 사용자 식별자만으로 인증 성공·토큰·세션을 만들지 않습니다.
- user와의 연동은 ACL 방식 예제로 유지합니다. 조회 결과는 `adapter/out/user`, 이벤트는 `adapter/in/event`에서 auth 소유 값으로 변환하며 Application·Domain에서 user 타입을 참조하지 않습니다. 선택 배경은 [ADR-001](../../../../../../docs/adr/001-backend-architecture.md#예제-모듈)입니다.
- 이벤트 로그는 `userId`, `occurredAt`만 기록합니다.
- 사용자 원본은 user가 소유합니다. 영속 모델·재처리를 추가하면 Auth 문서에 반영합니다.

## 인증·세션 구현 시

아래는 해당 기능을 추가·변경할 때 적용하며 현재 구현의 존재를 뜻하지 않습니다.

- OAuth 요청의 state/nonce는 시작 브라우저와 연결하고 콜백에서 일회성·만료·연결을 검증합니다. 다른 브라우저의 유효한 콜백을 수용하지 않는지 확인합니다.
- JWT는 실제 검증기로 서명·시간과 계약에서 정한 발급자·대상·토큰 종류를 확인합니다. 유효한 서명의 잘못된 claim도 거부되는지 검사합니다.
- Refresh 회전은 실제 저장소에서 정상 회전·동시 호출·재사용 철회와 합의한 만료 정책을 확인합니다. 전체 세션 철회를 제공하면 수명이 다른 세션이 공존할 때도 모두 철회되는지 검사합니다. 시계와 mock의 검증 범위는 [테스트 규칙](../../../../../../.claude/skills/dino-testing/references/design.md#design)을 따릅니다.

## 집중 검증

- Domain: `com.orbit.auth.domain.AuthSubjectTest`.
- 조회: `com.orbit.auth.application.service.GetAuthSubjectServiceTest`.
- 경계 변환: `com.orbit.auth.adapter.out.user.UserSubjectAdapterTest`, `com.orbit.auth.adapter.in.event.UserRegisteredListenerTest`.
- 모듈 조립: `com.orbit.auth.AuthModuleTest`.
- HTTP·OpenAPI·이벤트·모듈 경계는 [공통 검사 표](../../../../../../.claude/skills/dino-testing/references/verification.md#selection)를 따릅니다.
