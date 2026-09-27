package com.orbit.schedule.application.port.in.command.dto;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 관리자 강제 상태 변경 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별하며 총관리자만 할 수 있다.
 *
 * @param targetStatus 되돌릴 상태. 완료 → 작업중, 작업중 → 수락됨, 취소 → 대기함만 허용한다
 * @param reason 정정 사유. 필수이며 최대 255자다
 */
public record CorrectWorkStatusCommand(
        Long accountId, Long organizationId, Long workId, WorkStatus targetStatus, String reason) {}
