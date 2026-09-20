package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("고객 정보")
class CustomerInfoTest {

    @Test
    @DisplayName("고객명과 연락처와 주소를 모두 포함해 생성할 수 있다")
    void createsWithAllValues() {
        CustomerInfo customerInfo = new CustomerInfo("홍길동", "010-1234-5678", "서울시 강남구");

        assertThat(customerInfo.name()).contains("홍길동");
        assertThat(customerInfo.phone()).contains("010-1234-5678");
        assertThat(customerInfo.address()).contains("서울시 강남구");
    }

    @Test
    @DisplayName("일부 고객 정보만 포함해 생성할 수 있다")
    void createsWithSomeValues() {
        CustomerInfo customerInfo = new CustomerInfo("홍길동", null, null);

        assertThat(customerInfo.name()).contains("홍길동");
        assertThat(customerInfo.phone()).isEmpty();
        assertThat(customerInfo.address()).isEmpty();
    }

    @Test
    @DisplayName("모든 고객 정보가 null이어도 생성할 수 있다")
    void createsWithAllNullValues() {
        CustomerInfo customerInfo = new CustomerInfo(null, null, null);

        assertThat(customerInfo.name()).isEmpty();
        assertThat(customerInfo.phone()).isEmpty();
        assertThat(customerInfo.address()).isEmpty();
    }
}
