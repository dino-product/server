package com.orbit.schedule.application.port.in.query.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 작업 상세. 관리자는 모든 항목을 받고, 기사는 자기에게 보이는 항목만 받는다(보이지 않는 항목은 null·false·빈 목록). 기사가 지금 담당이 아니면(다른 기사로
 * 바뀌었거나 대기함으로 돌아갔으면) 작업명·상태와 자기 배정 이력만 받는다.
 *
 * @param delayed 조회 시각에 지연된 작업인지(아직 끝나지 않았는데 예정 종료시각과 같거나 지남). 기사가 지금 담당이 아니면 false
 * @param workTypeId 작업 유형. 없거나 기사가 지금 담당이 아니면 null
 * @param customer 고객 정보. 기사가 지금 담당이 아니면 null
 * @param payment 결제 정보. 기사가 지금 담당이 아니면 null
 * @param schedule 현재 일정. 대기함 작업이거나 기사가 지금 담당이 아니면 null
 * @param assignments 배정 이력. 기사에게는 자기 배정만 원래 순번 그대로 담는다
 * @param startedAt 작업 시작 시각. 없거나 기사가 지금 담당이 아니면 null
 * @param completedAt 완료 시각. 없거나 기사가 지금 담당이 아니면 null
 * @param cancellation 취소 기록. 없거나 기사가 지금 담당이 아니면 null
 * @param completionReport 완료보고. 없거나 기사가 지금 담당이 아니면 null
 * @param statusCorrections 관리자 강제 변경 기록. 기사에게는 빈 목록
 */
public record WorkDetailInfo(
        Long workId,
        String name,
        WorkStatus status,
        boolean delayed,
        Long workTypeId,
        Customer customer,
        Payment payment,
        Schedule schedule,
        List<Assignment> assignments,
        Instant startedAt,
        Instant completedAt,
        Cancellation cancellation,
        CompletionReportInfo completionReport,
        List<StatusCorrection> statusCorrections) {

    public WorkDetailInfo {
        assignments = List.copyOf(assignments);
        statusCorrections = List.copyOf(statusCorrections);
    }

    /** 입력하지 않은 항목은 null이다. */
    public record Customer(String name, String phone, String address) {}

    /** 입력하지 않은 항목은 null이다. */
    public record Payment(Long fee, PaymentMethod method) {}

    public record Schedule(Long technicianId, Instant startTime, Duration expectedDuration, Instant endTime) {}

    /**
     * 배정 한 건. 응답·거절·종료가 없으면 해당 항목이 null이다.
     *
     * @param assignmentNumber 배정 이력의 1부터 시작하는 순번. 기사의 수락·거절·시작·완료보고 요청에 쓴다
     */
    public record Assignment(
            int assignmentNumber,
            Schedule schedule,
            Instant assignedAt,
            Long assignedBy,
            AssignmentResult result,
            Instant decidedAt,
            RejectionReason rejectionReason,
            String rejectionNote,
            AssignmentEndReason endReason,
            Instant endedAt,
            Long endedBy) {}

    public record Cancellation(Instant cancelledAt, Long cancelledBy, String reason) {}

    /**
     * 관리자 강제 변경 한 건과 그때 작업에서 치운 기록. 치운 것이 없으면 해당 항목이 null이다.
     *
     * @param restoredAssignmentNumber 배정 중 취소를 대기함으로 되돌렸을 때 그 배정의 순번. 배정 이력의 취소 종료는 그대로 남으므로 어느 취소가 되돌려졌는지는
     *     이 값으로만 알 수 있다. 그 밖의 정정이면 null
     */
    public record StatusCorrection(
            WorkStatus from,
            WorkStatus to,
            Instant correctedAt,
            Long correctedBy,
            String reason,
            CompletionReportInfo retiredReport,
            Instant retiredCompletedAt,
            Instant retiredStartedAt,
            Cancellation retiredCancellation,
            Integer restoredAssignmentNumber) {}
}
