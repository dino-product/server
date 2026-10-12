package com.orbit.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("대기함 반환 결과")
class TechnicianWorkReleaseResultTest {

    @Test
    @DisplayName("돌린 예정 작업이 없으면 빈 목록으로 돌려준다")
    void releasedMayBeEmpty() {
        assertThat(new WorkReleased(List.of()).releasedWorkIds()).isEmpty();
    }

    @Test
    @DisplayName("작업중 작업 없이는 거부 결과를 만들 수 없다")
    void blockedRequiresInProgressWork() {
        assertThatThrownBy(() -> new WorkReleaseBlocked(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("작업 ID 목록은 null이거나 양수가 아닌 값을 담을 수 없다")
    void rejectsMissingOrNonPositiveWorkIds() {
        assertThatThrownBy(() -> new WorkReleased(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkReleased(Arrays.asList(1L, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkReleaseBlocked(List.of(0L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("만든 뒤 넘긴 목록을 바꿔도 결과는 바뀌지 않는다")
    void copiesWorkIds() {
        List<Long> workIds = new ArrayList<>(List.of(1L, 2L));
        WorkReleased released = new WorkReleased(workIds);
        workIds.add(3L);

        assertThat(released.releasedWorkIds()).containsExactly(1L, 2L);
        assertThatThrownBy(() -> released.releasedWorkIds().add(4L)).isInstanceOf(UnsupportedOperationException.class);
    }
}
