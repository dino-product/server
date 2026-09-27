package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("고객 정보")
class CustomerInfoTest {

    @Test
    @DisplayName("같은 고객 정보는 동등하다")
    void hasValueEquality() {
        CustomerInfo customerInfo = new CustomerInfo("홍길동", "010-1234-5678", "서울시 강남구");
        CustomerInfo sameCustomerInfo = new CustomerInfo("홍길동", "010-1234-5678", "서울시 강남구");

        assertThat(customerInfo).isEqualTo(sameCustomerInfo);
        assertThat(customerInfo.hashCode()).isEqualTo(sameCustomerInfo.hashCode());
    }

    @Test
    @DisplayName("하나라도 다른 고객 정보는 동등하지 않다")
    void isNotEqualWhenAnyValueDiffers() {
        CustomerInfo customerInfo = new CustomerInfo("홍길동", "010-1234-5678", "서울시 강남구");

        assertThat(customerInfo)
                .isNotEqualTo(new CustomerInfo("김철수", "010-1234-5678", "서울시 강남구"))
                .isNotEqualTo(new CustomerInfo("홍길동", "010-9876-5432", "서울시 강남구"))
                .isNotEqualTo(new CustomerInfo("홍길동", "010-1234-5678", "서울시 종로구"));
    }

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

    @Test
    @DisplayName("빈 문자열과 공백만 있는 값은 미입력으로 취급한다")
    void treatsBlankValuesAsAbsent() {
        CustomerInfo customerInfo = new CustomerInfo("", "   ", "\t");

        assertThat(customerInfo.name()).isEmpty();
        assertThat(customerInfo.phone()).isEmpty();
        assertThat(customerInfo.address()).isEmpty();
    }

    @Test
    @DisplayName("공백 값으로 만든 고객 정보는 미입력 고객 정보와 동등하다")
    void blankValuesAreEqualToAbsentValues() {
        CustomerInfo blank = new CustomerInfo("", " ", "");
        CustomerInfo absent = new CustomerInfo(null, null, null);

        assertThat(blank).isEqualTo(absent);
        assertThat(blank.hashCode()).isEqualTo(absent.hashCode());
    }

    @Test
    @DisplayName("이름 50자·연락처 20자·주소 200자까지 받는다")
    void acceptsValuesUpToLimits() {
        CustomerInfo info = new CustomerInfo("가".repeat(50), "0".repeat(20), "가".repeat(200));

        assertThat(info.name()).hasValueSatisfying(name -> assertThat(name).hasSize(50));
        assertThat(info.phone()).hasValueSatisfying(phone -> assertThat(phone).hasSize(20));
        assertThat(info.address())
                .hasValueSatisfying(address -> assertThat(address).hasSize(200));
    }

    @Test
    @DisplayName("상한을 넘는 이름·연락처·주소는 거부한다")
    void rejectsValuesOverLimits() {
        assertThatThrownBy(() -> new CustomerInfo("가".repeat(51), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customer name must be at most 50 characters");
        assertThatThrownBy(() -> new CustomerInfo(null, "0".repeat(21), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customer phone must be at most 20 characters");
        assertThatThrownBy(() -> new CustomerInfo(null, null, "가".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customer address must be at most 200 characters");
    }
}
