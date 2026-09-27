package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.ActualPaymentMethod;

/**
 * 작업의 현재 완료보고와 첨부 사진의 내려받을 주소. 관리자 강제 변경으로 치운 이전 보고는 담지 않는다(작업 상세의 강제 변경 기록에 있다). 입력하지 않은 항목은 null이다.
 *
 * @param technicianId 보고를 제출한 담당 기사
 * @param beforePhotos 작업 전 사진. 보고의 순서 그대로다
 * @param afterPhotos 작업 후 사진. 보고의 순서 그대로다
 * @param actualFee 실제 받은 금액(원)
 */
public record WorkCompletionReportInfo(
        Long workId,
        Long technicianId,
        Instant completedAt,
        List<PhotoView> beforePhotos,
        List<PhotoView> afterPhotos,
        String usedParts,
        String workNote,
        Long actualFee,
        ActualPaymentMethod actualPaymentMethod) {

    public WorkCompletionReportInfo {
        beforePhotos = List.copyOf(beforePhotos);
        afterPhotos = List.copyOf(afterPhotos);
    }

    public record PhotoView(String photoId, String url) {}
}
