-- notification: 같은 일(받는 계정·종류·작업·알림 시각)의 알림은 한 행만 둔다. 다시 전달된 이벤트가 동시에 처리돼도 하나만 남기는 마지막 방어선이다(ADR-004 소비 측 멱등).
-- work_id가 비어 있는 알림은 PostgreSQL이 NULL을 서로 다른 값으로 보므로 이 제약으로 막지 못한다.
-- 제약을 걸기 전에, 이 제약이 없던 동안 재전송으로 중복 생성된 알림은 가장 먼저 만든 행만 남기고 지운다. 같은 푸시를 두 번 보내지 않도록 지우는 알림의 발신 대기도 함께 지운다.
delete from notification_deliveries
where notification_id in (
    select id from (
        select id, row_number() over (partition by member_id, type, work_id, notified_at order by id) as duplicate_rank
        from notifications
        where work_id is not null
    ) ranked
    where duplicate_rank > 1
);

delete from notifications
where id in (
    select id from (
        select id, row_number() over (partition by member_id, type, work_id, notified_at order by id) as duplicate_rank
        from notifications
        where work_id is not null
    ) ranked
    where duplicate_rank > 1
);

alter table notifications
    add constraint uk_notifications_member_type_work_notified unique (member_id, type, work_id, notified_at);
