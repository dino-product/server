package com.orbit.schedule.adapter.in.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.TechnicianWorkReleaseBusyException;
import com.orbit.organization.WorkReleaseBlocked;
import com.orbit.organization.WorkReleased;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.ReleaseTechnicianWorksUseCase;
import com.orbit.schedule.application.port.in.command.dto.ReleaseTechnicianWorksCommand;
import com.orbit.schedule.application.port.in.command.dto.ReleaseTechnicianWorksCommand.Cause;
import com.orbit.schedule.application.port.in.command.dto.TechnicianWorkReleaseInfo;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@DisplayName("organization 대기함 반환 요구 인터페이스 구현")
class TechnicianWorkReleaseAdapterTest {

    private final RecordingUseCase useCase = new RecordingUseCase();
    private final TechnicianWorkReleaseAdapter adapter = new TechnicianWorkReleaseAdapter(useCase);

    @Test
    @DisplayName("역할 변경은 역할 변경 반환을 요청하고 돌린 작업을 반환 결과로 옮긴다")
    void translatesReleasedRoleChange() {
        useCase.result = TechnicianWorkReleaseInfo.released(List.of(5L, 6L));

        assertThat(adapter.releaseForRoleChange(100L, 3L, 21L)).isEqualTo(new WorkReleased(List.of(5L, 6L)));
        assertThat(useCase.commands)
                .containsExactly(new ReleaseTechnicianWorksCommand(100L, 3L, 21L, Cause.ROLE_CHANGE));
    }

    @Test
    @DisplayName("역할 변경이 작업중 작업에 막히면 그 작업을 막힘 결과로 옮긴다")
    void translatesBlockedRoleChange() {
        useCase.result = TechnicianWorkReleaseInfo.blocked(List.of(7L));

        assertThat(adapter.releaseForRoleChange(100L, 3L, 21L)).isEqualTo(new WorkReleaseBlocked(List.of(7L)));
    }

    @Test
    @DisplayName("비활성화는 비활성화 반환을 요청한다")
    void requestsDeactivationRelease() {
        useCase.result = TechnicianWorkReleaseInfo.released(List.of(5L));

        adapter.releaseForDeactivation(100L, 3L, 21L);

        assertThat(useCase.commands)
                .containsExactly(new ReleaseTechnicianWorksCommand(100L, 3L, 21L, Cause.DEACTIVATION));
    }

    @Test
    @DisplayName("비활성화가 막혔다는 결과는 유즈케이스 계약 위반이므로 성공으로 넘기지 않는다")
    void rejectsBlockedDeactivation() {
        useCase.result = TechnicianWorkReleaseInfo.blocked(List.of(7L));

        assertThatThrownBy(() -> adapter.releaseForDeactivation(100L, 3L, 21L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("기사 일정 잠금 대기 초과는 organization 계약의 예외로 바꾸고, 그 밖의 오류는 그대로 전파한다")
    void translatesBusyTechnicianSchedule() {
        BusinessException busy = new BusinessException(ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY);
        useCase.failure = busy;

        assertThatThrownBy(() -> adapter.releaseForDeactivation(100L, 3L, 21L))
                .isInstanceOf(TechnicianWorkReleaseBusyException.class)
                .hasCause(busy);
        assertThatThrownBy(() -> adapter.releaseForRoleChange(100L, 3L, 21L))
                .isInstanceOf(TechnicianWorkReleaseBusyException.class)
                .hasCause(busy);

        BusinessException other = new BusinessException(CommonErrorCode.BAD_REQUEST);
        useCase.failure = other;
        assertThatThrownBy(() -> adapter.releaseForDeactivation(100L, 3L, 21L)).isSameAs(other);
    }

    private static final class RecordingUseCase implements ReleaseTechnicianWorksUseCase {

        private final List<ReleaseTechnicianWorksCommand> commands = new ArrayList<>();
        private TechnicianWorkReleaseInfo result;
        private RuntimeException failure;

        @Override
        public TechnicianWorkReleaseInfo release(ReleaseTechnicianWorksCommand command) {
            commands.add(command);
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
