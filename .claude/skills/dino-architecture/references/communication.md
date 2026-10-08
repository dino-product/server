<a id="communication"></a>
# 모듈 간 통신

다른 비즈니스 모듈과는 **공개 인터페이스 호출**과 **공개 이벤트** 두 방식으로만 통신합니다. 두 방식 모두 상대 모듈이 모듈 루트에 둔 공개 계약만 사용합니다. 다른 모듈의 `domain`·`application`·`adapter` 타입, Repository, JPA Entity, 테이블에는 접근하지 않습니다. `shared` 의존은 [Shared 공개 계약](architecture.md#shared)을 따릅니다.

- 공개 계약을 쓰는 모듈은 상대 모듈을 `package-info.java`의 `allowedDependencies`에 추가하고 [도메인 지도](../../../../docs/domain/README.md#모듈별-책임과-공개-계약)를 갱신합니다. 이벤트 구독도 발행 모듈의 이벤트 타입에 의존합니다.
- 모듈 간 의존은 순환하지 않아야 합니다. 두 모듈이 서로의 계약을 쓰게 되면 한쪽 의존의 방향을 뒤집어 한 방향으로 모읍니다. 예를 들어 B가 A를 호출하는 대신 B가 사실 이벤트를 발행하고, 이미 B에 의존하는 A가 구독합니다. B가 A의 결과를 즉시 받아야 해서 이벤트로 풀 수 없으면 [요구 인터페이스](#요구-인터페이스)로 의존을 뒤집습니다. 그래도 풀리지 않으면 관계를 다시 설계합니다.

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
| 내 변경과 다른 모듈의 상태 변경이 함께 성공하거나 함께 실패해야 함 (업무 규칙상 원자성 필요) | 상태를 소유한 모듈의 공개 인터페이스로 요청. 그 모듈이 이미 내게 의존하면 [요구 인터페이스](#요구-인터페이스) |
| 다른 모듈의 상태 변경 결과를 즉시 받아야 함 | 상태를 소유한 모듈의 공개 인터페이스로 요청 |

- 모든 통신을 이벤트로 통일하지 않습니다. 즉시 결과가 필요한 조회까지 이벤트로 바꾸지 않습니다.
- 이벤트는 일어난 사실만 담습니다. 다른 모듈에 할 일을 시키는 명령형 이벤트는 만들지 않습니다.
- 공개 인터페이스 호출은 호출자의 트랜잭션 안에서 실행됩니다. 한 트랜잭션에서 여러 모듈의 상태를 함께 바꾸지 않도록 상태 변경 요청은 원자성이 업무 규칙일 때만 씁니다.
- 공개 이벤트는 발행 모듈의 커밋 후 별도 트랜잭션에서 소비됩니다. 소비 실패는 발행 쪽 커밋을 되돌리지 않습니다.

## 공개 인터페이스 호출

제공 모듈:

- 모듈 루트에 인터페이스와 계약 DTO(`record`)를 둡니다. 구현은 내부 `application/service`가 맡습니다.
- 계약 DTO에는 소비자에게 필요한 최소 값만 담습니다. 내부 Domain 객체·JPA Entity를 반환하지 않습니다.
- 특정 소비 모듈 전용 상태·행동을 계약 DTO나 제공 모듈 Domain에 누적하지 않습니다. 추가 정보가 필요하면 제공 모듈이 소유한 데이터의 최소 공개 계약을 검토합니다.

소비 모듈:

- 기본은 Application Service가 제공 모듈의 공개 인터페이스를 주입받아 호출하는 것입니다. 소비 쪽 출력 Port·Adapter를 따로 만들지 않습니다.
- Domain에는 계약 DTO 대신 필요한 값만 넘깁니다. Domain은 다른 모듈 타입을 참조하지 않으며 외부 DTO를 받는 생성자·변환 메서드를 두지 않습니다. 이 규칙은 `ArchitectureTest`가 검사합니다.

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
- 호출마다 정하지 않고 두 모듈의 관계마다 한 번 정합니다. ACL로 정한 관계와 그 이유는 구현이 시작된 모듈이면 해당 모듈 문서, 아직 설계 단계인 제품 모듈이면 [바운디드 컨텍스트 지도](../../../../docs/domain/bounded-contexts.md#bounded-contexts)의 관계 항목에 남깁니다.

<a id="요구-인터페이스"></a>
## 요구 인터페이스 (의존 역전)

이미 A가 B에 의존하는데 B가 A의 상태로 판단해야 하면(B → A 호출은 순환), B가 필요한 질의를 자기 모듈 루트에 인터페이스로 선언하고 A가 구현합니다. 타입 의존은 A → B 그대로이고 실행 시 호출만 B → A로 흐릅니다.

```text
[A: 구현 모듈]                                     [B: 선언 모듈]
adapter/in/{B 모듈} 구현체 ── implements ──▶ 모듈 루트 요구 인터페이스 ◀── B의 Application Service
        │
        └─▶ A의 입력 Port
```

- **즉시 결과가 필요한 조회·검증**에 씁니다(예: 작업 유형 삭제의 영향 건수). 확인과 그 결과에 따른 A의 상태 변경이 B의 변경과 원자적이어야 하면 둘을 한 메서드로 묶어 변경도 맡깁니다(예: 기사 역할 변경 — 작업중이면 거부, 아니면 배정됨·작업전 작업 반환). 원자성이 필요 없는 후속 처리는 B의 사실 이벤트를 A가 구독해 처리합니다.
- 인터페이스와 반환 값(`record`)은 B의 모듈 루트에 두고 B의 언어로 이름 짓습니다. 판단에 필요한 최소 값만 받고 돌려줍니다.
- 구현체는 A의 `adapter/in/{B 모듈}`에 두고 A의 입력 Port만 호출하며, B의 트랜잭션 안에서 실행됩니다. 조회 메서드는 상태를 바꾸지 않고, 판정과 B의 커밋 사이의 경합은 해당 기능에서 다룹니다. 변경을 묶은 메서드는 확인과 변경을 함께 하고, B가 응답·후속 처리에 쓸 결과(거부 사유·바뀐 대상)가 있으면 반환 값으로 돌려주며, 실패하면 B의 변경도 롤백됩니다.
- 구현은 한 모듈만 제공합니다. 구현이 아직 없으면 [제공 기능이 아직 없을 때](#제공-기능이-아직-없을-때)의 임시 구현 규칙을 B 쪽에 적용합니다(`application/service`, 로컬·테스트 프로필, 안전한 고정값).
- B의 `@ApplicationModuleTest`는 요구 인터페이스를 `@MockitoBean`으로 대체하고, A의 구현체는 A의 입력 Port를 fake로 둔 단위 테스트로 검증합니다.
- 도메인 지도에는 B의 공개 계약으로 적고 구현 모듈을 함께 적습니다.

## 공개 이벤트

- 발행 모듈은 이벤트 `record`를 모듈 루트에 둡니다. 다른 모듈과의 결합과 개인 데이터 확산을 줄이도록 식별자·발생 시각 등 최소 값만 담습니다.
- 소비 모듈은 `adapter/in/event`의 리스너에서 이벤트를 자기 Command로 변환해 입력 Port에 넘깁니다. 리스너에 업무 로직을 두지 않습니다.
- 발행 시점·커밋 후 소비·전달 보장 설계는 [트랜잭션 이벤트](persistence.md#events)를 따릅니다.

## 제공 기능이 아직 없을 때

필요한 다른 모듈의 기능이 아직 구현되지 않았으면 계약을 먼저 정하고 구현은 나중에 끼웁니다. 아래 임시 구현 규칙은 다른 모듈 계약과 ACL 출력 Port에만 적용하며, 자기 모듈 Port의 임시 구현(예: 메모리 저장소)에는 적용하지 않습니다.

- 필요한 쪽이 계약(인터페이스·계약 DTO 또는 이벤트 `record`) 초안을 제안하고 제공 모듈 담당자가 합의·리뷰합니다. 계약은 제공 모듈 루트에 두며 구현보다 먼저 병합할 수 있습니다. 계약과 임시 구현만 먼저 올리는 PR은 `모듈 연동` 종류이며 [PR 단위](../../dino-pr/references/planning.md#unit)의 '사용처 없는 공개 API 선언 금지'의 예외입니다. 리뷰 포인트에 이 계약을 쓸 소비 기능과 실제 구현의 추적 이슈를 적습니다. ACL 관계면 소비 모듈이 자기 출력 Port를 먼저 정의하므로 제공 계약 없이 진행하고, 계약이 생기면 어댑터를 그 계약 호출로 바꿉니다.
- 임시 구현은 기본 방식이면 제공 모듈의 `application/service`, ACL 관계면 소비 모듈의 `adapter/out/{제공 모듈}`에 두고 이름으로 임시임을 드러냅니다(예: `Denying…`).
- 임시 구현은 `@Profile({"local & !prod", "test & !prod"})`로 로컬·테스트에서만 등록합니다. `prod`와 프로필 없는 실행은 Bean이 없어 기동에 실패하며 이것이 의도입니다.
- 임시 구현은 거부·빈 결과 같은 안전한 고정값만 돌려주고 가짜 성공을 만들지 않습니다. 설정이나 저장소를 두지 않으며 교체 조건과 추적 이슈를 적어 둡니다.
- 실제 구현 전까지 그 계약에 기대는 소비 기능은 외부에 노출하지 않습니다.
- 실제 구현을 추가하는 PR에서 임시 구현을 삭제합니다. `@Primary`로 덮어 두지 않습니다.
- 소비 모듈 테스트에서 계약을 대체하는 방법은 [테스트 설계](../../dino-testing/references/design.md#design)를 따릅니다. 제공 모듈의 임시 구현은 소비 모듈의 `@ApplicationModuleTest`(독립 모듈 조립)에 등록되지 않습니다.

## 소비 모듈의 자체 모델

- 같은 대상이라도 필요한 정보·역할·불변식·행동이 다르면 소비 모듈의 `domain`에 자체 모델을 둡니다. 이름·필드는 소비 모듈의 언어·유스케이스로 정합니다. 제공 모듈의 Domain 모델을 재사용하거나 계약 DTO를 자체 Domain 모델로 취급하지 않습니다.
- 별도 의미·규칙 없는 표시·조회는 Application 조회 결과 값으로 충분합니다. 필드 선택만을 위해 행동 없는 Domain 클래스를 만들지 않습니다.
- 필드가 비슷하다는 이유로 계약 DTO나 자체 모델을 `shared`에 합치지 않습니다.
- 자체 모델은 별도 테이블·원본 소유권을 뜻하지 않습니다. 원본 변경은 위 [방식 선택](#방식-선택)에 따라 소유 모듈이 처리하게 합니다. 로컬 조회 모델을 영속화할 때는 동기화·최신성·실패 복구 정책을 별도로 정합니다.

## 도메인 문서에 적는 범위

모듈 간 통신 규칙의 원본은 이 문서와 [트랜잭션 이벤트](persistence.md#events)입니다. 도메인 문서(`docs/domain/`)와 모듈별 `AGENTS.md`에는 규칙을 다시 쓰지 않고 그 모듈의 사실만 적습니다.

- 공개 계약: 공개 인터페이스·계약 DTO·이벤트 목록, 필드 의미, 발행 조건 같은 약속. 목록은 `docs/domain/` 문서에만 적습니다.
- 연동 관계: 어떤 모듈의 어떤 계약·이벤트를 무엇에 쓰는지, 관계 유형(Conformist/ACL)과 ACL을 고른 이유
- 모듈 고유 결정: 계약 변경 시 확인할 소비자, 임시 구현과 교체 조건, 이 규칙과 다르게 가는 예외와 그 이유

모듈별 `AGENTS.md`에는 연동 관계와 모듈 고유 결정 가운데 그 경로의 작업에 필요한 것만 둡니다.

참고: [모듈 간 통신 결정](../../../../docs/adr/001-backend-architecture.md#모듈-간-통신).
