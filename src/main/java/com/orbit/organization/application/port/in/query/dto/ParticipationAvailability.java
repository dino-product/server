package com.orbit.organization.application.port.in.query.dto;

/** 회사 코드로 확인한 발주사에 요청자가 지금 참여 요청을 보낼 수 있는지. */
public enum ParticipationAvailability {
    /** 참여 요청을 보낼 수 있다. */
    AVAILABLE,
    /** 활성 직원 소속이나 기사 계약이 있다. */
    ALREADY_MEMBER,
    /** 비활성 직원 소속·기사 계약만 있다. 승인의 재활성화(HM-290) 전까지 요청을 막는다(O-03). */
    DEACTIVATED_MEMBER,
    /** 승인 대기 중인 요청이 있다. */
    PENDING_REQUEST
}
