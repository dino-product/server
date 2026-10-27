package com.orbit.organization.application.port.in.query.dto;

import java.time.Instant;

import com.orbit.organization.domain.MemberRole;

/**
 * 참여 인원 목록의 한 줄. 계정마다 하나다. {@code memberId}는 역할이 총관리자·직원이면 직원 소속 식별자, 기사면 기사 계약 식별자다. 이름은 profile에서 빌려
 * 오며 가입을 마치지 않았거나 프로필이 없으면 {@code null}, 세부 유형이 "유형 없음"이면 {@code category}가 {@code null}이다.
 */
public record ParticipantInfo(
        Long memberId,
        Long accountId,
        String name,
        MemberRole role,
        boolean active,
        Category category,
        Instant joinedAt) {

    public record Category(Long categoryId, String name, String color) {}
}
