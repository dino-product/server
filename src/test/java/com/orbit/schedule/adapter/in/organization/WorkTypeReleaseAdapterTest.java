package com.orbit.schedule.adapter.in.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.WorkTypeDeletionImpact;
import com.orbit.organization.WorkTypeReleasedWork;
import com.orbit.schedule.application.port.in.command.dto.ReleaseDeletedWorkTypeCommand;
import com.orbit.schedule.application.port.in.command.dto.ReleasedWorkTypeInfo;
import com.orbit.schedule.application.port.in.query.dto.CountWorksByTypeQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkTypeUsageInfo;

@DisplayName("organization 작업 유형 삭제 요구 인터페이스 구현")
class WorkTypeReleaseAdapterTest {

    @Test
    @DisplayName("영향 건수를 시작 전·시작한 작업 수로 옮긴다")
    void translatesImpact() {
        WorkTypeReleaseAdapter adapter = new WorkTypeReleaseAdapter(
                query -> {
                    assertThat(query).isEqualTo(new CountWorksByTypeQuery(100L, 5L));
                    return new WorkTypeUsageInfo(2L, 3L);
                },
                command -> List.of());

        assertThat(adapter.countImpact(100L, 5L)).isEqualTo(new WorkTypeDeletionImpact(2L, 3L));
    }

    @Test
    @DisplayName("유형을 비운 작업과 담당 기사를 옮긴다")
    void translatesReleasedWorks() {
        WorkTypeReleaseAdapter adapter = new WorkTypeReleaseAdapter(query -> new WorkTypeUsageInfo(0L, 0L), command -> {
            assertThat(command).isEqualTo(new ReleaseDeletedWorkTypeCommand(100L, 5L));
            return List.of(new ReleasedWorkTypeInfo(7L, null), new ReleasedWorkTypeInfo(8L, 3L));
        });

        assertThat(adapter.releaseForDeletion(100L, 5L))
                .containsExactly(new WorkTypeReleasedWork(7L, null), new WorkTypeReleasedWork(8L, 3L));
    }
}
