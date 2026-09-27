package com.orbit.schedule.domain;

/** 기사 배정 시도의 결과. 관리자 조치로 배정이 어떻게 끝났는지는 {@link AssignmentEnding}이 따로 남긴다. */
public enum AssignmentResult {
    /** 기사의 응답을 기다린다. */
    PENDING,
    /** 기사가 수락했다. 이후 관리자 조치로 끝나도 수락 결과는 그대로다. */
    ACCEPTED,
    /** 기사가 거절했다. */
    REJECTED,
    /** 기사가 응답하기 전에 관리자가 재배정·일정 변경·해제·취소로 회수했다. 회수 방식은 종료 기록이 알려준다. */
    WITHDRAWN
}
