package com.orbit.notification.application.port.out;

import java.time.Instant;
import java.util.List;

import com.orbit.notification.domain.NotificationDelivery;

/** 알림 발신 대기 저장소. 여러 인스턴스가 같은 대기열을 함께 처리해도 한 발신을 동시에 두 곳에서 집지 않는다. */
public interface NotificationDeliveryRepository {

    /**
     * 새 발신 대기를 부른 쪽 트랜잭션에 넣는다. 알림을 저장하는 트랜잭션에서 불러 알림과 발신 대기가 함께 커밋되게 한다({@link PushNotificationPort} 구현). 같은
     * 알림·채널의 대기는 하나뿐이다.
     */
    NotificationDelivery add(NotificationDelivery delivery);

    /**
     * {@code now}에 보낼 때가 된 발신(대기 중이거나 점유가 만료된 발신)을 다음 시도 시각이 이른 순으로 최대 {@code limit}건 집어 {@code leaseUntil}까지
     * 점유하고 커밋한다. 다른 인스턴스가 집는 중인 행은 기다리지 않고 건너뛴다. 부른 쪽 트랜잭션과 무관하게 따로 커밋해, 이어지는 외부 발신이 트랜잭션을 붙잡지 않게 한다.
     */
    List<NotificationDelivery> claimDue(Instant now, Instant leaseUntil, int limit);

    /**
     * 발신 결과를 따로 커밋한다. 집은 뒤 점유가 만료돼 다른 인스턴스가 다시 집었으면 저장하지 않고 {@code false}를 돌려준다.
     */
    boolean saveResult(NotificationDelivery delivery);
}
