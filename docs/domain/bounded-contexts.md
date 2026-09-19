<a id="bounded-contexts"></a>
# 바운디드 컨텍스트 지도 (P1~P4)

- 상태: 설계 초안 — P1은 모듈 골격만 생성, P2~P4는 코드 없음
- 기준일: 2026-09-18
- 기준 자료: 기획/기획안.md(오빗 서비스 기획서), [오빗 유즈케이스 분리안](../planning/use-cases.md)

이 문서는 DDD 관점에서 오빗의 바운디드 컨텍스트(BC)를 서브도메인 분류·소유 애그리게잇·컨텍스트 간 관계까지 제안한다. 현재 등록된 모듈 목록·책임 요약·공개 계약·허용 의존성의 원본은 [도메인 지도](README.md#모듈별-책임과-공개-계약)다. 적용된 범위는 `organization`·`work`·`notification` 모듈 골격 추가이며, 아래 애그리게잇·관계·상태 전이와 P2~P4는 설계 초안이다. 골격 모듈의 허용 의존성은 현재 없고, 계획된 관계를 구현할 때 필요한 공개 계약과 의존성을 함께 추가한다.

## 범례

| 구분 | 의미 |
| --- | --- |
| **서브도메인** | Core(핵심 경쟁력) / Supporting(핵심을 돕는) / Generic(범용) |
| **상태** | `골격 생성` — 모듈 루트의 `package-info.java`만 존재, 도메인 코드 없음 / `미착수` — 폴더도 없음 |
| **관계 표기** | `참조(ID)` — 동기 조회, 상대 공개 계약만 호출 / `이벤트` — 비동기, Published Language / `ACL` — 상대 모델을 자기 언어로 번역해 수용 |

---

## P1 — MVP 골격 생성 완료

### 1. 인증 컨텍스트 (`auth`) — Generic Subdomain

| 항목 | 내용 |
| --- | --- |
| 책임 | 로그인 수단(현재 예제 코드는 subject 조회) 처리. accountId 발급까지가 책임 범위 |
| 상태 | 기존 예제 모듈 재사용 — 실제 소셜/이메일 로그인 도메인 코드로 교체 예정 |
| 소유 개념 | `AuthSubject`(예제) → 실제로는 계정 자격증명·세션 발급을 다루는 애그리게잇으로 교체 |
| 관계 | `organization`이 이 모듈의 공개 계약을 참조(ID)해 accountId 유효성을 확인 (Conformist) |

> **"계정"이라는 이름을 조직 컨텍스트에서 뺀 이유**: "계정"은 마이페이지·내 정보 수정·회원탈퇴처럼 개인정보 관리 뉘앙스가 강해서, 조직 소속을 다루는 컨텍스트와 이름이 섞이면 안 된다고 판단했다. 개인 계정·프로필 관리 자체를 어느 컨텍스트가 가질지(이 `auth`인지, 별도 컨텍스트인지)는 아직 미정 — §미해결 이슈 참고.

<a id="organization"></a>
### 2. 조직 컨텍스트 (`organization`) — Supporting Subdomain

| 항목 | 내용 |
| --- | --- |
| 책임 | 조직(발주사) 설정, 유형(직원/기사/작업유형) 관리, 초대, 조직 소속(Membership) 관리 |
| 상태 | 골격 생성 (`package-info.java`만 존재) |
| 소유 애그리게잇 | **Organization**(Root) — 조직명·업종, 유형(직원/기사/작업유형) 보유<br>**Invitation**(Root) — 발급→수락/만료/재발급, 토큰·경로(ID/링크/QR)<br>**Membership**(Root) — role(Owner/Staff/Technician)·조직 소속·활성상태, authAccountId를 불투명 참조로만 보유 |
| 관계 | `auth` ← 참조(ID) (accountId 존재 확인, ACL) · `work` → 참조(ID) 제공 (Membership 조회) · `notification` ← 이벤트 발행(`InvitationAccepted`) |

**Invitation을 별도 모듈로 분리하지 않은 이유**: `docs/planning/use-cases.md`의 5모듈 제안은 `invitation`을 독립 모듈로 뒀지만, 지금은 우선 `organization` 안의 애그리게잇 하나로 통합해서 시작한다. 초대 토큰 생명주기가 복잡해지거나(재발급 정책, 다중 소속 검증 등) 별도 팀 경계가 필요해지면 그때 모듈로 분리한다 — 지금 미리 쪼개서 얻는 이득보다 모듈 하나 늘리는 관리 비용이 더 크다고 판단.

<a id="work"></a>
### 3. 스케줄링 컨텍스트 (`work`) — Core Subdomain ★핵심

| 항목 | 내용 |
| --- | --- |
| 책임 | 작업 등록부터 완료까지 전체 생애주기. 발주–배정–수행의 최소 단위를 다룸 |
| 상태 | 골격 생성 |
| 소유 애그리게잇 | **Work**(Root) — 작업명(유일한 필수 항목), 등록자·담당기사(Membership ID 참조), 시간, 상태<br>├ CompletionReport(내부 엔티티) — 완료보고(사진·메모), Work와 생명주기 완전히 묶임<br>└ CustomerInfo/PaymentInfo(VO) — 고객정보/결제정보 |
| 상태 전이 | 등록 → 대기함(기사·시간 미정) ⇄ 수락대기 → 수락/거절 → 작업중 → 완료 (취소는 소프트 삭제, 별도 종료 경로) |
| 읽기 모델 | Timetable/Backlog — **애그리게잇 아님.** Work를 기사×시간 축으로 투영한 조회 결과일 뿐, 자체 쓰기 불변식이 없음 |
| 관계 | `organization` → 참조(ID) (담당기사·등록자가 유효한 Membership인지 확인, ACL) · `notification` ← 이벤트 발행(작업 상태 변경) |

> **명명 정정**: 기획안 원문·이전 대화에서는 이 애그리게잇을 "Job"이라 불렀으나, 저장소의 기존 계획 문서(`use-cases.md`)가 이미 `work` 모듈·`*WorkUseCase` 명명을 쓰고 있어 **"Work"로 통일**한다. 이후 모든 문서·코드에서 Job이라는 이름은 쓰지 않는다.

<a id="notification"></a>
### 4. 알림 컨텍스트 (`notification`) — Generic Subdomain

| 항목 | 내용 |
| --- | --- |
| 책임 | 다른 컨텍스트의 이벤트를 구독해 알림을 생성. 읽음/안읽음 관리 |
| 상태 | 골격 생성 |
| 소유 개념 | **Notification** — 수신자·메시지·읽음여부. 상태가 얕아 별도 VO 없음 |
| 관계 | `organization`, `work` ← 이벤트 구독 (Published Language). 양쪽 모듈 모두 알지 못하는 순수 소비자 |

---

## P2 — 다음 확장 (미착수, 코드 없음)

| 컨텍스트 | 서브도메인 | 책임 | 관계 |
| --- | --- | --- | --- |
| **검수** | Supporting | 완료보고 승인/반려 | `work` → 참조(완료보고 조회, Customer-Supplier) · `work` ← 이벤트(판정 결과) |
| **채팅** | Generic | 카톡 딥링크 대체 검증 후 자체 채팅 도입 | `organization`/`work` → 참조(ID) (jobId·membershipId로 스레드 스코프만) |
| **기사 가용시간** | — (`work` 내부 확장) | 거절률이 높을 때 도입 검토. 배정 후보 필터링용 | `work` 내부 애그리게잇으로 추가 (별도 모듈 아님) |

## P3 — 데이터 축적 후 확장 (미착수)

| 컨텍스트 | 서브도메인 | 책임 | 관계 |
| --- | --- | --- | --- |
| **정산** | Supporting | 완료 작업의 금액 집계·정산 | `work` → ACL(완료 스냅샷만 참조, 얇은 모델) |
| **리포트·통계** | Generic | 전 컨텍스트 이벤트 기반 순수 읽기 집계 | 전 컨텍스트 ← 이벤트 구독 (쓰기 없음) |
| **재고·문서함** | Generic | 자재·문서 관리 | `work` → 참조(ID) (첨부파일 id만) — 통합 자체를 최소화(Separate Ways) |

## P4 — 장기 지향점 (미착수)

| 컨텍스트 | 서브도메인 | 책임 | 관계 |
| --- | --- | --- | --- |
| **고객 셀프예약** | Core-adjacent | 최종고객이 직접 빈 슬롯을 선택해 예약 | `work`의 Timetable 읽기모델 → 참조 (Open Host Service, 가용 슬롯 조회) · `work` ← 참조(예약=Work 초안 생성, Customer-Supplier) |

---

## 미해결 이슈

| # | 이슈 | 비고 |
| --- | --- | --- |
| BC-001 | 개인 계정·프로필 관리(마이페이지, 탈퇴)를 어느 컨텍스트가 가질지 | `auth`가 가질지, 별도 "계정" 성격 컨텍스트를 새로 둘지 미정. "계정"이라는 이름을 `organization`에서 뺀 것과 직접 연결된 이슈 |
| BC-002 | Invitation을 `organization`에서 분리할 시점 | `use-cases.md`는 이미 별도 모듈(`invitation`)로 제안 중 — 필요해지면(재발급 정책 복잡화 등) 분리 |
| BC-003 | 다중 소속(N:M) 허용 여부 | `use-cases.md` §2·§8에서 Figma 화면 간 상충 확인됨(초대 수락 Spec은 타사 소속 차단, 마이페이지는 다중 소속 가능). Membership 애그리게잇의 카디널리티에 직접 영향 |
| BC-004 | 정산 기능의 실제 포함 여부 | 탈퇴 차단 조건에 "미완료 정산"이 등장하지만 정산 업무 흐름 자체는 미확인 (`use-cases.md` §8) |

---

## 다음 단계

1. `organization`/`work`/`notification`의 `domain` 패키지에 실제 애그리게잇 구현 (Organization/Invitation/Membership, Work, Notification)
2. 각 모듈 루트에 공개 계약 클래스 배치 (예: `work`의 `WorkLookup`처럼 `auth`/`user` 예제와 동일한 패턴)
3. 모듈별 `AGENTS.md` 작성 (도메인 코드가 들어가는 시점에 함께)
4. 이 문서와 [도메인 지도](README.md)를 실제 구현 진행에 맞춰 갱신
