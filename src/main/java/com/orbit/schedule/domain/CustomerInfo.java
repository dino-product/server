package com.orbit.schedule.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * 작업의 고객 정보(선택 항목). 이름·연락처·주소는 서로 독립적으로 비어 있을 수 있다. 빈 문자열·공백만 있는 값은 미입력(null)으로
 * 정규화한다. 이름은 {@value #MAX_NAME_LENGTH}자, 연락처는 {@value #MAX_PHONE_LENGTH}자, 주소는 {@value #MAX_ADDRESS_LENGTH}자까지 받는다.
 */
public final class CustomerInfo {

    public static final int MAX_NAME_LENGTH = 50;
    public static final int MAX_PHONE_LENGTH = 20;
    public static final int MAX_ADDRESS_LENGTH = 200;

    private final String name;
    private final String phone;
    private final String address;

    public CustomerInfo(String name, String phone, String address) {
        this.name = requireAtMost(blankToNull(name), MAX_NAME_LENGTH, "customer name");
        this.phone = requireAtMost(blankToNull(phone), MAX_PHONE_LENGTH, "customer phone");
        this.address = requireAtMost(blankToNull(address), MAX_ADDRESS_LENGTH, "customer address");
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<String> phone() {
        return Optional.ofNullable(phone);
    }

    public Optional<String> address() {
        return Optional.ofNullable(address);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CustomerInfo that = (CustomerInfo) o;
        return Objects.equals(name, that.name)
                && Objects.equals(phone, that.phone)
                && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, phone, address);
    }

    @Override
    public String toString() {
        return "CustomerInfo[name=" + name + ", phone=" + phone + ", address=" + address + "]";
    }

    private static String requireAtMost(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
