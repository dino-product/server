package com.orbit.organization;

/** 발주사의 활성 구성원. 직원 소속({@link StaffMember})과 기사 계약({@link TechnicianMember})은 서로 다른 ID를 쓴다. */
public sealed interface OrganizationMember permits StaffMember, TechnicianMember {

    Long organizationId();
}
