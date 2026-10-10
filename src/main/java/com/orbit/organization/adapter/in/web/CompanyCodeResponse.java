package com.orbit.organization.adapter.in.web;

import com.orbit.organization.application.port.in.query.dto.CompanyCodeInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record CompanyCodeResponse(
        @Schema(description = "현재 회사 코드. 참여 링크·QR은 클라이언트가 이 코드로 만든다", example = "7K2M9X")
        String companyCode) {

    static CompanyCodeResponse from(CompanyCodeInfo companyCode) {
        return new CompanyCodeResponse(companyCode.companyCode());
    }
}
