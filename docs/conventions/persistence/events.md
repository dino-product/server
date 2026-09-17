<a id="events"></a>
# 트랜잭션 이벤트

- 완료 이벤트는 변경 트랜잭션 안에서 발행하고 `@ApplicationModuleListener`로 커밋 후 소비합니다. 롤백된 트랜잭션의 이벤트는 소비하지 않습니다.
- `shared.internal.config.AsyncConfig`의 `@EnableAsync`와 Boot TaskExecutor를 사용합니다. 리스너는 발행자와 다른 스레드·별도 트랜잭션에서 실행합니다.
- 발생 시각은 발행 시점의 `Clock` 값이며 커밋 시각이 아닙니다. 후속 처리는 API 응답과 독립적이며 소비 실패가 이미 커밋된 등록을 되돌리지 않습니다.
- 전달 보장이 필요한 확장은 저장소·재처리·멱등성과 ADR을 함께 설계합니다. 현재 구현은 [Auth의 예제 유지 범위](../../domain/auth.md#책임과-범위)를 참고합니다.
- 실제 커밋·롤백·소비 스레드를 통합 테스트로 검증합니다. 테스트 전체를 트랜잭션으로 감싸지 않습니다.

참고: [영속성과 이벤트 결정](../../adr/001-backend-architecture.md#영속성과-이벤트).
