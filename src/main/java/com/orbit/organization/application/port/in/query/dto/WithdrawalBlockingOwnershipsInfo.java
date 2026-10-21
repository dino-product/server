package com.orbit.organization.application.port.in.query.dto;

/** 계정이 유일한 활성 총관리자인 발주사 수. 0이면 총관리자 때문에 탈퇴가 막히지 않는다. */
public record WithdrawalBlockingOwnershipsInfo(int soleOwnerOrganizationCount) {}
