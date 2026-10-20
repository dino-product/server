package com.orbit.schedule.application.service;

import java.util.List;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;
import com.orbit.shared.error.BusinessException;

/**
 * 기사가 볼 수 있는 작업의 판정과, 기사가 화면에서 본 배정을 기준으로 요청(수락·거절·시작·완료보고)을 받는 공통 절차. 배정 순번은 배정 이력의 1부터 시작하는 위치이며
 * 이력은 추가만 되므로 바뀌지 않는다. 기사가 화면을 연 사이 일정이 바뀌어도 담당 기사는 그대로일 수 있어, 순번으로 본 적 없는 배정에 대해 처리하지 않게 한다. 호출
 * 순서:
 *
 * <ol>
 *   <li>{@link #requireVisible}: 기사가 볼 수 없는 작업이면 작업이 없는 것처럼 404(SCHEDULE-001). 작업을 찾은 직후, 입력 검증보다 먼저 부른다.
 *   <li>{@link #requireAssignmentNumber}: 순번이 없거나 1보다 작으면 400(SCHEDULE-003).
 *   <li>{@link #requireCurrentAssignmentOf}: 그 순번의 배정이 없거나 요청한 기사의 배정이 아니면 403(SCHEDULE-009), 이미 최신 배정이 아니면(일정
 *       변경, 다른 기사나 대기함을 거쳐 다시 배정됨) 409(SCHEDULE-010).
 * </ol>
 *
 * 최신 배정이어도 처리할 수 있는 상태인지는 도메인이 확인한다(이미 다른 응답을 했거나 취소됐으면 409). 같은 요청을 다시 보낸 경우는 서비스가 성공으로 처리한다.
 */
final class AssignedTechnicians {

    private AssignedTechnicians() {}

    /**
     * 기사가 그 작업을 볼 수 있는지. 작업에 남은 일정의 기사가 그 기사일 때만 볼 수 있다. 수락대기·수락됨·작업중·완료는 최신 배정이 그 기사이고, 취소는 취소 당시
     * 담당이던 기사다(배정 중 취소된 작업만 일정이 남는다). 거절했거나, 다른 기사로 재배정됐거나, 배정 해제·기사 반환으로 대기함에 돌아갔거나, 한 번도 배정받지 않은
     * 작업은 볼 수 없다. 거절했던 기사에게 다시 배정하면 새 배정으로 다시 보인다.
     */
    static boolean isVisibleTo(Work work, TechnicianActor technician) {
        return work.schedule()
                .map(schedule -> schedule.technicianId().equals(technician.technicianId()))
                .orElse(false);
    }

    /** 기사가 볼 수 없는 작업이면 다른 조직 작업처럼 존재를 숨겨 404(SCHEDULE-001)다. */
    static void requireVisible(Work work, TechnicianActor technician) {
        if (!isVisibleTo(work, technician)) {
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
    static AssignmentHistory requireCurrentAssignmentOf(Work work, TechnicianActor technician, int assignmentNumber) {
        List<AssignmentHistory> histories = work.assignmentHistory();
        if (assignmentNumber > histories.size()) {
            throw new BusinessException(ScheduleErrorCode.NOT_ASSIGNED_TECHNICIAN);
        }
        AssignmentHistory assignment = histories.get(assignmentNumber - 1);
        if (!assignment.schedule().technicianId().equals(technician.technicianId())) {
            throw new BusinessException(ScheduleErrorCode.NOT_ASSIGNED_TECHNICIAN);
        }
        if (assignmentNumber != work.currentAssignmentNumber()) {
            throw new BusinessException(ScheduleErrorCode.ASSIGNMENT_CHANGED);
        }
        return assignment;
    }
}
