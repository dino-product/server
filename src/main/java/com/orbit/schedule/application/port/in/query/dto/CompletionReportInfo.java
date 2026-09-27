package com.orbit.schedule.application.port.in.query.dto;

import java.util.List;

import com.orbit.schedule.domain.ActualPaymentMethod;

/**
 * 완료보고 내용. 작업 상세와 완료보고 조회가 함께 쓴다. 입력하지 않은 항목은 null이고, 사진이 없으면 빈 목록이다.
 *
 * @param beforePhotos 작업 전 사진. 보고의 순서 그대로다
 * @param afterPhotos 작업 후 사진. 보고의 순서 그대로다
 * @param actualFee 실제 받은 금액(원)
 */
public record CompletionReportInfo(
        List<Photo> beforePhotos,
        List<Photo> afterPhotos,
        String usedParts,
        String workNote,
        Long actualFee,
        ActualPaymentMethod actualPaymentMethod) {

    public CompletionReportInfo {
        beforePhotos = List.copyOf(beforePhotos);
        afterPhotos = List.copyOf(afterPhotos);
    }

    /** 업로드를 마친 사진의 식별자와 내려받을 주소. */
    public record Photo(String photoId, String url) {}
}
