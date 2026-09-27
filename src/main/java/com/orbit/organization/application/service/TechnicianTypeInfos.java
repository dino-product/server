package com.orbit.organization.application.service;

import com.orbit.organization.application.port.in.query.dto.TechnicianTypeInfo;
import com.orbit.organization.domain.TechnicianType;

final class TechnicianTypeInfos {
    private TechnicianTypeInfos() {}

    static TechnicianTypeInfo from(TechnicianType type) {
        return new TechnicianTypeInfo(
                type.id().value(), type.name().value(), type.color().value(), type.active());
    }
}
