package com.orbit.schedule.application.port.in.query.dto;

import java.math.BigDecimal;

/** 현장 좌표(WGS84 위도·경도, 소수점 6자리). 현장 기본주소에서 구한 길찾기용 값이다. */
public record LocationInfo(BigDecimal latitude, BigDecimal longitude) {}
