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
 * 작업 상세. 관리자는 모든 항목을 받고, 기사는 자기에게 보이는 항목만 받는다(보이지 않는 항목은 null 또는 빈 목록). 기사가 지금 담당이 아니면(다른 기사로 바뀌었거나
 * 대기함으로 돌아갔으면) 작업명·상태와 자기 배정 이력만 받는다.
 *
 * @param delayed 조회 시각에 지연된 작업인지(예정 종료시각이 지났는데 아직 끝나지 않음)
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
        CustomerView customer,
        PaymentView payment,
        ScheduleView schedule,
        List<AssignmentView> assignments,
        Instant startedAt,
        Instant completedAt,
        CancellationView cancellation,
        CompletionReportInfo completionReport,
        List<StatusCorrectionView> statusCorrections) {

    public WorkDetailInfo {
        assignments = List.copyOf(assignments);
        statusCorrections = List.copyOf(statusCorrections);
    }

    /** 입력하지 않은 항목은 null이다. */
    public record CustomerView(String name, String phone, String address) {}

    /** 입력하지 않은 항목은 null이다. */
    public record PaymentView(Long fee, PaymentMethod method) {}

    public record ScheduleView(Long technicianId, Instant startTime, Duration expectedDuration, Instant endTime) {}

    /**
     * 배정 한 건. 응답·종료가 없으면 해당 항목이 null이다.
     *
     * @param assignmentNumber 배정 이력의 1부터 시작하는 순번. 기사의 수락·거절·시작·완료보고 요청에 쓴다
     */
    public record AssignmentView(
            int assignmentNumber,
            ScheduleView schedule,
            Instant assignedAt,
            Long assignedBy,
            AssignmentResult result,
            Instant decidedAt,
            RejectionReason rejectionReason,
            String rejectionNote,
            AssignmentEndReason endReason,
            Instant endedAt,
            Long endedBy) {}

    public record CancellationView(Instant cancelledAt, Long cancelledBy, String reason) {}

    /** 관리자 강제 변경 한 건과 그때 작업에서 치운 기록. 치운 것이 없으면 해당 항목이 null이다. */
    public record StatusCorrectionView(
            WorkStatus from,
            WorkStatus to,
            Instant correctedAt,
            Long correctedBy,
            String reason,
            CompletionReportInfo retiredReport,
            Instant retiredCompletedAt,
            Instant retiredStartedAt,
            CancellationView retiredCancellation) {}
}
