package com.orbit.schedule.domain;

import java.util.Objects;
import java.util.Optional;

/** 작업의 고객 정보(선택 항목). 이름·연락처·주소는 서로 독립적으로 비어 있을 수 있다. */
public final class CustomerInfo {

    private final String name;
    private final String phone;
    private final String address;

    public CustomerInfo(String name, String phone, String address) {
        this.name = name;
        this.phone = phone;
        this.address = address;
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
}
