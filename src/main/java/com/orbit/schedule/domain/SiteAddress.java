package com.orbit.schedule.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 작업 현장 주소(우편번호·기본주소·상세주소). 세 항목 모두 선택이며 빈 문자열·공백만 있는 값은 미입력(null)으로 정규화한다.
 *
 * <p>우편번호는 숫자 {@value #POSTAL_CODE_LENGTH}자리, 기본주소는 {@value #MAX_BASE_ADDRESS_LENGTH}자까지 받는다. 상세주소는
 * {@value #MAX_DETAIL_ADDRESS_LENGTH}자까지 받고, 기본주소가 있어야 받는다.
 */
public final class SiteAddress {

    public static final int POSTAL_CODE_LENGTH = 5;
    public static final int MAX_BASE_ADDRESS_LENGTH = 200;
    public static final int MAX_DETAIL_ADDRESS_LENGTH = 100;

    private static final Pattern POSTAL_CODE = Pattern.compile("[0-9]{" + POSTAL_CODE_LENGTH + "}");

    private final String postalCode;
    private final String baseAddress;
    private final String detailAddress;

    public SiteAddress(String postalCode, String baseAddress, String detailAddress) {
        this.postalCode = requirePostalCode(blankToNull(postalCode));
        this.baseAddress = requireAtMost(blankToNull(baseAddress), MAX_BASE_ADDRESS_LENGTH, "base address");
        this.detailAddress = requireAtMost(blankToNull(detailAddress), MAX_DETAIL_ADDRESS_LENGTH, "detail address");
        if (this.detailAddress != null && this.baseAddress == null) {
            throw new IllegalArgumentException("detail address requires base address");
        }
    }

    public Optional<String> postalCode() {
        return Optional.ofNullable(postalCode);
    }

    public Optional<String> baseAddress() {
        return Optional.ofNullable(baseAddress);
    }

    public Optional<String> detailAddress() {
        return Optional.ofNullable(detailAddress);
    }

    /** 세 항목이 모두 미입력인지. */
    public boolean isEmpty() {
        return postalCode == null && baseAddress == null && detailAddress == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SiteAddress that = (SiteAddress) o;
        return Objects.equals(postalCode, that.postalCode)
                && Objects.equals(baseAddress, that.baseAddress)
                && Objects.equals(detailAddress, that.detailAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postalCode, baseAddress, detailAddress);
    }

    @Override
    public String toString() {
        return "SiteAddress[postalCode=" + postalCode + ", baseAddress=" + baseAddress + ", detailAddress="
                + detailAddress + "]";
    }

    private static String requirePostalCode(String value) {
        if (value != null && !POSTAL_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("postal code must be " + POSTAL_CODE_LENGTH + " digits");
        }
        return value;
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
