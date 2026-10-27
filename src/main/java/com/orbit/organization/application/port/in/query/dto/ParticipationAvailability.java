package com.orbit.organization.application.port.in.query.dto;

/** 회사 코드로 확인한 발주사에 요청자가 지금 참여 요청을 보낼 수 있는지. */
public enum ParticipationAvailability {
    /** 참여 요청을 보낼 수 있다. 비활성 소속만 있는 회원도 다시 요청할 수 있고, 승인되면 그 소속이 되살아난다([조직·계정] §6). */
    AVAILABLE,
    /** 활성 직원 소속이나 기사 계약이 있다. */
    ALREADY_MEMBER,
    /** 승인 대기 중인 요청이 있다. */
    PENDING_REQUEST
}
