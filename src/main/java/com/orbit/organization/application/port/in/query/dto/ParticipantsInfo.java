package com.orbit.organization.application.port.in.query.dto;

import java.util.List;

/** 참여 인원 목록과 역할별 인원 수. 인원 수는 이름 검색을 적용한 뒤 역할 필터와 관계없이 센다. */
public record ParticipantsInfo(Counts counts, List<ParticipantInfo> participants) {

    /** {@code staff}는 총관리자를 포함한 사무실 직원 수다. */
    public record Counts(int all, int staff, int technician) {}
}
