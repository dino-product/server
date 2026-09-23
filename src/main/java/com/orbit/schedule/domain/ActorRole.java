package com.orbit.schedule.domain;

/** 작업 행위자의 조직 내 역할. organization 소속(Membership)의 역할을 schedule의 언어로 옮긴 것이며, 직원 유형은 권한에 반영하지 않는다. */
public enum ActorRole {
    OWNER,
    STAFF,
    TECHNICIAN
}
