package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("현장 주소")
class SiteAddressTest {

    private static final SiteCoordinates SEOUL =
            new SiteCoordinates(new BigDecimal("37.556151"), new BigDecimal("126.922803"));

    @Test
    @DisplayName("우편번호·기본주소·상세주소를 모두 담고 같은 값이면 동등하다")
    void holdsAllPartsWithValueEquality() {
        SiteAddress address = new SiteAddress("04043", "서울시 마포구 양화로 45", "3층 302호");

        assertThat(address.postalCode()).contains("04043");
        assertThat(address.baseAddress()).contains("서울시 마포구 양화로 45");
        assertThat(address.detailAddress()).contains("3층 302호");
        assertThat(address).isEqualTo(new SiteAddress("04043", "서울시 마포구 양화로 45", "3층 302호"));
        assertThat(address.hashCode()).isEqualTo(new SiteAddress("04043", "서울시 마포구 양화로 45", "3층 302호").hashCode());
        assertThat(address)
                .isNotEqualTo(new SiteAddress("04044", "서울시 마포구 양화로 45", "3층 302호"))
                .isNotEqualTo(new SiteAddress("04043", "서울시 마포구 양화로 46", "3층 302호"))
                .isNotEqualTo(new SiteAddress("04043", "서울시 마포구 양화로 45", "3층 303호"));
    }

    @Test
    @DisplayName("우편번호만, 기본주소만 따로 담을 수 있다")
    void acceptsPartsIndependentlyExceptDetail() {
        assertThat(new SiteAddress("04043", null, null).baseAddress()).isEmpty();
        assertThat(new SiteAddress(null, "서울시", null).postalCode()).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열과 공백만 있는 값은 미입력이고, 모두 비면 빈 주소다")
    void treatsBlankPartsAsAbsent() {
        SiteAddress blank = new SiteAddress("", " ", "\t");

        assertThat(blank.isEmpty()).isTrue();
        assertThat(blank).isEqualTo(new SiteAddress(null, null, null));
        assertThat(new SiteAddress("04043", null, null).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("기본주소 200자·상세주소 100자까지 받는다")
    void acceptsAddressesUpToLimits() {
        SiteAddress address = new SiteAddress(null, "가".repeat(200), "나".repeat(100));

        assertThat(address.baseAddress())
                .hasValueSatisfying(base -> assertThat(base).hasSize(200));
        assertThat(address.detailAddress())
                .hasValueSatisfying(detail -> assertThat(detail).hasSize(100));
    }

    @Test
    @DisplayName("상한을 넘는 기본주소·상세주소는 거부한다")
    void rejectsAddressesOverLimits() {
        assertThatThrownBy(() -> new SiteAddress(null, "가".repeat(201), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("base address must be at most 200 characters");
        assertThatThrownBy(() -> new SiteAddress(null, "서울시", "나".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("detail address must be at most 100 characters");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0404", "040431", "04O43", "０４０４３", " 04043"})
    @DisplayName("우편번호는 숫자 5자리만 받는다")
    void rejectsMalformedPostalCode(String postalCode) {
        assertThatThrownBy(() -> new SiteAddress(postalCode, "서울시", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("postal code must be 5 digits");
    }

    @Test
    @DisplayName("기본주소 없이 상세주소만 줄 수 없다")
    void rejectsDetailWithoutBase() {
        assertThatThrownBy(() -> new SiteAddress("04043", null, "3층 302호"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("detail address requires base address");
        assertThatThrownBy(() -> new SiteAddress(null, " ", "3층 302호"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("detail address requires base address");
    }

    @Test
    @DisplayName("좌표는 기본주소가 있을 때만 담고, 같은 주소라도 좌표가 다르면 다른 값이다")
    void holdsCoordinatesOnlyWithBaseAddress() {
        SiteAddress located = new SiteAddress("04043", "서울시 마포구 양화로 45", null, SEOUL);

        assertThat(located.coordinates()).contains(SEOUL);
        assertThat(located).isNotEqualTo(new SiteAddress("04043", "서울시 마포구 양화로 45", null));
        assertThat(located).isEqualTo(new SiteAddress("04043", "서울시 마포구 양화로 45", null).withCoordinates(SEOUL));
        assertThatThrownBy(() -> new SiteAddress("04043", " ", null, SEOUL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("coordinates require base address");
    }

    @Test
    @DisplayName("기본주소가 있는데 좌표가 없을 때만 좌표를 구해야 한다")
    void needsCoordinatesOnlyForUnlocatedBaseAddress() {
        assertThat(new SiteAddress(null, "서울시", null).needsCoordinates()).isTrue();
        assertThat(new SiteAddress(null, "서울시", null, SEOUL).needsCoordinates()).isFalse();
        assertThat(new SiteAddress("04043", null, null).needsCoordinates()).isFalse();
    }

    @Test
    @DisplayName("기본주소가 같으면 이전 좌표를 이어받고, 기본주소가 바뀌거나 이전 좌표가 없으면 이어받지 않는다")
    void keepsCoordinatesOnlyForSameBaseAddress() {
        SiteAddress previous = new SiteAddress("04043", "서울시", "3층", SEOUL);

        assertThat(new SiteAddress("04044", "서울시", "4층").keepingCoordinatesOf(previous))
                .isEqualTo(new SiteAddress("04044", "서울시", "4층", SEOUL));
        assertThat(new SiteAddress(null, "부산시", null).keepingCoordinatesOf(previous))
                .isEqualTo(new SiteAddress(null, "부산시", null));
        assertThat(new SiteAddress(null, "서울시", null).keepingCoordinatesOf(new SiteAddress(null, "서울시", null)))
                .isEqualTo(new SiteAddress(null, "서울시", null));
        assertThat(new SiteAddress(null, "서울시", null).keepingCoordinatesOf(null))
                .isEqualTo(new SiteAddress(null, "서울시", null));
    }

    @Test
    @DisplayName("이미 좌표가 있으면 이전 좌표로 덮지 않는다")
    void keepsOwnCoordinates() {
        SiteCoordinates busan = new SiteCoordinates(new BigDecimal("35.1"), new BigDecimal("129.0"));
        SiteAddress own = new SiteAddress(null, "서울시", null, busan);

        assertThat(own.keepingCoordinatesOf(new SiteAddress(null, "서울시", null, SEOUL)))
                .isEqualTo(own);
    }
}
