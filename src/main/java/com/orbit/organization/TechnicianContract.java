package com.orbit.organization;

/** 계정의 기사 계약 하나. {@code technicianId}는 역할 변경으로 끝났다가 다시 활성이 되어도 바뀌지 않는다. */
public record TechnicianContract(Long technicianId, Long organizationId, boolean active) {}
