<a id="kinds"></a>
# PR 종류

사람은 PR의 코드 전체가 아니라 **핵심 다이어그램과 구현 내용 표**로 수용 여부를 판단합니다. 그래서 종류마다 판단 근거가 되는 산출물이 다릅니다. 한 PR에는 한 종류만 둡니다. 기계 판정은 [PR 규약 검사](../../../../.github/scripts/check_pr_metadata.py), 다이어그램과 코드의 일치는 [리뷰](../../dino-review/references/automation.md#규약-검사)가 확인합니다.

| 종류 | 정의 | 제목 type | 기능 명세 | 핵심 다이어그램 |
| --- | --- | --- | --- | --- |
| `유즈케이스` | 노션 기능 하나를 Web→Application→Domain→Adapter로 완결하는 수직 슬라이스. 필요한 도메인 모델·포트·어댑터를 함께 추가 | `feat` | 노션 링크 필수 | `sequenceDiagram` |
| `모듈 연동` | 새 공개 계약·이벤트·`allowedDependencies` 변경처럼 모듈 경계를 넘는 흐름 | `feat` | 노션 링크 필수 | 모듈을 참여자로 둔 `sequenceDiagram` |
| `호환성 변경` | 기존 스키마·API·이벤트 계약 변경. expand / migrate / contract 단계마다 PR 하나 | `feat`·`refactor`·`fix` (contract 단계는 `!`) | 링크 또는 `없음` | `erDiagram`·`classDiagram`·`flowchart`·`sequenceDiagram` 중 하나 |
| `구조 변경` | 외부 동작을 바꾸지 않는 리팩터링·성능 개선 | `refactor`·`perf` | `없음` | 변경 전후 `flowchart` 또는 `classDiagram` |
| `결함 수정` | 기대 동작·명세와 다른 결과를 바로잡음 | `fix` | 링크 또는 `없음` | 실패 경로를 표시한 `sequenceDiagram` |
| `유지보수` | 의존성·빌드·CI·문서·테스트 보강·되돌리기·조사 | `build`·`ci`·`docs`·`test`·`chore`·`revert`·`investigation` | `없음` | `없음` |

참고 근거: [Google Small CLs](https://google.github.io/eng-practices/review/developer/small-cls.html)(자기완결 변경·리팩터링 분리·수직 분할), [Vertical Slice](https://www.jimmybogard.com/vertical-slice-architecture/), [Parallel Change](https://martinfowler.com/bliki/ParallelChange.html), [Spring Modulith 모듈·테스트](https://docs.spring.io/spring-modulith/reference/fundamentals.html).

<a id="decide"></a>
## 판별 순서

diff에서 위부터 처음 맞는 종류를 고릅니다. 두 종류가 동시에 맞으면 [나눕니다](planning.md#unit).

1. 외부 동작이 그대로이고 코드 구조만 바뀜 → `구조 변경`. 코드가 아니면 → `유지보수`
2. 합의된 동작과 다르던 결과를 고침 → `결함 수정`
3. 기존 소비자가 대응해야 하는 스키마·API·이벤트 변경 → `호환성 변경`
4. 모듈 루트 공개 타입·named interface·이벤트·`allowedDependencies`가 바뀜 → `모듈 연동`
5. 그 밖의 새 동작 → `유즈케이스`

## 절별 작성 기준

모든 종류가 같은 [양식](../../../../.github/pull_request_template.md)과 절 순서를 씁니다. 해당 없는 필수 절은 `없음`으로 적습니다.

- **기능 명세:** PR 시점에 유효(`유효여부 O`)한 노션 기능명세 페이지 링크와 대상 절·기능명(예: `[작업] 기능명세 v1.1 §4 상세 조회`). 명세 내용을 복사하지 않습니다. 모듈별 페이지는 [도메인 지도](../../../../docs/domain/README.md#노션-기능명세)에서 찾습니다.
- **핵심 다이어그램:** `mermaid` 코드 블록. 실제 클래스·메서드 이름을 참여자·메시지로 씁니다. 한 PR에 3개 이내이며 넘으면 나눌 신호입니다.
- **구현 내용:** 아래 종류별 표. 한 행은 한 규칙이고 근거 테스트를 `클래스#메서드`로 연결합니다. 노션과 다르게 구현한 점은 `명세와 차이` 열에 적습니다.
- **기술적 선택:** 리뷰어가 동의해야 하는 선택만 `선택 / 이유 / 검토한 대안`으로 적습니다. 컨벤션을 그대로 따른 것은 적지 않습니다.
- **리뷰 포인트:** 다이어그램으로 설명되지 않아 사람이 코드를 직접 봐야 하는 위치(`경로:줄`)와 이유. 없으면 절을 지웁니다.

### 유즈케이스

```mermaid
sequenceDiagram
    actor 관리자
    participant C as WorkController
    participant S as GetWorkDetailService
    participant R as WorkRepository
    관리자->>C: GET /api/v1/works/{workId}
    C->>S: getWorkDetail(query)
    S->>R: findById(workId)
    alt 없거나 다른 조직의 작업
        S-->>C: SCHEDULE-001 (404)
    else 조회 성공
        S-->>C: WorkDetailInfo
    end
```

| 비즈니스 규칙 | 근거 테스트 | 명세와 차이 |
| --- | --- | --- |
| 다른 조직의 작업은 존재를 숨긴다(404) | `GetWorkDetailServiceTest#hidesWorkOfOtherOrganization` | 없음 |

정상 흐름과 오류 순서상 먼저 걸리는 실패 분기를 `alt`로 보입니다. 트랜잭션·이벤트 발행·잠금이 있으면 그 경계를 `Note` 또는 `rect`로 표시합니다.

### 모듈 연동

참여자를 모듈(`participant schedule`, `participant organization`)로 두고 공개 인터페이스 호출·이벤트 발행/소비를 그립니다. 구현 내용 표는 `제공·필요 인터페이스 | 변경 | 소비자 | 근거 테스트(ModularityTest, @ApplicationModuleTest, Scenario)`입니다. 구현 후 `module-reviewer` 검토 결과를 리뷰 포인트에 요약합니다.

### 호환성 변경

변경 전후 계약을 `erDiagram`(스키마)·`classDiagram`(타입) 등으로 보이고, 구현 내용 표는 `단계(expand/migrate/contract) | 바뀌는 계약 | 기존 소비자 영향 | 적용·복구 순서 | 근거 테스트`입니다. contract 단계만 `!`와 `compatibility:breaking` 라벨을 붙입니다.

### 구조 변경

변경 전후 의존 관계를 `flowchart LR` 두 개(subgraph 전/후) 또는 `classDiagram`으로 보입니다. 구현 내용 표는 `보존한 계약 | 근거(수정 없이 통과한 기존 테스트)`이며 테스트 기대값을 바꿨다면 구조 변경이 아닙니다.

### 결함 수정

실패하던 경로를 `sequenceDiagram`으로 그리고 원인 지점에 `Note over`로 표시합니다. 구현 내용 표는 `발생 조건 | 원인 | 수정 후 결과 | 재현 테스트(실패→성공)`입니다.

### 유지보수

핵심 다이어그램은 `없음`. 구현 내용에 `대상 | 목적 | 영향 | 확인 결과`를 적습니다. 의존성 업그레이드는 변경 버전과 릴리스 노트의 영향 항목만 적습니다.
