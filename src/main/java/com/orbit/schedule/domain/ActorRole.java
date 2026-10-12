package com.orbit.schedule.domain;

/**
 * 관리자의 조직 내 역할. organization의 직원 소속에 붙은 총관리자 표시(한 조직에 여러 명일 수 있음)를 schedule의 언어로 옮긴 것이며, 직원 유형은 권한에 반영하지
 * 않는다. 기사는 조직 소속이 아니라 기사 계약으로 조직과 연결되므로 역할 대신 {@link TechnicianActor}로 구분한다.
 */
public enum ActorRole {
    OWNER,
    STAFF
}
