package com.orbit.schedule.application.service;

import java.util.List;
import java.util.stream.IntStream;

import com.orbit.schedule.application.port.in.query.dto.CompletionReportInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo.AssignmentView;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo.CancellationView;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo.CustomerView;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo.PaymentView;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo.ScheduleView;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo.StatusCorrectionView;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.Cancellation;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.StatusCorrection;
import com.orbit.schedule.domain.TechnicianActor;
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

    static CustomerView customer(CustomerInfo customer) {
        return new CustomerView(
                customer.name().orElse(null),
                customer.phone().orElse(null),
                customer.address().orElse(null));
    }

    static PaymentView payment(PaymentInfo payment) {
        return new PaymentView(
                payment.fee().map(Money::won).orElse(null), payment.method().orElse(null));
    }

    static ScheduleView schedule(WorkSchedule schedule) {
        return new ScheduleView(
                schedule.technicianId().value(), schedule.startTime(), schedule.expectedDuration(), schedule.endTime());
    }

    /** 배정 이력을 순번과 함께 옮긴다. technician이 있으면 그 기사의 배정만 남긴다(순번은 전체 이력 기준 그대로). */
    static List<AssignmentView> assignments(Work work, TechnicianActor technician) {
        List<AssignmentHistory> histories = work.assignmentHistory();
        return IntStream.range(0, histories.size())
                .filter(index -> technician == null
                        || histories.get(index).schedule().technicianId().equals(technician.technicianId()))
                .mapToObj(index -> assignment(index + 1, histories.get(index)))
                .toList();
    }

    static CancellationView cancellation(Cancellation cancellation) {
        return new CancellationView(
                cancellation.cancelledAt(), cancellation.cancelledBy().value(), cancellation.reason());
    }

    static List<StatusCorrectionView> statusCorrections(Work work) {
        return work.statusCorrections().stream()
                .map(WorkViews::statusCorrection)
                .toList();
    }

    private static AssignmentView assignment(int number, AssignmentHistory history) {
        return new AssignmentView(
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

    private static StatusCorrectionView statusCorrection(StatusCorrection correction) {
        return new StatusCorrectionView(
                correction.from(),
                correction.to(),
                correction.correctedAt(),
                correction.correctedBy().value(),
                correction.reason(),
                correction.retiredReport() == null ? null : CompletionReportInfo.from(correction.retiredReport()),
                correction.retiredCompletedAt(),
                correction.retiredStartedAt(),
                correction.retiredCancellation() == null ? null : cancellation(correction.retiredCancellation()));
    }
}
