<a id="notification"></a>
# 알림 컨텍스트 (`notification`) — Generic Subdomain

- 기획 원본: [기획 원본 색인](../planning/README.md#planning)의 [알림] 정책·기능명세. 표기(`[알림] §4`)는 색인을 따른다
- 다른 컨텍스트와의 관계·로드맵: [바운디드 컨텍스트 지도](bounded-contexts.md#notification)

이 문서는 `notification`의 설계·구현 결정과 Notion 기획과의 차이를 소유한다. 제품 규칙(트리거·수신자·문구·채널)은 다시 적지 않고 Notion 절 번호로만 참조한다.

## 개요

| 항목 | 내용 |
| --- | --- |
| 책임 | schedule 작업 이벤트를 받아 계정별 알림(알림센터)을 만들고, 기사 알림은 알림 설정에 따라 푸시한다. 목록·안 읽은 수·읽음 처리·알림 설정 |
| 상태 | 구현 중 — 작업 트리거 8종(배정·일정 변경·다른 기사에게 넘어감·취소·완료보고 요청 → 담당 기사, 수락·거절·완료보고 제출 → 등록자), 알림센터 API, 알림 설정 API. 조직 트리거는 미구현([차이·공백](#notification-gaps)) |
| 기획 원본 | [알림] 정책 — 트리거 §4·문구 §4.1, 채널 §5, 알림센터·설정 §6·§6.1, 상태 §8, 권한 §9, 검증 §10 |
| 소유 개념 | **Notification**(Root) — 수신 계정·발주사·종류·관련 작업·문구·상태(UNREAD/READ)·알림 시각·숨김. **NotificationSetting**(Root) — 계정별 작업 배정·일정 변경·작업 취소 푸시 수신 여부(기본 켜짐) |
| 관계 | `schedule` ← 이벤트 구독(Conformist): `WorkAssigned`·`WorkReassigned`·`WorkRescheduled`·`WorkCancelled`·`CompletionReportRequested`·`WorkAccepted`·`WorkRejected`·`CompletionReportSubmitted` · `organization` → 참조(ID) (받는 계정 조회, Conformist: 서비스가 공개 계약 `OrganizationAccountLookup`을 직접 주입받는다. 계정 식별자만 받아 번역할 모델이 없어 ACL을 두지 않는다) · `auth` → 요청 계정(`AccountPrincipal`) |

<a id="notification-implementation"></a>
## 구현 결정

- **계정 단위**: 알림센터와 알림 설정은 계정 단위다. 여러 발주사의 알림을 함께 보여 주고 각 알림의 `organizationId`로 구분한다.
- **이벤트와 알림**: 리스너는 이벤트를 받는 사람별 Command로 나누는 데까지만 하고(재배정은 새 기사·이전 기사 두 건), 실제로 만들지·푸시할지는 서비스가 정한다. `WorkReassigned`는 새 기사의 배정 알림과 이전 기사의 넘어감 알림을 한 트랜잭션에서 만든다. 일정 변경·넘어감은 받는 기사가 수락했던 작업일 때만 만든다(`NotificationType.onlyForAcceptedWork`, [알림] §4). `WorkCancelled`의 담당 기사가 없으면(대기함 작업) 만들지 않는다.
- **거절 사유 문구**: `WorkRejected`는 사유 코드(schedule 거절 사유 이름)와 메모만 담고, 표시 문구는 notification이 `NotificationMessage`에서 정한다(Figma 135:1753, [현장 수행] §4 FLD-005). 기타와 모르는 코드는 메모를, 메모가 없으면 "기타"를 쓰며 코드를 그대로 보이지 않는다. schedule이 사유를 추가하면 `NotificationMessage`의 사유 표도 함께 고친다.
- **알림 시각**: 알림 시각은 이벤트의 발생 시각(`occurredAt`)이다. 거절하면 그 기사가 그 작업에 대해 거절 시각까지 받은 기사 대상 알림을 숨겨([현장 수행] §4), 이후 같은 작업을 다시 배정받아 생긴 알림은 보인다. 직원에서 기사로 역할이 바뀐 계정이 그 작업에 대해 받은 사무실 알림은 숨기지 않는다. 이벤트는 따로 처리돼 순서가 보장되지 않으므로, 배정 알림 처리가 그 뒤의 거절 처리보다 늦게 커밋되면 그 배정 알림은 숨기지 못한다. 거절한 기사의 계약이 리스너 처리 전에 비활성화되면 계정을 찾지 못해 숨김을 건너뛴다.
- **받는 계정**: 기사 대상은 활성 기사 계약, 사무실 대상은 등록자의 활성 직원 소속의 계정이다. 없으면(비활성·없는 계약·소속) 알림을 만들지 않는다.
- **푸시**: 출력 포트 `PushNotificationPort`. 지금은 `local`·`test`·`dev` 전용 임시 어댑터(`FakePushNotificationAdapter`, 알림 id·계정·종류만 기록)만 있어 운영(`prod`)과 프로필 없는 실행은 Bean 부재로 기동하지 않는다(schedule 임시 어댑터와 같은 상태). 실제 발송 어댑터(N-21)를 추가할 때 이 어댑터를 지우고 커밋 뒤 발송한다.
- **전달 보장**: 이벤트 발행 기록 저장소가 없어 리스너 실패·재시작 때 다시 처리하지 않고, 같은 이벤트가 두 번 오면 알림도 두 번 생긴다([ADR-001](../adr/001-backend-architecture.md#모듈-간-통신)). 리스너 실패는 schedule 커밋을 되돌리지 않는다.
- **동시 수정**: 읽음 처리와 거절 숨김이 같은 행을 고칠 수 있어 바뀐 컬럼만 갱신한다(`@DynamicUpdate`). 한쪽이 읽고 있는 사이 다른 쪽이 커밋해도 읽음과 숨김이 함께 남는지 실제 커밋으로 확인한다(`NotificationPersistenceAdapterTest`).
- **테이블(ERD 반영용)**: `notifications`는 수신 계정(`member_id`)·발주사(`company_id`)·작업(`work_id`)을 ID로만 두고 인덱스 `(member_id, notified_at)`(목록), `(member_id, status)`(안 읽은 수), `(member_id, work_id)`(거절 숨김 대상)를 둔다. `notification_settings`는 `member_id` 유일 제약으로 계정당 한 행이다.

<a id="notification-gaps"></a>
## 차이·공백

ID 대역·추적 칸의 뜻은 [차이·공백 표기](../planning/README.md#gaps)를 따른다.

| 유형 | ID | 내용 | 추적 |
| --- | --- | --- | --- |
| Notion 확정, 미구현 | N-20 | 조직 트리거(참여 요청 접수·처리 결과, 역할 변경, 작업유형 삭제) ([알림] §4). 발행 쪽 기능(참여 승인·거절, 역할 변경, HM-295)이 생기면 구독한다 | 미등록 |
| Notion 확정, 미구현 | N-21 | 실제 앱 푸시(기기 토큰 등록·FCM 발송) ([알림] §5) | 미등록 |
| 구현에서 정한 제품 결정 | N-10 | 알림 설정을 계정 단위로 저장하고 기사 역할로 제한하지 않는다([알림] §9는 기사 본인만 변경) | 미등록 |
