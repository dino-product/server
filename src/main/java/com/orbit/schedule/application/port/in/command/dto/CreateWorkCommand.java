package com.orbit.schedule.application.port.in.command.dto;

import java.time.Duration;
import java.time.Instant;

import com.orbit.schedule.domain.PaymentMethod;

/**
 * 작업 등록 요청. 요청자는 인증된 계정(accountId)과 요청한 조직(organizationId)으로 식별한다. 요청자 식별값과 작업명 외 항목은 선택이며 null이면 비워 둔다.
 * 담당기사·시작시각·예상소요시간을 모두 주면 등록과 함께 배정하고, 일부만 주면 입력한 값을 남겨 대기함에 두며, 모두 비우면 대기함에 둔다.
 *
 * @param fee 원 단위 요금
 * @param conflictConfirmed 배정할 때 같은 기사의 일정 겹침 경고를 확인했는지
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
        PaymentMethod paymentMethod,
        Long technicianId,
        Instant startTime,
        Duration expectedDuration,
        boolean conflictConfirmed) {}
