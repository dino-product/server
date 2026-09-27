package com.orbit.schedule.domain;

/** 배정 수락·거절, 작업 시작, 완료보고를 하는 기사. 요청한 조직과 맺은 기사 계약이다. 그 작업의 담당 기사인지는 작업마다 따로 확인한다. */
public record TechnicianActor(TechnicianId technicianId, OrganizationId organizationId) implements Actor {

    public TechnicianActor {
        if (technicianId == null) {
            throw new IllegalArgumentException("technicianId must not be null");
        }
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId must not be null");
        }
    }
}
