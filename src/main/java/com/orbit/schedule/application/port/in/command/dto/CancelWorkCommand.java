package com.orbit.schedule.application.port.in.command.dto;

import com.orbit.schedule.domain.CancellationReason;

/**
 * 작업 취소 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다.
 *
 * @param reason 목록에서 고른 취소 사유. 필수다
 * @param note 기타({@code OTHER}) 사유의 메모. 기타일 때 필수이고 다른 사유에는 줄 수 없으며(빈칸은 없음으로 본다) 최대 255자다
 */
public record CancelWorkCommand(
        Long accountId, Long organizationId, Long workId, CancellationReason reason, String note) {}
