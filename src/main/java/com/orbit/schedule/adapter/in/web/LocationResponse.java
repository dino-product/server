package com.orbit.schedule.adapter.in.web;

import java.math.BigDecimal;

import com.orbit.schedule.application.port.in.query.dto.LocationInfo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현장 좌표(WGS84). 현장 기본주소에서 서버가 구한 길찾기용 값")
public record LocationResponse(
        @Schema(description = "위도(소수점 6자리)", example = "37.556151")
        BigDecimal latitude,

        @Schema(description = "경도(소수점 6자리)", example = "126.922803")
        BigDecimal longitude) {

    public static LocationResponse from(LocationInfo info) {
        return info == null ? null : new LocationResponse(info.latitude(), info.longitude());
    }
}
