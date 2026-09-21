package com.orbit.schedule.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Work에 완전히 속하며 제출 후 수정되지 않는 완료보고. */
public final class CompletionReport {

    private static final int MAX_PHOTO_COUNT = 6;
    private static final int MAX_USED_PARTS_LENGTH = 255;
    private static final int MAX_WORK_NOTE_LENGTH = 255;

    private final List<String> beforePhotos;
    private final List<String> afterPhotos;
    private final String usedParts;
    private final String workNote;
    private final BigDecimal actualFee;
    private final ActualPaymentMethod actualPaymentMethod;

    public CompletionReport(
            List<String> beforePhotos,
            List<String> afterPhotos,
            String usedParts,
            String workNote,
            BigDecimal actualFee,
            ActualPaymentMethod actualPaymentMethod) {
        this.beforePhotos = copyPhotos(beforePhotos, "beforePhotos");
        this.afterPhotos = copyPhotos(afterPhotos, "afterPhotos");
        if (usedParts != null && usedParts.length() > MAX_USED_PARTS_LENGTH) {
            throw new IllegalArgumentException("usedParts must be at most 255 characters");
        }
        if (workNote != null && workNote.length() > MAX_WORK_NOTE_LENGTH) {
            throw new IllegalArgumentException("workNote must be at most 255 characters");
        }
        if (actualFee != null && actualFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("actualFee must not be negative");
        }
        this.usedParts = usedParts;
        this.workNote = workNote;
        this.actualFee = actualFee;
        this.actualPaymentMethod = actualPaymentMethod;
    }

    public List<String> beforePhotos() {
        return beforePhotos;
    }

    public List<String> afterPhotos() {
        return afterPhotos;
    }

    public Optional<String> usedParts() {
        return Optional.ofNullable(usedParts);
    }

    public Optional<String> workNote() {
        return Optional.ofNullable(workNote);
    }

    public Optional<BigDecimal> actualFee() {
        return Optional.ofNullable(actualFee);
    }

    public Optional<ActualPaymentMethod> actualPaymentMethod() {
        return Optional.ofNullable(actualPaymentMethod);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompletionReport that = (CompletionReport) o;
        return Objects.equals(beforePhotos, that.beforePhotos)
                && Objects.equals(afterPhotos, that.afterPhotos)
                && Objects.equals(usedParts, that.usedParts)
                && Objects.equals(workNote, that.workNote)
                && actualFeeEquals(actualFee, that.actualFee)
                && Objects.equals(actualPaymentMethod, that.actualPaymentMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                beforePhotos,
                afterPhotos,
                usedParts,
                workNote,
                actualFee == null ? null : actualFee.stripTrailingZeros(),
                actualPaymentMethod);
    }

    // BigDecimal.equals는 scale까지 비교해 150000과 150000.00을 다르다고 판단하므로,
    // 생성자 검증과 같은 compareTo 기준(값 동등성)으로 맞춘다.
    private static boolean actualFeeEquals(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    @Override
    public String toString() {
        return "CompletionReport[beforePhotos="
                + beforePhotos
                + ", afterPhotos="
                + afterPhotos
                + ", usedParts="
                + usedParts
                + ", workNote="
                + workNote
                + ", actualFee="
                + actualFee
                + ", actualPaymentMethod="
                + actualPaymentMethod
                + "]";
    }

    private static List<String> copyPhotos(List<String> photos, String fieldName) {
        if (photos == null) {
            return List.of();
        }
        if (photos.size() > MAX_PHOTO_COUNT) {
            throw new IllegalArgumentException(fieldName + " must have at most 6 photos");
        }
        return List.copyOf(photos);
    }
}
