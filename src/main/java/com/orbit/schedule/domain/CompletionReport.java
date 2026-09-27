package com.orbit.schedule.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Work에 완전히 속하며 제출 후 수정되지 않는 완료보고. 사용부품·수행메모의 빈 문자열·공백은 미입력(null)으로 정규화한다. */
public final class CompletionReport {

    private static final int MAX_PHOTO_COUNT = 6;
    private static final int MAX_USED_PARTS_LENGTH = 255;
    private static final int MAX_WORK_NOTE_LENGTH = 255;

    private final List<String> beforePhotos;
    private final List<String> afterPhotos;
    private final String usedParts;
    private final String workNote;
    private final Money actualFee;
    private final ActualPaymentMethod actualPaymentMethod;

    public CompletionReport(
            List<String> beforePhotos,
            List<String> afterPhotos,
            String usedParts,
            String workNote,
            Money actualFee,
            ActualPaymentMethod actualPaymentMethod) {
        this.beforePhotos = copyPhotos(beforePhotos, "beforePhotos");
        this.afterPhotos = copyPhotos(afterPhotos, "afterPhotos");
        usedParts = blankToNull(usedParts);
        workNote = blankToNull(workNote);
        if (usedParts != null && usedParts.length() > MAX_USED_PARTS_LENGTH) {
            throw new IllegalArgumentException("usedParts must be at most " + MAX_USED_PARTS_LENGTH + " characters");
        }
        if (workNote != null && workNote.length() > MAX_WORK_NOTE_LENGTH) {
            throw new IllegalArgumentException("workNote must be at most " + MAX_WORK_NOTE_LENGTH + " characters");
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

    public Optional<Money> actualFee() {
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
                && Objects.equals(actualFee, that.actualFee)
                && Objects.equals(actualPaymentMethod, that.actualPaymentMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(beforePhotos, afterPhotos, usedParts, workNote, actualFee, actualPaymentMethod);
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static List<String> copyPhotos(List<String> photos, String fieldName) {
        if (photos == null) {
            return List.of();
        }
        if (photos.size() > MAX_PHOTO_COUNT) {
            throw new IllegalArgumentException(fieldName + " must have at most " + MAX_PHOTO_COUNT + " photos");
        }
        if (photos.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(fieldName + " must not contain null");
        }
        return List.copyOf(photos);
    }
}
