package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.service.fake.FakeSiteCoordinatesPort;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.SiteCoordinates;
import com.orbit.schedule.domain.Work;

@DisplayName("트랜잭션 밖에서 미리 구한 현장 좌표")
class SiteLookupTest {

    private static final SiteCoordinates SEOUL =
            new SiteCoordinates(new BigDecimal("37.566535"), new BigDecimal("126.977969"));
    private static final SiteCoordinates BUSAN =
            new SiteCoordinates(new BigDecimal("35.179554"), new BigDecimal("129.075642"));

    private final FakeSiteCoordinatesPort port = new FakeSiteCoordinatesPort();

    @Test
    @DisplayName("요청의 기본주소로 좌표를 구하고, 비었거나 길이 제한을 넘는 주소는 조회하지 않는다")
    void looksUpOnlyStorableBaseAddress() {
        port.givenCoordinates("서울시", SEOUL);

        assertThat(SiteLookup.lookUp(port, "서울시")).isEqualTo(new SiteLookup("서울시", SEOUL));
        assertThat(SiteLookup.lookUp(port, "부산시")).isEqualTo(new SiteLookup("부산시", null));
        assertThat(SiteLookup.lookUp(port, null)).isEqualTo(SiteLookup.none());
        assertThat(SiteLookup.lookUp(port, " ")).isEqualTo(SiteLookup.none());
        assertThat(SiteLookup.lookUp(port, "가".repeat(SiteAddress.MAX_BASE_ADDRESS_LENGTH + 1)))
                .isEqualTo(SiteLookup.none());
        assertThat(port.lookups()).containsExactly("서울시", "부산시");
    }

    @Test
    @DisplayName("작업의 기본주소가 조회에 쓴 주소와 같고 좌표가 없을 때만 담는다")
    void appliesOnlyToSameUnlocatedBaseAddress() {
        Work same = workAt("서울시");
        Work other = workAt("부산시");
        Work located = workAt("서울시");
        located.locateSite(BUSAN);
        Work noAddress = workAt(null);

        for (Work work : new Work[] {same, other, located, noAddress}) {
            new SiteLookup("서울시", SEOUL).applyTo(work);
        }

        assertThat(coordinatesOf(same)).isEqualTo(SEOUL);
        assertThat(coordinatesOf(other)).isNull();
        assertThat(coordinatesOf(located)).isEqualTo(BUSAN);
        assertThat(noAddress.customerInfo().address()).isEmpty();
    }

    @Test
    @DisplayName("좌표를 구하지 못했으면 아무것도 담지 않는다")
    void appliesNothingWithoutCoordinates() {
        Work work = workAt("서울시");

        new SiteLookup("서울시", null).applyTo(work);
        SiteLookup.none().applyTo(work);

        assertThat(coordinatesOf(work)).isNull();
    }

    private static SiteCoordinates coordinatesOf(Work work) {
        return work.customerInfo().address().flatMap(SiteAddress::coordinates).orElse(null);
    }

    private static Work workAt(String baseAddress) {
        return Work.register(
                new OrganizationId(100L),
                "에어컨 수리",
                new MembershipId(1L),
                Instant.parse("2026-10-01T01:00:00Z"),
                null,
                new CustomerInfo("이정호", null, new SiteAddress(null, baseAddress, null)),
                null);
    }
}
