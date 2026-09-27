package com.orbit.schedule.domain;

/**
 * 관리자의 조직 내 역할. organization 소속(Membership)의 역할을 schedule의 언어로 옮긴 것이며, 직원 유형은 권한에 반영하지 않는다. 기사는 조직 소속이
 * 없는 회사별 기사 관계이므로 역할 대신 {@link TechnicianActor}로 구분한다.
 */
public enum ActorRole {
    OWNER,
    STAFF
}
