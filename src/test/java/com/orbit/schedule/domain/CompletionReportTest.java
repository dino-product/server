package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("완료보고")
class CompletionReportTest {

    @Test
    @DisplayName("모든 수행 정보를 포함해 제출할 수 있다")
    void createsWithAllValues() {
        List<String> beforePhotos = List.of("before-1.jpg", "before-2.jpg");
        List<String> afterPhotos = List.of("after-1.jpg", "after-2.jpg");
        BigDecimal actualFee = new BigDecimal("150000");

        CompletionReport report = new CompletionReport(
                beforePhotos, afterPhotos, "필터 1개", "필터 교체 완료", actualFee, ActualPaymentMethod.CREDIT_CARD);

        assertThat(report.beforePhotos()).containsExactlyElementsOf(beforePhotos);
        assertThat(report.afterPhotos()).containsExactlyElementsOf(afterPhotos);
        assertThat(report.usedParts()).contains("필터 1개");
        assertThat(report.workNote()).contains("필터 교체 완료");
        assertThat(report.actualFee()).contains(actualFee);
        assertThat(report.actualPaymentMethod()).contains(ActualPaymentMethod.CREDIT_CARD);
    }

    @Test
    @DisplayName("모든 수행 정보가 null이어도 제출할 수 있다")
    void createsWithAllNullValues() {
        CompletionReport report = new CompletionReport(null, null, null, null, null, null);

        assertThat(report.beforePhotos()).isEmpty();
        assertThat(report.afterPhotos()).isEmpty();
        assertThat(report.usedParts()).isEmpty();
        assertThat(report.workNote()).isEmpty();
        assertThat(report.actualFee()).isEmpty();
        assertThat(report.actualPaymentMethod()).isEmpty();
    }

    @Test
    @DisplayName("작업 전 사진이 7장이면 거부한다")
    void rejectsMoreThanSixBeforePhotos() {
        List<String> beforePhotos = List.of("1", "2", "3", "4", "5", "6", "7");

        assertThatThrownBy(() -> new CompletionReport(beforePhotos, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("beforePhotos must have at most 6 photos");
    }

    @Test
    @DisplayName("작업 후 사진이 7장이면 거부한다")
    void rejectsMoreThanSixAfterPhotos() {
        List<String> afterPhotos = List.of("1", "2", "3", "4", "5", "6", "7");

        assertThatThrownBy(() -> new CompletionReport(null, afterPhotos, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("afterPhotos must have at most 6 photos");
    }

    @Test
    @DisplayName("작업 전 사진 목록에 null이 있으면 거부한다")
    void rejectsNullBeforePhoto() {
        List<String> beforePhotos = Arrays.asList("before.jpg", null);

        assertThatThrownBy(() -> new CompletionReport(beforePhotos, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("beforePhotos must not contain null");
    }

    @Test
    @DisplayName("작업 후 사진 목록에 null이 있으면 거부한다")
    void rejectsNullAfterPhoto() {
        List<String> afterPhotos = Arrays.asList(null, "after.jpg");

        assertThatThrownBy(() -> new CompletionReport(null, afterPhotos, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("afterPhotos must not contain null");
    }

    @Test
    @DisplayName("수행메모가 256자이면 거부한다")
    void rejectsWorkNoteLongerThan255Characters() {
        String workNote = "a".repeat(256);

        assertThatThrownBy(() -> new CompletionReport(null, null, null, workNote, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workNote must be at most 255 characters");
    }

    @Test
    @DisplayName("사용부품이 255자이면 허용한다")
    void acceptsUsedPartsOf255Characters() {
        String usedParts = "a".repeat(255);

        CompletionReport report = new CompletionReport(null, null, usedParts, null, null, null);

        assertThat(report.usedParts()).contains(usedParts);
    }

    @Test
    @DisplayName("사용부품이 256자이면 거부한다")
    void rejectsUsedPartsLongerThan255Characters() {
        String usedParts = "a".repeat(256);

        assertThatThrownBy(() -> new CompletionReport(null, null, usedParts, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("usedParts must be at most 255 characters");
    }

    @Test
    @DisplayName("결제금액이 음수이면 거부한다")
    void rejectsNegativeActualFee() {
        assertThatThrownBy(() -> new CompletionReport(null, null, null, null, new BigDecimal("-0.01"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("actualFee must not be negative");
    }

    @Test
    @DisplayName("제출된 작업 전 사진 목록은 수정할 수 없다")
    void beforePhotosAreImmutable() {
        CompletionReport report = new CompletionReport(List.of("before.jpg"), null, null, null, null, null);

        assertThatThrownBy(() -> report.beforePhotos().add("other.jpg"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("제출 후 원본 목록을 수정해도 작업 전 사진은 바뀌지 않는다")
    void defensivelyCopiesBeforePhotos() {
        List<String> beforePhotos = new ArrayList<>(List.of("before.jpg"));
        CompletionReport report = new CompletionReport(beforePhotos, null, null, null, null, null);

        beforePhotos.add("other.jpg");

        assertThat(report.beforePhotos()).containsExactly("before.jpg");
    }

    @Test
    @DisplayName("같은 완료보고는 동등하다")
    void hasValueEquality() {
        CompletionReport report = new CompletionReport(
                List.of("before.jpg"),
                List.of("after.jpg"),
                "필터 1개",
                "필터 교체 완료",
                new BigDecimal("150000"),
                ActualPaymentMethod.BANK_TRANSFER);
        CompletionReport sameReport = new CompletionReport(
                List.of("before.jpg"),
                List.of("after.jpg"),
                "필터 1개",
                "필터 교체 완료",
                new BigDecimal("150000"),
                ActualPaymentMethod.BANK_TRANSFER);

        assertThat(report).isEqualTo(sameReport);
        assertThat(report.hashCode()).isEqualTo(sameReport.hashCode());
    }

    @Test
    @DisplayName("결제금액의 scale이 달라도 값이 같으면 동등하다")
    void isEqualWhenActualFeeScaleDiffers() {
        CompletionReport report = new CompletionReport(
                List.of("before.jpg"),
                List.of("after.jpg"),
                "필터 1개",
                "필터 교체 완료",
                new BigDecimal("150000"),
                ActualPaymentMethod.BANK_TRANSFER);
        CompletionReport sameValueDifferentScale = new CompletionReport(
                List.of("before.jpg"),
                List.of("after.jpg"),
                "필터 1개",
                "필터 교체 완료",
                new BigDecimal("150000.00"),
                ActualPaymentMethod.BANK_TRANSFER);

        assertThat(report).isEqualTo(sameValueDifferentScale);
        assertThat(report.hashCode()).isEqualTo(sameValueDifferentScale.hashCode());
    }
}
