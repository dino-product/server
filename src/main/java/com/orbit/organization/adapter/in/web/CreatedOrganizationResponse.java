package com.orbit.organization.adapter.in.web;

import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreatedOrganizationResponse(
        @Schema(description = "발주사 식별자", example = "1") Long organizationId,

        @Schema(description = "회사 코드. 참여 링크·QR의 근거", example = "7K2M9X")
        String companyCode) {

    static CreatedOrganizationResponse from(CreatedOrganizationInfo organization) {
        return new CreatedOrganizationResponse(organization.organizationId(), organization.companyCode());
    }
}
