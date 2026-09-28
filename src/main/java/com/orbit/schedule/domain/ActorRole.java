package com.orbit.schedule.domain;

/**
 * 관리자의 조직 내 역할. organization이 정한 총관리자(조직이 가리키는 직원 소속)와 직원을 schedule의 언어로 옮긴 것이며, 직원 유형은 권한에 반영하지 않는다. 기사는 조직 소속이
 * 아니라 기사 계약으로 조직과 연결되므로 역할 대신 {@link TechnicianActor}로 구분한다.
 */
public enum ActorRole {
    OWNER,
    STAFF
}
