package com.orbit.organization.adapter.in.web;

import com.orbit.organization.application.port.in.query.dto.OrganizationInfo;
import com.orbit.organization.domain.Industry;

import io.swagger.v3.oas.annotations.media.Schema;

public record OrganizationResponse(
        @Schema(description = "발주사 식별자", example = "1") Long organizationId,

        @Schema(description = "발주사명", example = "오르빗 설비") String name,

        @Schema(
                description = "업종. HVAC(냉난방 설비), ELECTRICAL_ELECTRONICS(전기·전자), PLUMBING(배관·설비), "
                        + "APPLIANCE_SERVICE(가전 A/S), FACILITY_MANAGEMENT(종합 시설관리), OTHER(기타)",
                example = "HVAC")
        Industry industry) {

    static OrganizationResponse from(OrganizationInfo organization) {
        return new OrganizationResponse(organization.organizationId(), organization.name(), organization.industry());
    }
}
