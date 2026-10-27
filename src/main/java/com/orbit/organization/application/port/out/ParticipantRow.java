package com.orbit.organization.application.port.out;

import java.time.Instant;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberRole;

/**
 * 발주사의 직원 소속 또는 기사 계약 한 행. 역할 변경으로 끝난 행도 비활성으로 들어 있다. {@code memberId}는 직원 소속(총관리자·직원)이면 소속 식별자, 기사면
 * 기사 계약 식별자다. 세부 유형이 "유형 없음"이면 유형 칸은 모두 {@code null}이다.
 */
public record ParticipantRow(
        MemberRole role,
        Long memberId,
        AccountId accountId,
        boolean active,
        Long categoryId,
        String categoryName,
        String categoryColor,
        Instant joinedAt,
        Instant statusChangedAt) {}
