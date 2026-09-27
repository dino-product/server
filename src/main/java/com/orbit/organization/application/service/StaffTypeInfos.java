package com.orbit.organization.application.service;

import com.orbit.organization.application.port.in.query.dto.StaffTypeInfo;
import com.orbit.organization.domain.StaffType;

final class StaffTypeInfos {
    private StaffTypeInfos() {}

    static StaffTypeInfo from(StaffType type) {
        return new StaffTypeInfo(
                type.id().value(), type.name().value(), type.color().value(), type.active());
    }
}
