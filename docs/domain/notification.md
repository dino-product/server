<a id="notification"></a>
# 알림 컨텍스트 (`notification`) — Generic Subdomain

- 기획 원본: [기획 원본 색인](../planning/README.md#planning)의 [알림] 정책·기능명세. 표기(`[알림] §4`)는 색인을 따른다
- 다른 컨텍스트와의 관계·로드맵: [바운디드 컨텍스트 지도](bounded-contexts.md#notification)

이 문서는 `notification`의 설계·구현 결정과 Notion 기획과의 차이를 소유한다. 제품 규칙(트리거·수신자·문구·채널)은 다시 적지 않고 Notion 절 번호로만 참조한다.

## 개요

| 항목 | 내용 |
| --- | --- |
| 책임 | schedule 작업 이벤트를 받아 계정별 알림(알림센터)을 만들고, 기사 알림은 알림 설정에 따라 기사 앱으로 푸시한다(FCM). 목록·안 읽은 수·읽음 처리·알림 설정, 앱 푸시 기기 등록·해제 |
| 상태 | 구현 중 — 작업 트리거 8종(배정·일정 변경·다른 기사에게 넘어감·취소·완료보고 요청 → 담당 기사, 수락·거절·완료보고 제출 → 등록자), 알림센터 API, 알림 설정 API, 앱 푸시 기기 API와 FCM 발신. 조직 트리거는 미구현([차이·공백](#notification-gaps)) |
| 기획 원본 | [알림] 정책 — 트리거 §4·문구 §4.1, 채널 §5, 알림센터·설정 §6·§6.1, 상태 §8, 권한 §9, 검증 §10 |
| 소유 개념 | **Notification**(Root) — 수신 계정·발주사·종류·관련 작업·문구·상태(UNREAD/READ)·알림 시각·숨김. **NotificationSetting**(Root) — 계정별 작업 배정·일정 변경·작업 취소 푸시 수신 여부(기본 켜짐). **PushDevice**(Root) — 앱 푸시 기기: 소유 계정·플랫폼(iOS·Android)·FCM 등록 토큰(유일)·마지막 등록 시각 |
| 관계 | `schedule` ← 이벤트 구독(Conformist): `WorkAssigned`·`WorkReassigned`·`WorkRescheduled`·`WorkCancelled`·`CompletionReportRequested`·`WorkAccepted`·`WorkRejected`·`CompletionReportSubmitted` · `organization` → 참조(ID) (받는 계정 조회, Conformist: 서비스가 공개 계약 `OrganizationAccountLookup`을 직접 주입받는다. 계정 식별자만 받아 번역할 모델이 없어 ACL을 두지 않는다) · `profile` ← 이벤트 구독(Conformist): `AccountWithdrawn`(탈퇴한 계정의 앱 푸시 기기 삭제) · `auth` → 요청 계정(`AccountPrincipal`) · `realtime` ← 이벤트 발행(루트 공개 record `NotificationCreated`·`NotificationRead`, 커밋 뒤 웹 헤더 배지 갱신 신호) |

<a id="notification-implementation"></a>
## 구현 결정

- **계정 단위**: 알림센터와 알림 설정은 계정 단위다. 여러 발주사의 알림을 함께 보여 주고 각 알림의 `organizationId`로 구분한다.
- **이벤트와 알림**: 리스너는 이벤트를 받는 사람별 Command로 나누는 데까지만 하고(재배정은 새 기사·이전 기사 두 건), 실제로 만들지·푸시할지는 서비스가 정한다. `WorkReassigned`는 새 기사의 배정 알림과 이전 기사의 넘어감 알림을 한 트랜잭션에서 만든다. 일정 변경·넘어감은 받는 기사가 수락했던 작업일 때만 만든다(`NotificationType.onlyForAcceptedWork`, [알림] §4). `WorkCancelled`의 담당 기사가 없으면(대기함 작업) 만들지 않는다.
- **거절 사유 문구**: `WorkRejected`는 사유 코드(schedule 거절 사유 이름)와 메모만 담고, 표시 문구는 notification이 `NotificationMessage`에서 정한다(Figma 135:1753, [현장 수행] §4 FLD-005). 기타와 모르는 코드는 메모를, 메모가 없으면 "기타"를 쓰며 코드를 그대로 보이지 않는다. schedule이 사유를 추가하면 `NotificationMessage`의 사유 표도 함께 고친다.
- **알림 시각**: 알림 시각은 schedule 이벤트의 발생 시각(`occurredAt`)이다. 거절하면 그 기사가 그 작업에 대해 거절 시각까지 받은 기사 대상 알림을 숨겨([현장 수행] §4), 이후 같은 작업을 다시 배정받아 생긴 알림은 보인다. 직원에서 기사로 역할이 바뀐 계정이 그 작업에 대해 받은 사무실 알림은 숨기지 않는다. 이벤트는 따로 처리돼 순서가 보장되지 않으므로, 배정 알림 처리가 그 뒤의 거절 처리보다 늦게 커밋되면 그 배정 알림은 숨기지 못한다. 거절한 기사의 계약이 리스너 처리 전에 비활성화되면 계정을 찾지 못해 숨김을 건너뛴다.
- **받는 계정**: 기사 대상은 활성 기사 계약, 사무실 대상은 등록자의 활성 직원 소속의 계정이다. 없으면(비활성·없는 계약·소속) 알림을 만들지 않는다.
- **푸시**: 서비스는 출력 포트 `PushNotificationPort`로 요청하고, 구현(`PushNotificationQueueAdapter`)은 알림을 저장하는 트랜잭션에 발신 대기(`notification_deliveries`)를 넣는다. 주기 작업(`PushDeliveryDispatchScheduler`, `app.notification.push-delivery.*`)이 커밋된 발신 대기를 집어 실제 발신 포트 `PushSender`로 보내고, 다시 보낼 실패는 횟수 제한 없이 재시도한다. 설계와 재시도·종료 규칙은 [ADR-004](../adr/004-event-publication-registry.md#기사-앱-푸시-발신-대기열)가 원본이다.
- **기기별 발신**: 디스패처(`DispatchPushDeliveriesService`)가 수신 계정의 등록 기기마다 `PushSender`를 부르고 결과를 모은다. 한 기기라도 받으면 보낸 것이고(다른 기기의 실패는 다시 보내지 않는다), 받은 기기가 없으면 다시 보낼 실패가 하나라도 있을 때 모든 기기로 다시 보내며, 등록된 기기가 없거나 모두 등록 해제됐으면 건너뛴다. 제공자가 더 받지 않는 토큰의 기기는 그 자리에서 지운다. 드물게 같은 알림이 다시 가도 Android tag·APNs collapse id(`notification-{알림 id}`)로 기기에서 하나로 덮어쓴다.
- **FCM 발신기**: `FcmPushSender`가 FCM HTTP v1으로 보낸다. 표시는 알림 문구뿐이고(Notion에 없는 제목·앱 아이콘 배지는 두지 않음), 데이터에 알림 식별자·종류·관련 작업 또는 참여 요청을 문자열로 담는다. FCM 오류는 `UNREGISTERED`와 토큰 필드 오류만 기기가 더 받지 않는 것으로, 토큰이 아닌 400만 다시 보내도 성공할 수 없는 실패로, 그 밖(429·5xx·인증·발신자 불일치·주소 오류·통신 실패·제한 시간 초과)은 다시 보낼 실패로 나눈다. 설정 오류로 정상 기기를 지우지 않기 위해서다. access token은 서비스 계정 키로 서명한 JWT bearer grant(RFC 7523)를 `RestClient`로 보내 받고 만료 5분 전까지 다시 쓴다. Firebase Admin SDK·Google 인증 라이브러리는 guava·gson·google-http-client를 끌고 오고 제한 시간·오류 처리가 따로 놀며, 발송·토큰 주소를 로컬 스텁으로 바꿀 수 없어 쓰지 않는다.
- **환경별 발신기**: `app.notification.push.fcm.enabled`로 고른다. 운영(`prod`)은 항상 켜며 `FCM_CREDENTIALS`(서비스 계정 키 JSON의 base64)가 없거나 틀리면 기동하지 않는다. 개발 서버는 `FCM_ENABLED`로 켠다. 끄면 `local`·`test`·`dev`는 보내지 않고 기록만 하는 `LoggingPushSender`를 쓰고, 그 밖의 환경은 발신기가 없어 기동하지 않는다.
- **앱 푸시 기기**: 기기는 FCM 등록 토큰마다 하나이고 마지막으로 등록한 계정의 것이다. 등록(`POST /api/v1/push-devices`)은 PostgreSQL upsert라 같은 토큰을 다시 등록하면 같은 기기 식별자를 돌려주고, 같은 기기에서 다른 계정이 등록하면 그 계정으로 넘어가며, 동시에 등록해도 한 행만 남는다. 앱은 시작할 때와 토큰이 바뀔 때마다 등록하고, 로그아웃 직전에 등록 때 받은 기기 식별자로 해제한다(`DELETE /api/v1/push-devices/{pushDeviceId}`, 토큰을 주소에 넣지 않아 접근 로그에 남지 않음). 해제는 본인 기기만 되며 없거나 다른 계정의 기기면 `NOTIFICATION-005`다. 역할은 보지 않는다(소속 전 신청자도 참여 요청 결과 푸시를 받음). 기기 수 상한·미사용 기기 정리 작업은 두지 않고, 만료된 토큰은 FCM `UNREGISTERED` 응답으로 지운다. 탈퇴하면 `AccountWithdrawn` 구독(`AccountWithdrawnEventListener`)으로 그 계정의 기기를 모두 지운다. 탈퇴 커밋 직전에 인증을 통과한 기기 등록이 이 삭제보다 늦게 커밋되면 그 기기는 남으며, 드문 경합이라 받아들인다. 토큰은 로그·오류 응답·문자열 표현에 앞 6자와 길이만 남긴다.
- **공개 이벤트**: 알림을 저장하면 `NotificationCreated`, 읽음으로 바꾸면 `NotificationRead`를 발행한다(조건은 각 record Javadoc). 알림센터·안 읽은 수가 계정 단위라 발주사 없이 받는 계정만 담는다. 거절로 기사 알림을 숨길 때는 발행하지 않는다. 숨김 대상은 기사 대상 알림이고, 직원을 겸하는 계정의 웹 배지는 다음 조회에서 맞춰지면 충분하기 때문이다.
- **전달 보장**: 리스너가 실패하거나 처리 중에 멈추면 이벤트 발행 기록이 처리될 때까지 횟수 제한 없이 다시 보낸다([ADR-004](../adr/004-event-publication-registry.md)). 그래서 같은 이벤트가 두 번 이상 올 수 있으며, 서비스는 같은 일(받는 계정·종류·작업·일어난 시각)의 알림이 이미 있으면 다시 만들지도 푸시하지도 않는다. 같은 일이 동시에 처리되면 유일 제약이 한쪽을 실패시키고 재전송 때 건너뛴다. 지금 알림은 모두 작업 알림이라 유일 제약이 적용되며, 작업이 없는 알림(조직 트리거 N-20)을 더할 때는 다른 키가 필요하다. 리스너 실패는 schedule 커밋을 되돌리지 않는다.
- **동시 수정**: 읽음 처리와 거절 숨김이 같은 행을 고칠 수 있어 바뀐 컬럼만 갱신한다(`@DynamicUpdate`). 한쪽이 읽고 있는 사이 다른 쪽이 커밋해도 읽음과 숨김이 함께 남는지 실제 커밋으로 확인한다(`NotificationPersistenceAdapterTest`).
- **테이블(ERD 반영용)**: `notifications`는 수신 계정(`member_id`)·발주사(`company_id`)·작업(`work_id`)을 ID로만 두고 인덱스 `(member_id, notified_at)`(목록), `(member_id, status)`(안 읽은 수), `(member_id, work_id)`(거절 숨김 대상)와 같은 일 하나만 남기는 유일 제약 `(member_id, type, work_id, notified_at)`을 둔다. `notification_settings`는 `member_id` 유일 제약으로 계정당 한 행이다. `notification_deliveries`는 알림(`notification_id`, 외래 키 없음)·수신 계정(`recipient_account_id`)·채널·상태·시도 횟수·다음 시도 시각·마지막 오류·제공자 메시지 식별자·보낸 시각을 두고, `(notification_id, channel)` 유일 제약과 인덱스 `(status, next_attempt_at)`(집기)를 둔다. `push_devices`는 계정(`member_id`, ID로만)·플랫폼·토큰(최대 1024자)·마지막 등록 시각을 두고, 토큰 유일 제약(upsert 키)과 인덱스 `(member_id)`(계정의 기기 조회·탈퇴 삭제)를 둔다.

<a id="notification-gaps"></a>
## 차이·공백

ID 대역·추적 칸의 뜻은 [차이·공백 표기](../planning/README.md#gaps)를 따른다.

| 유형 | ID | 내용 | 추적 |
| --- | --- | --- | --- |
| Notion 확정, 미구현 | N-20 | 조직 트리거(참여 요청 접수·처리 결과, 역할 변경, 작업유형 삭제) ([알림] §4). 발행 쪽 기능(참여 승인·거절, 역할 변경, HM-295)이 생기면 구독한다 | 미등록 |
| 구현에서 정한 제품 결정 | N-10 | 알림 설정을 계정 단위로 저장하고 기사 역할로 제한하지 않는다([알림] §9는 기사 본인만 변경) | 미등록 |
