package com.orbit.schedule.application.port.in.command.dto;

import com.orbit.schedule.domain.RejectionReason;

/**
 * 배정 거절 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다.
 *
 * @param assignmentNumber 기사가 화면에서 본 배정의 순번(배정 이력의 1부터 시작하는 위치). 그 뒤로 배정이 바뀌었으면 거절하지 않는다
 * @param reason 거절 사유. 필수
 * @param note 거절 메모. 사유가 기타면 필수(최대 255자)이고 다른 사유에는 받지 않는다
 */
public record RejectWorkCommand(
        Long accountId,
        Long organizationId,
        Long workId,
        Integer assignmentNumber,
        RejectionReason reason,
        String note) {}
