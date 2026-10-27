-- notification: 참여 요청 결과 알림이 가리키는 참여 요청([알림] 기능명세 §5 관련 참여 요청, 딥링크 대상). organization이 소유한 식별자라 FK 없이 둔다.
-- 같은 참여 요청의 같은 종류 알림은 한 행만 둔다. 작업이 없는 알림이라 V3의 (member_id, type, work_id, notified_at) 제약이 막지 못하는 재전송 중복을 막는다.
-- 작업 알림은 participation_request_id가 비어 있어 PostgreSQL이 NULL을 서로 다른 값으로 보므로 이 제약에 걸리지 않는다.
alter table notifications add column participation_request_id bigint;

alter table notifications
    add constraint uk_notifications_member_type_participation_request unique (member_id, type, participation_request_id);
