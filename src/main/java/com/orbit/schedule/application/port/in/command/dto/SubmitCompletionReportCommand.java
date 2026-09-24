package com.orbit.schedule.application.port.in.command.dto;

import java.util.List;

import com.orbit.schedule.domain.ActualPaymentMethod;

/**
 * 완료보고 제출 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. 사진은 먼저 올려 둔 파일의 식별자이며, 업로드와 식별자 형식은
 * 이 요청의 범위 밖이다. 실제 금액·결제수단은 비워 보낼 수 있다.
 *
 * @param assignmentNumber 기사가 화면에서 본 배정의 순번(배정 이력의 1부터 시작하는 위치). 그 뒤로 배정이 바뀌었으면 받지 않는다
 * @param actualFee 실제로 받은 금액(원). 음수는 받지 않는다
 */
public record SubmitCompletionReportCommand(
        Long accountId,
        Long organizationId,
        Long workId,
        Integer assignmentNumber,
        List<String> beforePhotos,
        List<String> afterPhotos,
        String usedParts,
        String workNote,
        Long actualFee,
        ActualPaymentMethod actualPaymentMethod) {}
