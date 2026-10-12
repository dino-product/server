package com.orbit.organization.domain;

/** 발주사 안에서 계정의 역할. 총관리자는 총관리자 표시가 있는 직원 소속이고, 기사는 기사 계약이다. */
public enum MemberRole {
    OWNER,
    STAFF,
    TECHNICIAN
}
