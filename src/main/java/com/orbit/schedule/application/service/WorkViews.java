package com.orbit.schedule.application.service;

import java.util.List;
import java.util.stream.IntStream;

import com.orbit.schedule.application.port.in.query.dto.CompletionReportInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo;
import com.orbit.schedule.application.port.out.PhotoUrlPort;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.Cancellation;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.StatusCorrection;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/** 조회 응답에 담을 작업의 일부를 도메인 값에서 옮겨 담는 공통 변환. 무엇을 보여 줄지는 호출하는 서비스가 정한다. */
final class WorkViews {

    private WorkViews() {}

    /** 기사가 지금 그 작업의 담당(현재 일정의 기사)인지. 대기함으로 돌아가 일정이 없으면 담당이 아니다. */
    static boolean isCurrentAssignee(Work work, TechnicianActor technician) {
        return work.schedule()
                .map(schedule -> schedule.technicianId().equals(technician.technicianId()))
                .orElse(false);
    }

    static WorkDetailInfo.Customer customer(CustomerInfo customer) {
        return new WorkDetailInfo.Customer(
                customer.name().orElse(null),
                customer.phone().orElse(null),
                customer.address().orElse(null));
    }

    static WorkDetailInfo.Payment payment(PaymentInfo payment) {
        return new WorkDetailInfo.Payment(
                payment.fee().map(Money::won).orElse(null), payment.method().orElse(null));
    }

    static WorkDetailInfo.Schedule schedule(WorkSchedule schedule) {
        return new WorkDetailInfo.Schedule(
                schedule.technicianId().value(), schedule.startTime(), schedule.expectedDuration(), schedule.endTime());
    }

    /** 모든 배정 이력을 순번과 함께 옮긴다. */
    static List<WorkDetailInfo.Assignment> assignments(Work work) {
        return assignments(work, null);
    }

    /** 그 기사의 배정만 옮긴다. 순번은 전체 이력 기준 그대로다. */
    static List<WorkDetailInfo.Assignment> assignmentsOf(Work work, TechnicianId technicianId) {
        return assignments(work, technicianId);
    }

    static WorkDetailInfo.Cancellation cancellation(Cancellation cancellation) {
        return new WorkDetailInfo.Cancellation(
                cancellation.cancelledAt(), cancellation.cancelledBy().value(), cancellation.reason());
    }

    /** 사진은 식별자와 함께 내려받을 주소를 담는다. */
    static CompletionReportInfo completionReport(CompletionReport report, PhotoUrlPort photoUrlPort) {
        return new CompletionReportInfo(
                photos(report.beforePhotos(), photoUrlPort),
                photos(report.afterPhotos(), photoUrlPort),
                report.usedParts().orElse(null),
                report.workNote().orElse(null),
                report.actualFee().map(Money::won).orElse(null),
                report.actualPaymentMethod().orElse(null));
    }

    static List<WorkDetailInfo.StatusCorrection> statusCorrections(Work work, PhotoUrlPort photoUrlPort) {
        return work.statusCorrections().stream()
                .map(correction -> statusCorrection(correction, photoUrlPort))
                .toList();
    }

    private static List<CompletionReportInfo.Photo> photos(List<String> photoIds, PhotoUrlPort photoUrlPort) {
        return photoIds.stream()
                .map(photoId -> new CompletionReportInfo.Photo(photoId, photoUrlPort.urlOf(photoId)))
                .toList();
    }

    private static List<WorkDetailInfo.Assignment> assignments(Work work, TechnicianId technicianId) {
        List<AssignmentHistory> histories = work.assignmentHistory();
        return IntStream.range(0, histories.size())
                .filter(index -> technicianId == null
                        || histories.get(index).schedule().technicianId().equals(technicianId))
                .mapToObj(index -> assignment(index + 1, histories.get(index)))
                .toList();
    }

    private static WorkDetailInfo.Assignment assignment(int number, AssignmentHistory history) {
        return new WorkDetailInfo.Assignment(
                number,
                schedule(history.schedule()),
                history.assignedAt(),
                history.assignedBy().value(),
                history.result(),
                history.decidedAt().orElse(null),
                history.rejection().map(Rejection::reason).orElse(null),
                history.rejection().map(Rejection::note).orElse(null),
                history.ending().map(AssignmentEnding::reason).orElse(null),
                history.ending().map(AssignmentEnding::endedAt).orElse(null),
                history.ending()
                        .map(AssignmentEnding::endedBy)
                        .map(MembershipId::value)
                        .orElse(null));
    }

    private static WorkDetailInfo.StatusCorrection statusCorrection(
            StatusCorrection correction, PhotoUrlPort photoUrlPort) {
        return new WorkDetailInfo.StatusCorrection(
                correction.from(),
                correction.to(),
                correction.correctedAt(),
                correction.correctedBy().value(),
                correction.reason(),
                correction.retiredReport() == null ? null : completionReport(correction.retiredReport(), photoUrlPort),
                correction.retiredCompletedAt(),
                correction.retiredStartedAt(),
                correction.retiredCancellation() == null ? null : cancellation(correction.retiredCancellation()),
                correction.restoredAssignmentNumber());
    }
}
