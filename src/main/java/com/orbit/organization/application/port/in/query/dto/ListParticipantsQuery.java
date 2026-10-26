package com.orbit.organization.application.port.in.query.dto;

import com.orbit.organization.domain.ParticipationType;

/**
 * {@code accountId}는 인증 주체에서 얻은 요청자 계정이다. {@code type}은 역할 필터로 {@code null}이면 전체, {@code STAFF}면 총관리자를 포함한 사무실
 * 직원이다. {@code name}은 이름 검색어로 {@code null}이거나 공백뿐이면 거르지 않는다.
 */
public record ListParticipantsQuery(Long accountId, Long organizationId, ParticipationType type, String name) {}
