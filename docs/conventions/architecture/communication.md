<a id="communication"></a>
# 모듈 간 통신

다른 비즈니스 모듈과는 **공개 인터페이스 호출**과 **공개 이벤트** 두 방식으로만 통신합니다. 두 방식 모두 상대 모듈이 모듈 루트에 둔 공개 계약만 사용합니다. 다른 모듈의 `domain`·`application`·`adapter` 타입, Repository, JPA Entity, 테이블에는 접근하지 않습니다. `shared` 의존은 [Shared 공개 계약](shared.md#shared)을 따릅니다.

- 공개 계약을 쓰는 모듈은 상대 모듈을 `package-info.java`의 `allowedDependencies`에 추가하고 [도메인 지도](../../domain/README.md#모듈별-책임과-공개-계약)를 갱신합니다. 이벤트 구독도 발행 모듈의 이벤트 타입에 의존합니다.
- 모듈 간 의존은 순환하지 않아야 합니다. 두 모듈이 서로의 계약을 쓰게 되면 한쪽 의존의 방향을 뒤집어 한 방향으로 모읍니다. 예를 들어 B가 A를 호출하는 대신 B가 사실 이벤트를 발행하고, 이미 B에 의존하는 A가 구독합니다. 그래도 풀리지 않으면 관계를 다시 설계합니다.

```text
[소비 모듈]                                   [제공 모듈]
Application Service ── 공개 인터페이스 호출 ──▶ 모듈 루트 인터페이스 ─▶ 내부 구현
       ▲                  ◀── 계약 DTO ──
       │
입력 Port ◀─ adapter/in/event ◀── 공개 이벤트 ── 모듈 루트 이벤트 ◀─ 상태 변경
```

## 방식 선택

| 상황 | 방식 |
| --- | --- |
| 호출자가 결과를 받아야 다음 판단을 할 수 있음 (소속·권한·존재 확인, 판단에 필요한 조회) | 공개 인터페이스 호출 |
| 일어난 사실을 알리고, 호출자가 후속 처리(알림, 집계, 기록 등 부수 효과)의 결과를 기다릴 필요가 없음 | 공개 이벤트 |
| 내 변경의 결과로 다른 모듈의 상태도 바뀌어야 함 | 공개 이벤트 — 내 모듈이 자기 사실 이벤트를 발행하고 상태를 소유한 모듈이 구독해 자기 상태를 바꿈 |
| 다른 모듈의 상태 변경 결과를 즉시 받아야 함 | 상태를 소유한 모듈의 공개 인터페이스로 요청 |

- 모든 통신을 이벤트로 통일하지 않습니다. 즉시 결과가 필요한 조회까지 이벤트로 바꾸지 않습니다.
- 이벤트는 일어난 사실만 담습니다. 다른 모듈에 할 일을 시키는 명령형 이벤트는 만들지 않습니다.
- 공개 인터페이스 호출은 호출자의 트랜잭션 안에서 실행됩니다. 한 트랜잭션에서 여러 모듈의 상태를 함께 바꾸지 않도록 상태 변경 요청은 최소화합니다.
- 공개 이벤트는 발행 모듈의 커밋 후 별도 트랜잭션에서 소비됩니다. 소비 실패는 발행 쪽 커밋을 되돌리지 않습니다.

## 공개 인터페이스 호출

제공 모듈:

- 모듈 루트에 인터페이스와 계약 DTO(`record`)를 둡니다. 구현은 내부 `application/service`가 맡습니다.
- 계약 DTO에는 소비자에게 필요한 최소 값만 담습니다. 내부 Domain 객체·JPA Entity를 반환하지 않습니다.
- 특정 소비 모듈 전용 상태·행동을 계약 DTO나 제공 모듈 Domain에 누적하지 않습니다. 추가 정보가 필요하면 제공 모듈이 소유한 데이터의 최소 공개 계약을 검토합니다.

소비 모듈:

- 기본은 Application Service가 제공 모듈의 공개 인터페이스를 주입받아 호출하는 것입니다. 소비 쪽 출력 Port·Adapter를 따로 만들지 않습니다.
- Domain에는 계약 DTO 대신 필요한 값만 넘깁니다. Domain은 다른 모듈 타입을 참조하지 않으며 외부 DTO를 받는 생성자·변환 메서드를 두지 않습니다. 이 규칙은 `ArchitectureTest`가 검사하지 않으므로 리뷰에서 확인합니다.

기본 방식의 예시입니다. 타입 이름은 설명용이며 실제 계약이 아닙니다.

```java
// 제공 모듈 루트 (예: com.orbit.organization)
public interface OrganizationLookup {
    Optional<OrganizationSummary> findById(Long organizationId);
}

public record OrganizationSummary(Long organizationId, String name) {}

// 소비 모듈 application/service (com.orbit.{consumer})
@Service
public class CreateNoticeService implements CreateNoticeUseCase {

    private final OrganizationLookup organizationLookup; // 소비 쪽 Port 없이 바로 주입
    private final NoticeRepository noticeRepository;

    // 생성자 생략

    @Override
    @Transactional
    public void create(CreateNoticeCommand command) {
        String organizationName = organizationLookup.findById(command.organizationId())
                .map(OrganizationSummary::name)
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.ORGANIZATION_NOT_FOUND));
        noticeRepository.save(Notice.create(command.title(), organizationName)); // Domain에는 값만
    }
}
```

예외 — ACL:

- 두 모듈의 모델·언어가 크게 달라 소비 모듈의 모델을 보호해야 하면 소비 모듈 `application/port/out`에 자기 언어로 Port를 정의하고, `adapter/out/{제공 모듈}`에서 공개 인터페이스를 호출해 자체 모델로 변환합니다.
- 호출마다 정하지 않고 두 모듈의 관계마다 한 번 정합니다. ACL로 정한 관계와 그 이유는 제품 모듈이면 [바운디드 컨텍스트 지도](../../domain/bounded-contexts.md#bounded-contexts)의 관계 항목, 예제 모듈이면 해당 모듈 문서에 남깁니다.

## 공개 이벤트

- 발행 모듈은 이벤트 `record`를 모듈 루트에 둡니다. 다른 모듈과의 결합과 개인 데이터 확산을 줄이도록 식별자·발생 시각 등 최소 값만 담습니다.
- 소비 모듈은 `adapter/in/event`의 리스너에서 이벤트를 자기 Command로 변환해 입력 Port에 넘깁니다. 리스너에 업무 로직을 두지 않습니다.
- 발행 시점·커밋 후 소비·전달 보장 설계는 [트랜잭션 이벤트](../persistence/events.md#events)를 따릅니다.

## 소비 모듈의 자체 모델

- 같은 대상이라도 필요한 정보·역할·불변식·행동이 다르면 소비 모듈의 `domain`에 자체 모델을 둡니다. 이름·필드는 소비 모듈의 언어·유스케이스로 정합니다. 제공 모듈의 Domain 모델을 재사용하거나 계약 DTO를 자체 Domain 모델로 취급하지 않습니다.
- 별도 의미·규칙 없는 표시·조회는 Application 조회 결과 값으로 충분합니다. 필드 선택만을 위해 행동 없는 Domain 클래스를 만들지 않습니다.
- 필드가 비슷하다는 이유로 계약 DTO나 자체 모델을 `shared`에 합치지 않습니다.
- 자체 모델은 별도 테이블·원본 소유권을 뜻하지 않습니다. 원본 변경은 위 [방식 선택](#방식-선택)에 따라 소유 모듈이 처리하게 합니다. 로컬 조회 모델을 영속화할 때는 동기화·최신성·실패 복구 정책을 별도로 정합니다.

## 도메인 문서에 적는 범위

모듈 간 통신 규칙의 원본은 이 문서와 [트랜잭션 이벤트](../persistence/events.md#events)입니다. 도메인 문서(`docs/domain/`)와 모듈별 `AGENTS.md`에는 규칙을 다시 쓰지 않고 그 모듈의 사실만 적습니다.

- 공개 계약: 공개 인터페이스·계약 DTO·이벤트 목록, 필드 의미, 발행 조건 같은 약속. 목록은 `docs/domain/` 문서에만 적습니다.
- 연동 관계: 어떤 모듈의 어떤 계약·이벤트를 무엇에 쓰는지, 관계 유형(Conformist/ACL)과 ACL을 고른 이유
- 모듈 고유 결정: 계약 변경 시 확인할 소비자, 임시 구현과 교체 조건, 이 규칙과 다르게 가는 예외와 그 이유

모듈별 `AGENTS.md`에는 연동 관계와 모듈 고유 결정 가운데 그 경로의 작업에 필요한 것만 둡니다.

참고: [모듈 간 통신 결정](../../adr/001-backend-architecture.md#모듈-간-통신).
