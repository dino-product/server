package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.PaymentMethod;

/**
 * 작업내역 출력 결과. 요청한 정렬 순서의 작업 전체다.
 *
 * @param exportedAt 출력 시각
 */
public record WorkExportInfo(List<Row> rows, Instant exportedAt) {

    public WorkExportInfo {
        rows = List.copyOf(rows);
    }

    /**
     * 출력할 작업 한 줄. 목록 줄에 결제 정보를 더한다.
     *
     * @param fee 등록 때 정한 작업 금액(원). 없으면 null
     * @param paymentMethod 등록 때 정한 결제 방식. 없으면 null
     */
    public record Row(WorkSearchInfo.WorkSummary work, Long fee, PaymentMethod paymentMethod) {}
}
