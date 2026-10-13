package com.orbit.organization.domain;

/** 참여 요청 상태. 명칭은 제안값이다(O-31). 승인 대기에서만 승인·거절·취소로 바뀌고, 그 뒤에는 바뀌지 않는다. */
public enum ParticipationRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELED
}
