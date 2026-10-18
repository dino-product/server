package com.orbit.schedule.application.port.in.query.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 작업 상세. 관리자는 모든 항목을 받고, 기사는 자기에게 보이는 항목만 받는다(보이지 않는 항목은 null·false·빈 목록). 기사가 지금 담당이 아니면(다른 기사로
 * 바뀌었거나 대기함으로 돌아갔으면) 작업명·상태와 자기 배정 이력만 받는다.
 *
 * @param delayed 조회 시각에 지연된 작업인지({@link com.orbit.schedule.domain.Work#isDelayedAt}). 배정됐다 돌아온 대기함 작업은 일정이
 *     없어도 마지막 배정 일정(배정 이력의 마지막)으로 판정한다. 기사가 지금 담당이 아니면 false
 * @param workType 작업유형(시작 전 작업의 삭제된 유형은 없는 것으로 본다). 없거나 기사가 지금 담당이 아니면 null
 * @param registrar 등록자. 관리자에게만 담고 기사에게는 null
 * @param registeredAt 등록 시각(상태 변경 이력의 "작업 등록됨"). 기사가 지금 담당이 아니면 null
 * @param customer 고객 정보. 기사가 지금 담당이 아니면 null
 * @param payment 결제 정보. 기사가 지금 담당이 아니면 null
 * @param schedule 현재 일정. 대기함 작업이거나 기사가 지금 담당이 아니면 null
 * @param plannedSchedule 대기함 작업이 등록 때 일부만 입력해 둔 담당기사·시간. 없거나 기사가 지금 담당이 아니면 null
 * @param assignments 배정 이력. 기사에게는 자기 배정만 원래 순번 그대로 담는다
 * @param startedAt 작업 시작 시각. 없거나 기사가 지금 담당이 아니면 null
 * @param completedAt 완료 시각. 없거나 기사가 지금 담당이 아니면 null
 * @param cancellation 취소 기록. 없거나 기사가 지금 담당이 아니면 null
 * @param completionReport 완료보고. 없거나 기사가 지금 담당이 아니면 null
 * @param sitePhotos 등록·수정 때 붙인 현장 사진(첨부 파일). 기사가 지금 담당이 아니면 빈 목록
 * @param statusCorrections 관리자 강제 변경 기록. 기사에게는 빈 목록
 * @param version 작업 버전. 배정 변경·해제 요청의 expectedVersion으로 그대로 보낸다
 */
public record WorkDetailInfo(
        Long workId,
        String name,
        WorkStatus status,
        boolean delayed,
        LabelInfo workType,
        StaffInfo registrar,
        Instant registeredAt,
        Customer customer,
        Payment payment,
        Schedule schedule,
        PlannedSchedule plannedSchedule,
        List<Assignment> assignments,
        Instant startedAt,
        Instant completedAt,
        Cancellation cancellation,
        CompletionReportInfo completionReport,
        List<PhotoInfo> sitePhotos,
        List<StatusCorrection> statusCorrections,
        long version) {

    public WorkDetailInfo {
        assignments = List.copyOf(assignments);
        sitePhotos = List.copyOf(sitePhotos);
        statusCorrections = List.copyOf(statusCorrections);
    }

    /**
     * 입력하지 않은 항목은 null이다.
     *
     * @param address 현장 기본주소
     * @param addressDetail 현장 상세주소
     */
    public record Customer(String name, String phone, String postalCode, String address, String addressDetail) {}

    /** 입력하지 않은 항목은 null이다. */
    public record Payment(Long fee, PaymentMethod method) {}

    /** 미리 입력한 일정. 셋 중 일부만 있다. */
    public record PlannedSchedule(TechnicianInfo technician, Instant startTime, Duration expectedDuration) {}

    public record Schedule(TechnicianInfo technician, Instant startTime, Duration expectedDuration, Instant endTime) {}

    /**
     * 배정 한 건. 응답·거절·종료가 없으면 해당 항목이 null이다.
     *
     * @param assignmentNumber 배정 이력의 1부터 시작하는 순번. 기사의 수락·거절·시작·완료보고 요청에 쓴다
     * @param endedBy 배정을 끝낸 관리자(조직 소속). 기사의 탈퇴로 끝났으면({@code ACCOUNT_WITHDRAWN}) 처리한 관리자가 없어 종료 기록이 있어도 null이다
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

    public record Cancellation(Instant cancelledAt, Long cancelledBy, CancellationReason reason, String note) {}

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
