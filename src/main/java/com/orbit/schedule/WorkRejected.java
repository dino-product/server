package com.orbit.schedule;

import java.time.Instant;

/**
 * 담당 기사가 배정을 거절해 작업이 대기함으로 돌아갔다. {@code reasonCode}는 거절 사유 코드({@code SCHEDULE_CONFLICT}·{@code ALREADY_ASSIGNED}·
 * {@code LOCATION_TOO_FAR}·{@code SCOPE_MISMATCH}·{@code OTHER}, 거절 사유 이름 — 바꾸면 구독하는 쪽 문구 대응도 함께 고친다)이고
 * {@code reasonNote}는 기사가 적은 메모(없으면 null)다. {@code registrarMembershipId}는 작업을 등록한 직원·총관리자의 소속이다. 작업명은 발행 시점 값이다.
 */
public record WorkRejected(
        Long workId,
        Long organizationId,
        Long registrarMembershipId,
        Long technicianId,
        String workName,
        String reasonCode,
        String reasonNote,
        Instant occurredAt) {}
