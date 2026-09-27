package com.orbit.schedule.application.port.in.query.dto;

import java.util.List;

import com.orbit.schedule.domain.ActualPaymentMethod;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.Money;

/**
 * 완료보고 내용. 사진은 업로드를 마친 파일의 식별자이며, 내려받을 주소로 바꾸는 방식은 업로드 방식과 함께 정한다(HM-238).
 *
 * @param actualFee 실제 받은 금액(원). 입력하지 않았으면 null
 */
public record CompletionReportInfo(
        List<String> beforePhotoIds,
        List<String> afterPhotoIds,
        String usedParts,
        String workNote,
        Long actualFee,
        ActualPaymentMethod actualPaymentMethod) {

    public CompletionReportInfo {
        beforePhotoIds = List.copyOf(beforePhotoIds);
        afterPhotoIds = List.copyOf(afterPhotoIds);
    }

    public static CompletionReportInfo from(CompletionReport report) {
        return new CompletionReportInfo(
                report.beforePhotos(),
                report.afterPhotos(),
                report.usedParts().orElse(null),
                report.workNote().orElse(null),
                report.actualFee().map(Money::won).orElse(null),
                report.actualPaymentMethod().orElse(null));
    }
}
