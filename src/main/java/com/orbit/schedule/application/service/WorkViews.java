package com.orbit.schedule.application.service;

import java.util.List;
import java.util.stream.IntStream;

import com.orbit.media.MediaFiles;
import com.orbit.media.MediaStorageUnavailableException;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.query.dto.CompletionReportInfo;
import com.orbit.schedule.application.port.in.query.dto.PhotoInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo;
import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.Cancellation;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.shared.error.BusinessException;

/** 조회 응답에 담을 작업의 일부를 도메인 값에서 옮겨 담는 공통 변환. 무엇을 보여 줄지는 호출하는 서비스가 정한다. */
final class WorkViews {

    private WorkViews() {}

    static WorkDetailInfo.Customer customer(CustomerInfo customer) {
        return new WorkDetailInfo.Customer(
                customer.name().orElse(null),
                customer.phone().orElse(null),
                customer.address().flatMap(SiteAddress::postalCode).orElse(null),
                customer.address().flatMap(SiteAddress::baseAddress).orElse(null),
                customer.address().flatMap(SiteAddress::detailAddress).orElse(null));
    }

    static WorkDetailInfo.Payment payment(PaymentInfo payment) {
        return new WorkDetailInfo.Payment(
                payment.fee().map(Money::won).orElse(null), payment.method().orElse(null));
    }

    static WorkDetailInfo.Schedule schedule(WorkSchedule schedule, DisplayInfos displayInfos) {
        return new WorkDetailInfo.Schedule(
                displayInfos.assignee(schedule), schedule.startTime(), schedule.expectedDuration(), schedule.endTime());
    }

    /** 모든 배정 이력을 순번과 함께 옮긴다. */
    static List<WorkDetailInfo.Assignment> assignments(Work work, DisplayInfos displayInfos) {
        List<AssignmentHistory> histories = work.assignmentHistory();
        return IntStream.range(0, histories.size())
                .mapToObj(index -> assignment(index + 1, histories.get(index), displayInfos))
                .toList();
    }

    /**
     * 최신 배정 하나만 옮긴다. 기사에게는 지금 자기 배정만 보이고, 같은 작업을 다시 배정받기 전의 거절 같은 지난 배정은 보이지 않는다([현장 수행] §4). 순번은
     * 전체 이력 기준 그대로다.
     */
    static List<WorkDetailInfo.Assignment> currentAssignment(Work work, DisplayInfos displayInfos) {
        List<AssignmentHistory> histories = work.assignmentHistory();
        int last = histories.size() - 1;
        return List.of(assignment(last + 1, histories.get(last), displayInfos));
    }

    static WorkDetailInfo.PlannedSchedule plannedSchedule(PlannedSchedule planned, DisplayInfos displayInfos) {
        return new WorkDetailInfo.PlannedSchedule(
                displayInfos.plannedTechnician(planned), planned.startTime(), planned.expectedDuration());
    }

    static WorkDetailInfo.Cancellation cancellation(Cancellation cancellation) {
        return new WorkDetailInfo.Cancellation(
                cancellation.cancelledAt(),
                cancellation.cancelledBy().value(),
                cancellation.reason(),
                cancellation.note());
    }

    /** 사진은 식별자와 함께 내려받을 주소를 담는다. */
    static CompletionReportInfo completionReport(Work work, CompletionReport report, MediaFiles mediaFiles) {
        return new CompletionReportInfo(
                photos(work, report.beforePhotos(), mediaFiles),
                photos(work, report.afterPhotos(), mediaFiles),
                report.usedParts().orElse(null),
                report.workNote().orElse(null),
                report.actualFee().map(Money::won).orElse(null),
                report.actualPaymentMethod().orElse(null));
    }

    /**
     * 사진 식별자마다 내려받을 시간 제한 주소를 붙인다. 호출한 조회가 이미 그 작업의 열람 권한을 확인했으므로 여기서 다시 확인하지 않는다. 주소는 서명만 해 저장소를 부르지
     * 않는다(ADR-007). 서명하지 못하면(자격 증명을 얻지 못함 등) 사진을 쓰는 변경과 같은 503(SCHEDULE-023)이다.
     */
    static List<PhotoInfo> photos(Work work, List<String> photoIds, MediaFiles mediaFiles) {
        Long organizationId = work.organizationId().value();
        try {
            return photoIds.stream()
                    .map(photoId -> new PhotoInfo(photoId, mediaFiles.downloadUrl(organizationId, photoId)))
                    .toList();
        } catch (MediaStorageUnavailableException exception) {
            throw new BusinessException(ScheduleErrorCode.PHOTO_STORAGE_UNAVAILABLE, exception);
        }
    }

    private static WorkDetailInfo.Assignment assignment(
            int number, AssignmentHistory history, DisplayInfos displayInfos) {
        return new WorkDetailInfo.Assignment(
                number,
                schedule(history.schedule(), displayInfos),
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
}
