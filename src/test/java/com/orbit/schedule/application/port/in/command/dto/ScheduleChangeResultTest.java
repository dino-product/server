package com.orbit.schedule.application.port.in.command.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.port.in.command.dto.ScheduleChangeResult.ConflictingWork;

@DisplayName("일정 변경 결과")
class ScheduleChangeResultTest {

    private static final ConflictingWork CONFLICT = new ConflictingWork(
            1L, "기존 작업", Instant.parse("2026-09-25T01:00:00Z"), Instant.parse("2026-09-25T03:00:00Z"));

    @Test
    @DisplayName("반영하지 않은 결과는 겹친 작업이 없으면 만들 수 없다")
    void withheldRequiresConflicts() {
        assertThatThrownBy(() -> ScheduleChangeResult.withheld(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(ScheduleChangeResult.withheld(List.of(CONFLICT)).applied()).isFalse();
    }

    @Test
    @DisplayName("겹친 작업 목록은 넘긴 목록이 바뀌어도 그대로다")
    void copiesConflicts() {
        List<ConflictingWork> conflicts = new ArrayList<>(List.of(CONFLICT));
        ScheduleChangeResult result = ScheduleChangeResult.applied(conflicts);

        conflicts.clear();

        assertThat(result.applied()).isTrue();
        assertThat(result.conflicts()).containsExactly(CONFLICT);
    }
}
