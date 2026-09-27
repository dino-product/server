package com.orbit.schedule.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.application.port.out.WorkSearchCriteria.Sort;
import com.orbit.schedule.domain.WorkStatus;

@DisplayName("작업 목록 검색 조건")
class WorkSearchCriteriaTest {

    private static final Instant AT = Instant.parse("2026-09-25T01:00:00Z");

    @Test
    @DisplayName("검색어는 다듬어진 값이고, 상태 집합·정렬은 비울 수 없고, 시작 구간은 앞뒤가 맞게 함께 주며, 건너뛰는 작업은 최대 10,000건이다")
    void guardsInvariants() {
        assertThatThrownBy(() -> criteria(null, Sort.REGISTERED_DESC, null, null, 0, 20))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> criteria(Set.of(), null, null, null, 0, 20)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> criteria(Set.of(), Sort.REGISTERED_DESC, AT, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(Set.of(), Sort.REGISTERED_DESC, AT, AT, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        for (String keyword : new String[] {"", " 보일러", "보일러 "}) {
            assertThatThrownBy(() -> new WorkSearchCriteria(
                            keyword, Set.of(), null, null, null, false, null, Sort.REGISTERED_DESC, 0, 20))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> criteria(Set.of(), Sort.REGISTERED_DESC, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(Set.of(), Sort.REGISTERED_DESC, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(Set.of(), Sort.REGISTERED_DESC, null, null, 101, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(criteria(Set.of(), Sort.REGISTERED_DESC, AT, AT.plusSeconds(1), 100, 100)
                        .page())
                .isEqualTo(100);
    }

    private static WorkSearchCriteria criteria(
            Set<WorkStatus> statuses, Sort sort, Instant startFrom, Instant startTo, int page, int size) {
        return new WorkSearchCriteria(null, statuses, null, startFrom, startTo, false, null, sort, page, size);
    }
}
