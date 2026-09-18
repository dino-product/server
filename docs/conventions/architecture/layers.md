<a id="layers"></a>
# 모듈 내부 계층

아래 경로는 `src/main/java/com/orbit/{module}` 기준입니다.

- Adapter는 Application Port와 Domain에 의존합니다. `domain`은 식별자·값의 불변식을 보장하며 Application/Adapter 및 Spring·JPA·Web 타입·annotation에 의존하지 않습니다.
- 입력 Adapter는 입력 Port를 호출합니다. Application Service 구현·출력 Port·Persistence 직접 호출은 금지합니다. HTTP는 `adapter/in/web`, 이벤트 소비는 `adapter/in/event`에 둡니다.
- `application/service`는 트랜잭션 흐름을 조율하며 Domain·Port에 의존합니다. Adapter·영속 기술에 직접 의존하지 않습니다. 구현 증가로 탐색·책임 구분이 필요할 때만 `service/command`, `service/query`로 나눕니다.
- 외부 기술 계약은 `application/port/out`, 이를 구현하는 JPA·외부 연동 Adapter는 `adapter/out`에 둡니다.
- 입력 Port는 상태 변경·후속 처리를 `application/port/in/command`, 조회를 `application/port/in/query`로 나눕니다. 빈 책임의 패키지는 만들지 않습니다.
- 입력·결과 DTO는 접미사가 아닌 소유 유스케이스에 따라 `command/dto` 또는 `query/dto`에 둡니다. 등록 결과 `RegisteredUserInfo`는 `command/dto` 소유입니다.
- Port·계약 DTO는 서비스 구현에 의존하지 않습니다. 조회·변경 패키지 분리는 별도 DB나 CQRS 인프라를 요구하지 않습니다.
- 모듈 간 공개 계약은 내부 분류와 별개로 모듈 루트에 유지합니다. 다른 모듈이 내부 Port를 참조하지 않습니다.
- Request는 Adapter에서 Command/Query로 변환합니다. Controller의 Repository/Persistence Adapter 직접 호출과 JPA Entity 반환은 금지합니다.
- 트랜잭션·시간 변경은 [트랜잭션](../persistence/transactions.md#transactions), Entity 변경은 [JPA](../persistence/jpa.md#jpa), 오류 변경은 [오류 계약](errors.md#errors)을 적용합니다.
- `ModularityTest`는 모듈 간 계약, `ArchitectureTest`는 내부 의존성·JPA Entity 위치를 검사합니다. 실행 선택은 [집중 검사](../testing/selection.md#selection)를 따릅니다.

참고: [Application과 모델 분리](../../adr/001-backend-architecture.md#application과-모델-분리), [예제 모듈 결정](../../adr/001-backend-architecture.md#예제-모듈).
