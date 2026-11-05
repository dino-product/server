package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업의 현장 좌표")
class WorkSiteCoordinatesTest {

    private static final OrganizationId ORGANIZATION = new OrganizationId(100L);
    private static final MembershipId REGISTRAR = new MembershipId(1L);
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-01T01:00:00Z");
    private static final SiteCoordinates SEOUL =
            new SiteCoordinates(new BigDecimal("37.556151"), new BigDecimal("126.922803"));

    @Test
    @DisplayName("기본주소에서 구한 좌표를 현장 주소에 담고 고객 이름·연락처는 그대로 둔다")
    void locatesSite() {
        Work work = register(new CustomerInfo("이정호", "010-1234-5678", new SiteAddress("04043", "서울시", "3층")));

        work.locateSite(SEOUL);

        assertThat(work.customerInfo())
                .isEqualTo(new CustomerInfo("이정호", "010-1234-5678", new SiteAddress("04043", "서울시", "3층", SEOUL)));
    }

    @Test
    @DisplayName("기본주소가 없으면 좌표를 담을 수 없다")
    void rejectsLocatingWithoutBaseAddress() {
        Work noAddress = register(new CustomerInfo("이정호", null, null));
        Work postalCodeOnly = register(new CustomerInfo(null, null, new SiteAddress("04043", null, null)));

        assertThatThrownBy(() -> noAddress.locateSite(SEOUL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot locate a site without an address");
        assertThatThrownBy(() -> postalCodeOnly.locateSite(SEOUL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("coordinates require base address");
    }

    @Test
    @DisplayName("기본주소가 그대로인 기본정보 수정은 좌표를 이어받는다")
    void keepsCoordinatesWhenBaseAddressUnchanged() {
        Work work = located();

        work.changeDetails(
                "보일러 점검", null, new CustomerInfo("김철수", null, new SiteAddress(null, "서울시", "4층")), null, null);

        assertThat(work.customerInfo().address().flatMap(SiteAddress::coordinates))
                .contains(SEOUL);
    }

    @Test
    @DisplayName("기본주소를 바꾸거나 지우면 이전 좌표를 버린다")
    void dropsCoordinatesWhenBaseAddressChanges() {
        Work changed = located();
        Work cleared = located();

        changed.changeDetails(
                "보일러 점검", null, new CustomerInfo(null, null, new SiteAddress(null, "부산시", null)), null, null);
        cleared.changeDetails("보일러 점검", null, new CustomerInfo("이정호", null, null), null, null);

        assertThat(changed.customerInfo().address()).contains(new SiteAddress(null, "부산시", null));
        assertThat(cleared.customerInfo().address()).isEmpty();
    }

    private static Work located() {
        Work work = register(new CustomerInfo(null, null, new SiteAddress(null, "서울시", null)));
        work.locateSite(SEOUL);
        return work;
    }

    private static Work register(CustomerInfo customerInfo) {
        return Work.register(ORGANIZATION, "에어컨 수리", REGISTRAR, REGISTERED_AT, null, customerInfo, null);
    }
}
