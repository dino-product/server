package com.orbit.schedule.application.port.out;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;

/** 계정의 기사 계약 하나. 기사 계약 ID와 그 계약을 맺은 조직, 지금 활성인지를 담는다. */
public record AccountTechnicianContract(TechnicianId technicianId, OrganizationId organizationId, boolean active) {

    public AccountTechnicianContract {
        if (technicianId == null) {
            throw new IllegalArgumentException("technicianId must not be null");
        }
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId must not be null");
        }
    }
}
