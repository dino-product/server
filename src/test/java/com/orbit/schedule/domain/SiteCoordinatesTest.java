package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("현장 좌표")
class SiteCoordinatesTest {

    @Test
    @DisplayName("위도·경도를 소수점 6자리로 반올림해 담고, 자릿수 표기가 달라도 같은 값이면 동등하다")
    void roundsToSixDecimalPlaces() {
        SiteCoordinates coordinates =
                new SiteCoordinates(new BigDecimal("37.5561519999"), new BigDecimal("126.92280349"));

        assertThat(coordinates.latitude()).isEqualTo(new BigDecimal("37.556152"));
        assertThat(coordinates.longitude()).isEqualTo(new BigDecimal("126.922803"));
        assertThat(new SiteCoordinates(new BigDecimal("37.5"), new BigDecimal("127")))
                .isEqualTo(new SiteCoordinates(new BigDecimal("37.500000"), new BigDecimal("127.000000")));
    }

    @Test
    @DisplayName("위도 ±90, 경도 ±180 경계값까지 받는다")
    void acceptsBoundaryValues() {
        SiteCoordinates max = new SiteCoordinates(new BigDecimal("90"), new BigDecimal("180"));
        SiteCoordinates min = new SiteCoordinates(new BigDecimal("-90"), new BigDecimal("-180"));

        assertThat(max.latitude()).isEqualTo(new BigDecimal("90.000000"));
        assertThat(min.longitude()).isEqualTo(new BigDecimal("-180.000000"));
    }

    @ParameterizedTest(name = "위도 {0}, 경도 {1}")
    @CsvSource({
        "90.000001, 127, latitude",
        "-90.000001, 127, latitude",
        "37, 180.000001, longitude",
        "37, -180.000001, longitude"
    })
    @DisplayName("범위를 벗어난 위도·경도는 거부한다")
    void rejectsOutOfRange(String latitude, String longitude, String field) {
        assertThatThrownBy(() -> new SiteCoordinates(new BigDecimal(latitude), new BigDecimal(longitude)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(field + " must be between");
    }

    @Test
    @DisplayName("위도·경도는 둘 다 있어야 한다")
    void requiresBothValues() {
        assertThatThrownBy(() -> new SiteCoordinates(null, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("latitude must not be null");
        assertThatThrownBy(() -> new SiteCoordinates(BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("longitude must not be null");
    }
}
