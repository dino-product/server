package com.orbit.schedule.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 작업 현장의 좌표(WGS84 위도·경도). 기본주소에서 구한 값이라 길찾기용이며, 소수점 {@value #SCALE}자리(약 0.1m)로 반올림해 담는다. 위도는 -90~90, 경도는
 * -180~180 안이어야 한다.
 */
public record SiteCoordinates(BigDecimal latitude, BigDecimal longitude) {

    public static final int SCALE = 6;

    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    public SiteCoordinates {
        latitude = requireWithin(latitude, MAX_LATITUDE, "latitude");
        longitude = requireWithin(longitude, MAX_LONGITUDE, "longitude");
    }

    private static BigDecimal requireWithin(BigDecimal value, BigDecimal bound, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        BigDecimal scaled = value.setScale(SCALE, RoundingMode.HALF_UP);
        if (scaled.abs().compareTo(bound) > 0) {
            throw new IllegalArgumentException(field + " must be between -" + bound + " and " + bound);
        }
        return scaled;
    }
}
