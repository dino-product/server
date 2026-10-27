package com.orbit.schedule.adapter.in.organization;

import org.springframework.stereotype.Component;

import com.orbit.organization.TechnicianWorkRelease;
import com.orbit.organization.TechnicianWorkReleaseBusyException;
import com.orbit.organization.TechnicianWorkReleaseResult;
import com.orbit.organization.WorkReleaseBlocked;
import com.orbit.organization.WorkReleased;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.ReleaseTechnicianWorksUseCase;
import com.orbit.schedule.application.port.in.command.dto.ReleaseTechnicianWorksCommand;
import com.orbit.schedule.application.port.in.command.dto.ReleaseTechnicianWorksCommand.Cause;
import com.orbit.schedule.application.port.in.command.dto.TechnicianWorkReleaseInfo;
import com.orbit.shared.error.BusinessException;

/**
 * organization이 선언한 대기함 반환 요구 인터페이스를 schedule의 반환 유즈케이스로 잇는 어댑터. organization의 트랜잭션 안에서 불리며, 반환 결과와 기사 일정 잠금
 * 대기 초과(SCHEDULE-006)를 organization 계약의 결과 타입·예외로 옮기기만 한다. organization은 schedule 타입을 볼 수 없어 계약의 언어로만 실패를 받는다.
 */
@Component
class TechnicianWorkReleaseAdapter implements TechnicianWorkRelease {

    private final ReleaseTechnicianWorksUseCase releaseTechnicianWorksUseCase;

    TechnicianWorkReleaseAdapter(ReleaseTechnicianWorksUseCase releaseTechnicianWorksUseCase) {
        this.releaseTechnicianWorksUseCase = releaseTechnicianWorksUseCase;
    }

    @Override
    public TechnicianWorkReleaseResult releaseForRoleChange(
            Long organizationId, Long technicianId, Long processedByMembershipId) {
        TechnicianWorkReleaseInfo info =
                release(organizationId, technicianId, processedByMembershipId, Cause.ROLE_CHANGE);
        return info.released() ? new WorkReleased(info.workIds()) : new WorkReleaseBlocked(info.workIds());
    }

    @Override
    public void releaseForDeactivation(Long organizationId, Long technicianId, Long processedByMembershipId) {
        TechnicianWorkReleaseInfo info =
                release(organizationId, technicianId, processedByMembershipId, Cause.DEACTIVATION);
        if (!info.released()) {
            throw new IllegalStateException("deactivation release must not be blocked");
        }
    }

    private TechnicianWorkReleaseInfo release(
            Long organizationId, Long technicianId, Long processedByMembershipId, Cause cause) {
        try {
            return releaseTechnicianWorksUseCase.release(
                    new ReleaseTechnicianWorksCommand(organizationId, technicianId, processedByMembershipId, cause));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY) {
                throw new TechnicianWorkReleaseBusyException(exception);
            }
            throw exception;
        }
    }
}
