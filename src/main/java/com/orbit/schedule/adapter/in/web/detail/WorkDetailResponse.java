package com.orbit.schedule.adapter.in.web.detail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.orbit.schedule.adapter.in.web.LabelResponse;
import com.orbit.schedule.adapter.in.web.StaffResponse;
import com.orbit.schedule.adapter.in.web.TechnicianResponse;
import com.orbit.schedule.application.port.in.query.dto.CompletionReportInfo;
import com.orbit.schedule.application.port.in.query.dto.PhotoInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkDetailInfo;
import com.orbit.schedule.domain.ActualPaymentMethod;
import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.WorkStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "작업 상세. 등록자와 완료보고 요청 기록은 관리자에게만 담는다. 기사가 지금 담당이 아니면 작업명·상태와 "
                + "자기 배정 이력만 담고 나머지는 null(지연은 false, 현장 사진은 빈 목록)이다")
public record WorkDetailResponse(
        @Schema(description = "작업 식별자", example = "10") Long workId,

        @Schema(description = "작업명", example = "배관 누수 점검 및 교체")
        String name,

        @Schema(
                description = "작업 상태. REGISTERED(등록), PENDING_ACCEPTANCE(배정됨), ACCEPTED(작업전), "
                        + "IN_PROGRESS(작업중), COMPLETED(완료), CANCELLED(취소)",
                example = "ACCEPTED")
        WorkStatus status,

        @Schema(description = "조회 시각에 지연된 작업인지", example = "false")
        boolean delayed,

        @Schema(description = "작업유형. 없으면 null") LabelResponse workType,
        @Schema(description = "등록자. 기사에게는 null") StaffResponse registrar,

        @Schema(description = "등록 시각(상태 변경 이력의 작업 등록됨)", example = "2026-10-18T01:00:00Z")
        Instant registeredAt,

        @Schema(description = "고객 정보") Customer customer,
        @Schema(description = "결제 정보(등록 때 정한 값)") Payment payment,
        @Schema(description = "현재 일정. 대기함 작업이면 null") Schedule schedule,

        @Schema(description = "대기함 작업이 등록 때 일부만 입력해 둔 담당기사·시간. 없으면 null")
        PlannedSchedule plannedSchedule,

        @Schema(description = "배정 이력(상태 변경 이력의 배정·수락·거절·종료 시각). 기사에게는 자기 배정만 원래 순번 그대로 담는다")
        List<Assignment> assignments,

        @Schema(description = "작업 시작 시각. 없으면 null", example = "2026-10-21T00:05:00Z")
        Instant startedAt,

        @Schema(description = "완료 시각. 없으면 null", example = "2026-10-21T02:00:00Z")
        Instant completedAt,

        @Schema(description = "취소 기록. 없으면 null") Cancellation cancellation,
        @Schema(description = "완료보고. 없으면 null") CompletionReport completionReport,

        @Schema(description = "등록·수정 때 붙인 현장 사진(첨부 파일). 기사가 지금 담당이 아니면 빈 목록")
        List<Photo> sitePhotos,

        @Schema(
                description = "사무실이 담당 기사에게 완료보고를 요청한 기록(오래된 것부터, 상태 변경 이력의 보고 요청 발송됨). "
                        + "마지막 요청 시각으로 '요청 보냄'을 그린다. 관리자에게만 담고 기사에게는 빈 목록")
        List<CompletionReportRequest> completionReportRequests,

        @Schema(
                description = "조회 시각에 완료보고를 요청할 수 있는지(작업중·예정 종료 시각 지남·마지막 요청 뒤 30분 지남). "
                        + "true면 '기사에게 요청 보내기'를 보여 준다. 기사에게는 false",
                example = "false")
        boolean completionReportRequestable,

        @Schema(description = "작업 버전. 배정 변경·해제 요청의 expectedVersion으로 그대로 보낸다", example = "3")
        long version) {

    static WorkDetailResponse from(WorkDetailInfo info) {
        return new WorkDetailResponse(
                info.workId(),
                info.name(),
                info.status(),
                info.delayed(),
                LabelResponse.from(info.workType()),
                info.registrar() == null ? null : StaffResponse.from(info.registrar()),
                info.registeredAt(),
                info.customer() == null ? null : Customer.from(info.customer()),
                info.payment() == null
                        ? null
                        : new Payment(info.payment().fee(), info.payment().method()),
                info.schedule() == null ? null : Schedule.from(info.schedule()),
                info.plannedSchedule() == null ? null : PlannedSchedule.from(info.plannedSchedule()),
                info.assignments().stream().map(Assignment::from).toList(),
                info.startedAt(),
                info.completedAt(),
                info.cancellation() == null ? null : Cancellation.from(info.cancellation()),
                info.completionReport() == null ? null : CompletionReport.from(info.completionReport()),
                info.sitePhotos().stream().map(Photo::from).toList(),
                info.completionReportRequests().stream()
                        .map(CompletionReportRequest::from)
                        .toList(),
                info.completionReportRequestable(),
                info.version());
    }

    private static Integer minutes(Duration duration) {
        return duration == null ? null : Math.toIntExact(duration.toMinutes());
    }

    @Schema(description = "고객 정보. 입력하지 않은 항목은 null")
    public record Customer(
            @Schema(description = "고객명", example = "이정호") String name,

            @Schema(description = "연락처", example = "010-1234-5678")
            String phone,

            @Schema(description = "현장 우편번호", example = "04043")
            String postalCode,

            @Schema(description = "현장 기본주소", example = "서울시 마포구 양화로 45")
            String address,

            @Schema(description = "현장 상세주소", example = "3층 302호")
            String addressDetail) {

        static Customer from(WorkDetailInfo.Customer info) {
            return new Customer(info.name(), info.phone(), info.postalCode(), info.address(), info.addressDetail());
        }
    }

    @Schema(description = "결제 정보. 입력하지 않은 항목은 null")
    public record Payment(
            @Schema(description = "작업 금액(원)", example = "120000")
            Long fee,

            @Schema(
                    description =
                            "결제 방식. ON_SITE_CARD(현장 카드), ON_SITE_CASH(현장 현금), BANK_TRANSFER(계좌이체), INVOICE(청구서 발행·후불)")
            PaymentMethod method) {}

    @Schema(description = "일정")
    public record Schedule(
            @Schema(description = "담당 기사") TechnicianResponse technician,

            @Schema(description = "시작 시각", example = "2026-10-21T00:00:00Z")
            Instant startTime,

            @Schema(description = "예상 소요시간(분)", example = "120")
            Integer expectedDurationMinutes,

            @Schema(description = "종료 시각", example = "2026-10-21T02:00:00Z")
            Instant endTime) {

        static Schedule from(WorkDetailInfo.Schedule info) {
            return new Schedule(
                    TechnicianResponse.from(info.technician()),
                    info.startTime(),
                    minutes(info.expectedDuration()),
                    info.endTime());
        }
    }

    @Schema(description = "미리 입력한 일정. 셋 중 일부만 있다")
    public record PlannedSchedule(
            @Schema(description = "담당 기사. 없으면 null") TechnicianResponse technician,

            @Schema(description = "시작 시각. 없으면 null", example = "2026-10-21T00:00:00Z")
            Instant startTime,

            @Schema(description = "예상 소요시간(분). 없으면 null", example = "120")
            Integer expectedDurationMinutes) {

        static PlannedSchedule from(WorkDetailInfo.PlannedSchedule info) {
            return new PlannedSchedule(
                    TechnicianResponse.from(info.technician()), info.startTime(), minutes(info.expectedDuration()));
        }
    }

    @Schema(description = "배정 한 건. 응답·거절·종료가 없으면 해당 항목이 null")
    public record Assignment(
            @Schema(description = "배정 순번(1부터). 기사의 수락·거절·시작·완료보고 요청에 쓴다", example = "1")
            int assignmentNumber,

            @Schema(description = "그 배정의 일정") Schedule schedule,

            @Schema(
                    description = "배정 시각. 화면의 '배정 알림 발송됨'에 쓴다. 단 앞 배정이 RESCHEDULED로 끝났으면 일정 변경으로 "
                            + "이어진 배정이라 이 값은 변경 시각이고 배정 알림은 다시 보내지 않으므로(수락된 작업만 일정 변경 알림) "
                            + "'배정 알림 발송됨'으로 그리지 않는다",
                    example = "2026-10-20T08:50:00Z")
            Instant assignedAt,

            @Schema(description = "배정한 관리자(소속 식별자)", example = "5")
            Long assignedBy,

            @Schema(
                    description = "응답 결과. PENDING(응답 전), ACCEPTED(수락), REJECTED(거절), WITHDRAWN(응답 전 회수)",
                    example = "ACCEPTED")
            AssignmentResult result,

            @Schema(
                    description =
                            "응답 시각. 앞 배정이 RESCHEDULED로 끝났고 수락된 작업이면 변경 시각에 수락을 이어받은 것이라 " + "기사의 응답 시각이 아니라 일정 변경 시각이다",
                    example = "2026-10-20T09:02:00Z")
            Instant decidedAt,

            @Schema(
                    description =
                            "거절 사유 코드(SCHEDULE_CONFLICT, ALREADY_ASSIGNED, LOCATION_TOO_FAR, SCOPE_MISMATCH, OTHER)")
            RejectionReason rejectionReason,

            @Schema(description = "거절 메모") String rejectionNote,

            @Schema(
                    description = "배정이 끝난 방식. REASSIGNED(재배정), RESCHEDULED(일정 변경), UNASSIGNED(배정 해제), "
                            + "CANCELLED(취소), ACCOUNT_WITHDRAWN(기사 탈퇴)")
            AssignmentEndReason endReason,

            @Schema(description = "배정이 끝난 시각") Instant endedAt,

            @Schema(description = "배정을 끝낸 관리자(소속 식별자). 기사 탈퇴로 끝났으면 null")
            Long endedBy) {

        static Assignment from(WorkDetailInfo.Assignment info) {
            return new Assignment(
                    info.assignmentNumber(),
                    Schedule.from(info.schedule()),
                    info.assignedAt(),
                    info.assignedBy(),
                    info.result(),
                    info.decidedAt(),
                    info.rejectionReason(),
                    info.rejectionNote(),
                    info.endReason(),
                    info.endedAt(),
                    info.endedBy());
        }
    }

    @Schema(description = "취소 기록")
    public record Cancellation(
            @Schema(description = "취소 시각", example = "2026-10-20T10:00:00Z")
            Instant cancelledAt,

            @Schema(description = "취소한 관리자(소속 식별자)", example = "5")
            Long cancelledBy,

            @Schema(
                    description = "취소 사유. CUSTOMER_REQUEST(고객 요청), TECHNICIAN_ISSUE(기사 사정), "
                            + "DUPLICATE_REGISTRATION(중복 등록), OTHER(기타)",
                    example = "CUSTOMER_REQUEST")
            CancellationReason reason,

            @Schema(description = "기타 사유 메모") String note) {

        static Cancellation from(WorkDetailInfo.Cancellation info) {
            return new Cancellation(info.cancelledAt(), info.cancelledBy(), info.reason(), info.note());
        }
    }

    @Schema(description = "완료보고")
    public record CompletionReport(
            @Schema(description = "작업 전 사진") List<Photo> beforePhotos,
            @Schema(description = "작업 후 사진") List<Photo> afterPhotos,
            @Schema(description = "사용 부품") String usedParts,
            @Schema(description = "작업 메모") String workNote,
            @Schema(description = "실제 결제 금액(원)") Long actualFee,

            @Schema(description = "실제 결제 방식. CASH(현금), BANK_TRANSFER(계좌이체), CREDIT_CARD(신용카드), INVOICE(청구서 발행·후불)")
            ActualPaymentMethod actualPaymentMethod) {

        static CompletionReport from(CompletionReportInfo info) {
            return new CompletionReport(
                    info.beforePhotos().stream().map(Photo::from).toList(),
                    info.afterPhotos().stream().map(Photo::from).toList(),
                    info.usedParts(),
                    info.workNote(),
                    info.actualFee(),
                    info.actualPaymentMethod());
        }
    }

    @Schema(description = "완료보고 요청 한 건")
    public record CompletionReportRequest(
            @Schema(description = "요청 시각", example = "2026-10-21T02:10:00Z")
            Instant requestedAt,

            @Schema(description = "요청한 관리자(소속 식별자)", example = "5")
            Long requestedBy) {

        static CompletionReportRequest from(WorkDetailInfo.CompletionReportRequest info) {
            return new CompletionReportRequest(info.requestedAt(), info.requestedBy());
        }
    }

    @Schema(description = "사진")
    public record Photo(
            @Schema(description = "업로드한 사진 식별자") String photoId,
            @Schema(description = "내려받을 주소") String url) {

        static Photo from(PhotoInfo info) {
            return new Photo(info.photoId(), info.url());
        }
    }
}
