package com.orbit.organization.application.port.in.command.dto;

/** 일괄 승인·거절에서 요청 한 건의 처리 결과. */
public enum ParticipationRequestDecisionOutcome {
    APPROVED,
    REJECTED,
    /** 없는 요청이거나 다른 발주사의 요청. 존재 여부를 드러내지 않으려고 구분하지 않는다. */
    NOT_FOUND,
    /** 이미 승인·거절·취소된 요청. 현재 상태를 함께 준다. */
    NOT_PENDING,
    /** 신청자에게 그 발주사의 활성 직원 소속이나 기사 계약이 있다. 요청은 승인 대기로 남는다. */
    ALREADY_MEMBER
}
