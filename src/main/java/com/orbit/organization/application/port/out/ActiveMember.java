package com.orbit.organization.application.port.out;

/** 활성 직원 소속 또는 활성 기사 계약의 조회 결과. */
public sealed interface ActiveMember permits ActiveMembership, ActiveTechnicianContract {}
