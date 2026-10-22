package com.orbit.notification.domain;

/** 알림 발신 대기의 상태. {@link #SENT}·{@link #FAILED}·{@link #SKIPPED}는 끝난 상태로 다시 보내지 않는다. */
public enum DeliveryStatus {
    /** 다음 시도 시각이 되면 보낸다. 다시 보낼 실패도 이 상태로 돌아온다. */
    PENDING,
    /** 한 인스턴스가 집어 보내는 중이다. 점유가 만료될 때까지 결과가 없으면 다시 집는다. */
    IN_FLIGHT,
    SENT,
    /** 다시 보내도 성공할 수 없는 실패(요청 형식 오류 등). */
    FAILED,
    /** 받을 기기가 없어 보내지 않았다. 알림센터의 알림은 그대로 남는다. */
    SKIPPED
}
