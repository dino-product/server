package com.orbit.schedule.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 작업 현장 주소(우편번호·기본주소·상세주소). 세 항목 모두 선택이며 빈 문자열·공백만 있는 값은 미입력(null)으로 정규화한다.
 *
 * <p>우편번호는 숫자 {@value #POSTAL_CODE_LENGTH}자리, 기본주소는 {@value #MAX_BASE_ADDRESS_LENGTH}자까지 받는다. 상세주소는
 * {@value #MAX_DETAIL_ADDRESS_LENGTH}자까지 받고, 기본주소가 있어야 받는다.
 *
 * <p>좌표({@link SiteCoordinates})는 기본주소에서 구한 값이라 기본주소가 있을 때만 담고, 입력값이 아니므로 비어 있어도 빈 주소 판정에 영향이 없다. 기본주소가
 * 같은 주소로 바꾸면 이전 좌표를 이어받는다({@link #keepingCoordinatesOf}).
 */
public final class SiteAddress {

    public static final int POSTAL_CODE_LENGTH = 5;
    public static final int MAX_BASE_ADDRESS_LENGTH = 200;
    public static final int MAX_DETAIL_ADDRESS_LENGTH = 100;

    private static final Pattern POSTAL_CODE = Pattern.compile("[0-9]{" + POSTAL_CODE_LENGTH + "}");

    private final String postalCode;
    private final String baseAddress;
    private final String detailAddress;
    private final SiteCoordinates coordinates;

    public SiteAddress(String postalCode, String baseAddress, String detailAddress) {
        this(postalCode, baseAddress, detailAddress, null);
    }

    public SiteAddress(String postalCode, String baseAddress, String detailAddress, SiteCoordinates coordinates) {
        this.postalCode = requirePostalCode(blankToNull(postalCode));
        this.baseAddress = requireAtMost(blankToNull(baseAddress), MAX_BASE_ADDRESS_LENGTH, "base address");
        this.detailAddress = requireAtMost(blankToNull(detailAddress), MAX_DETAIL_ADDRESS_LENGTH, "detail address");
        if (this.detailAddress != null && this.baseAddress == null) {
            throw new IllegalArgumentException("detail address requires base address");
        }
        if (coordinates != null && this.baseAddress == null) {
            throw new IllegalArgumentException("coordinates require base address");
        }
        this.coordinates = coordinates;
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

    public Optional<SiteCoordinates> coordinates() {
        return Optional.ofNullable(coordinates);
    }

    /** 세 항목이 모두 미입력인지. */
    public boolean isEmpty() {
        return postalCode == null && baseAddress == null && detailAddress == null;
    }

    /** 좌표를 아직 구하지 않은 기본주소가 있는지. */
    public boolean needsCoordinates() {
        return baseAddress != null && coordinates == null;
    }

    /** 같은 주소에 좌표를 담은 새 주소. 기본주소가 없으면 좌표를 담을 수 없다. */
    public SiteAddress withCoordinates(SiteCoordinates newCoordinates) {
        return new SiteAddress(postalCode, baseAddress, detailAddress, newCoordinates);
    }

    /** 좌표가 없고 기본주소가 이전 주소와 같으면 이전 좌표를 이어받은 새 주소, 그 밖에는 이 주소 그대로. */
    public SiteAddress keepingCoordinatesOf(SiteAddress previous) {
        if (coordinates != null
                || previous == null
                || previous.coordinates == null
                || !Objects.equals(baseAddress, previous.baseAddress)) {
            return this;
        }
        return withCoordinates(previous.coordinates);
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
                && Objects.equals(detailAddress, that.detailAddress)
                && Objects.equals(coordinates, that.coordinates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postalCode, baseAddress, detailAddress, coordinates);
    }

    @Override
    public String toString() {
        return "SiteAddress[postalCode=" + postalCode + ", baseAddress=" + baseAddress + ", detailAddress="
                + detailAddress + ", coordinates=" + coordinates + "]";
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
