package com.orbit.schedule.application.service;

import java.util.List;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.Work;
import com.orbit.shared.error.BusinessException;

/**
 * 기사가 화면에서 본 배정을 기준으로 응답(수락·거절)을 받는 공통 절차. 배정 순번은 배정 이력의 1부터 시작하는 위치이며 이력은 추가만 되므로 바뀌지 않는다. 기사가
 * 화면을 연 사이 일정이 바뀌어도 담당 기사는 그대로일 수 있어, 순번으로 본 적 없는 배정에 응답하지 않게 한다. 호출 순서:
 *
 * <ol>
 *   <li>{@link #requireEverAssigned}: 그 작업에 한 번도 배정된 적 없는 기사에게는 작업이 없는 것처럼 404(SCHEDULE-001). 작업을 찾은 직후, 입력 검증보다
 *       먼저 부른다.
 *   <li>{@link #requireAssignmentNumber}: 순번이 없거나 1보다 작으면 400(SCHEDULE-003).
 *   <li>{@link #requireCurrentAssignmentOf}: 그 순번의 배정이 없거나 요청한 기사의 배정이 아니면 403(SCHEDULE-009), 이미 최신 배정이 아니면(재배정·일정
 *       변경됨) 409(SCHEDULE-010).
 * </ol>
 *
 * 최신 배정이어도 응답할 수 있는 상태인지는 도메인이 확인한다(이미 다른 응답을 했거나 회수됐으면 409). 같은 응답을 다시 보낸 경우는 서비스가 성공으로 처리한다.
 */
final class AssignedTechnicians {

    private AssignedTechnicians() {}

    static void requireEverAssigned(Work work, Actor technician) {
        boolean everAssigned = work.assignmentHistory().stream()
                .anyMatch(history -> history.schedule().technicianId().equals(technician.membershipId()));
        if (!everAssigned) {
            throw new BusinessException(ScheduleErrorCode.WORK_NOT_FOUND);
        }
    }

    static int requireAssignmentNumber(Integer assignmentNumber) {
        if (assignmentNumber == null || assignmentNumber < 1) {
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
        }
        return assignmentNumber;
    }

    /** 기사가 본 배정이 그 기사의 최신 배정이면 그 배정 이력을 돌려준다. */
    static AssignmentHistory requireCurrentAssignmentOf(Work work, Actor technician, int assignmentNumber) {
        List<AssignmentHistory> histories = work.assignmentHistory();
        if (assignmentNumber > histories.size()) {
            throw new BusinessException(ScheduleErrorCode.NOT_ASSIGNED_TECHNICIAN);
        }
        AssignmentHistory assignment = histories.get(assignmentNumber - 1);
        if (!assignment.schedule().technicianId().equals(technician.membershipId())) {
            throw new BusinessException(ScheduleErrorCode.NOT_ASSIGNED_TECHNICIAN);
        }
        if (assignmentNumber != histories.size()) {
            throw new BusinessException(ScheduleErrorCode.ASSIGNMENT_CHANGED);
        }
        return assignment;
    }
}
