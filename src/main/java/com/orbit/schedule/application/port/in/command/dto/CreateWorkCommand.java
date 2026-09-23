package com.orbit.schedule.application.port.in.command.dto;

import com.orbit.schedule.domain.PaymentMethod;

/**
 * 작업 등록 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. 요청자 식별값과 작업명 외 항목은 선택이며 null이면 비워 둔다.
 *
 * @param fee 원 단위 요금
 */
public record CreateWorkCommand(
        Long accountId,
        Long organizationId,
        String name,
        Long workTypeId,
        String customerName,
        String customerPhone,
        String customerAddress,
        Long fee,
        PaymentMethod paymentMethod) {}
