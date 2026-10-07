package com.orbit.schedule.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.orbit.schedule.domain.AssignmentEnding;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.Cancellation;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * {@link Work} 애그리게잇과 JPA Entity 사이의 변환. Entity는 필드만 가지고 이 모듈의 매핑 결정은 여기에 모은다.
 *
 * <ul>
 *   <li>현재 일정(담당 기사·시작시각·예상소요시간)은 {@code work} 테이블에 둔 최신 배정 이력의 비정규화 사본이다. 겹침 후보 조회가 이력 테이블을 보지 않게
 *       하기 위한 것이며 저장할 때마다 Domain의 현재 일정으로 덮어쓴다.
 *   <li>완료보고는 ERD대로 {@code work} 컬럼에 인라인이고 사진만 {@code work_execution_images}로 나뉜다. 작업 상태가 완료일 때만 복원하며, 항목이 모두
 *       선택 입력이라 보고 컬럼이 전부 비어 있어도 빈 보고를 만든다.
 *   <li>배정 이력의 순번은 부모 컬렉션의 {@code id} 순서다(ERD에 순번 컬럼이 없음). 기존 저장은 i번째 행을 i번째 이력으로 갱신하고 새 이력만 추가한다
 *       (이력은 추가 전용).
 *   <li>상태 정정 기록({@code StatusCorrection})은 ERD에 자리가 없어 저장하지 않으며 복원 시 빈 목록이다. 정정 기록이 있는 작업은 어댑터가 저장을
 *       거부한다(S-11).
 *   <li>복원({@link #toDomain})은 {@link Work#reconstitute}를 거치므로 저장값의 정합성 검증은 Domain이 한다. 검증 실패는 그대로 전파한다.
 * </ul>
 */
final class WorkMapper {

    private WorkMapper() {}

    /** 새 작업을 Entity로 만든다. 식별자가 있는 작업은 {@link #applyChanges}로 기존 Entity에 반영한다. */
    static WorkJpaEntity toEntity(Work work) {
        if (work.id().isPresent()) {
            throw new IllegalArgumentException("only new works can be converted to a new entity");
        }
        WorkJpaEntity entity = new WorkJpaEntity(
                work.organizationId().value(), work.registrarId().value(), work.name(), work.status());
        applyChanges(entity, work);
        return entity;
    }

    /** Domain의 현재 값을 managed Entity에 덮어쓴다. 배정 이력은 기존 행을 갱신하고 새 행만 추가한다. */
    static void applyChanges(WorkJpaEntity entity, Work work) {
        WorkStatus previousStatus = entity.status();
        CustomerInfo customer = work.customerInfo();
        PaymentInfo payment = work.paymentInfo();
        entity.updateDetails(
                work.name(),
                work.workType().map(WorkTypeId::value).orElse(null),
                customer.name().orElse(null),
                customer.phone().orElse(null),
                customer.address().orElse(null),
                payment.method().orElse(null),
                payment.fee().map(Money::won).orElse(null));
        WorkSchedule schedule = work.schedule().orElse(null);
        entity.updateStatusAndSchedule(
                work.status(),
                schedule == null ? null : schedule.technicianId().value(),
                schedule == null ? null : schedule.startTime(),
                schedule == null ? null : schedule.expectedDuration());
        Cancellation cancellation = work.cancellation().orElse(null);
        entity.updateProgress(
                work.startedAt().orElse(null),
                work.completedAt().orElse(null),
                cancellation == null ? null : cancellation.cancelledAt(),
                cancellation == null ? null : cancellation.cancelledBy().value(),
                cancellation == null ? null : cancellation.reason());
        applyAssignments(entity, work.assignmentHistory());
        applyCompletionReport(entity, previousStatus, work.completionReport().orElse(null));
    }

    static Work toDomain(WorkJpaEntity entity) {
        List<AssignmentHistory> histories =
                entity.assignments().stream().map(WorkMapper::toHistory).toList();
        WorkSchedule schedule = entity.technicianId() == null
                ? null
                : new WorkSchedule(
                        new TechnicianId(entity.technicianId()), entity.scheduledStartAt(), entity.expectedDuration());
        Cancellation cancellation = entity.cancelledAt() == null
                ? null
                : new Cancellation(entity.cancelledAt(), new MembershipId(entity.cancelledBy()), entity.cancelReason());
        return Work.reconstitute(
                new WorkId(entity.id()),
                new OrganizationId(entity.companyId()),
                entity.name(),
                new MembershipId(entity.createdBy()),
                entity.jobTypeId() == null ? null : new WorkTypeId(entity.jobTypeId()),
                schedule,
                new CustomerInfo(entity.customerName(), entity.customerPhone(), entity.customerAddress()),
                new PaymentInfo(entity.fee() == null ? null : new Money(entity.fee()), entity.paymentMethod()),
                entity.status(),
                histories,
                entity.status() == WorkStatus.COMPLETED ? toCompletionReport(entity) : null,
                entity.startedAt(),
                entity.completedAt(),
                cancellation,
                List.of());
    }

    private static void applyAssignments(WorkJpaEntity entity, List<AssignmentHistory> histories) {
        List<WorkAssignmentJpaEntity> rows = entity.assignments();
        if (histories.size() < rows.size()) {
            throw new IllegalStateException(
                    "assignment history is append-only: stored " + rows.size() + ", given " + histories.size());
        }
        for (int i = 0; i < histories.size(); i++) {
            AssignmentHistory history = histories.get(i);
            WorkAssignmentJpaEntity row;
            if (i < rows.size()) {
                row = rows.get(i);
                requireSameAssignment(row, history, i + 1);
            } else {
                WorkSchedule schedule = history.schedule();
                row = new WorkAssignmentJpaEntity(
                        entity,
                        schedule.technicianId().value(),
                        history.assignedBy().value(),
                        schedule.startTime(),
                        schedule.expectedDuration(),
                        history.assignedAt(),
                        history.result());
                rows.add(row);
            }
            Rejection rejection = history.rejection().orElse(null);
            row.updateDecision(
                    history.result(),
                    history.decidedAt().orElse(null),
                    rejection == null ? null : rejection.reason(),
                    rejection == null ? null : rejection.note());
            AssignmentEnding ending = history.ending().orElse(null);
            row.updateEnding(
                    ending == null ? null : ending.endedAt(),
                    ending == null ? null : ending.endedBy().value(),
                    ending == null ? null : ending.reason());
        }
    }

    /** 이력은 추가 전용이라 저장된 행의 배정 자체(기사·일정·배정자·배정 시각)는 바뀌지 않아야 한다. */
    private static void requireSameAssignment(WorkAssignmentJpaEntity row, AssignmentHistory history, int number) {
        WorkSchedule schedule = history.schedule();
        boolean same =
                Objects.equals(row.technicianId(), schedule.technicianId().value())
                        && Objects.equals(row.scheduledStartAt(), schedule.startTime())
                        && Objects.equals(row.expectedDuration(), schedule.expectedDuration())
                        && Objects.equals(row.assignedBy(), history.assignedBy().value())
                        && Objects.equals(row.assignedAt(), history.assignedAt());
        if (!same) {
            throw new IllegalStateException("assignment #" + number + " differs from the stored assignment");
        }
    }

    private static void applyCompletionReport(
            WorkJpaEntity entity, WorkStatus previousStatus, CompletionReport report) {
        if (report == null) {
            entity.updateCompletionReport(null, null, null, null);
            // 사진은 완료된 작업에만 있다. 완료가 아니던 작업의 빈 컬렉션까지 clear하면 지연 컬렉션 초기화 조회가 생기므로 피한다.
            if (previousStatus == WorkStatus.COMPLETED) {
                entity.images().clear();
            }
            return;
        }
        entity.updateCompletionReport(
                report.usedParts().orElse(null),
                report.workNote().orElse(null),
                report.actualFee().map(Money::won).orElse(null),
                report.actualPaymentMethod().orElse(null));
        List<WorkExecutionImageJpaEntity> images = entity.images();
        if (!samePhotos(images, report)) {
            images.clear();
            for (String photo : report.beforePhotos()) {
                images.add(new WorkExecutionImageJpaEntity(entity, WorkExecutionImageJpaEntity.Kind.BEFORE, photo));
            }
            for (String photo : report.afterPhotos()) {
                images.add(new WorkExecutionImageJpaEntity(entity, WorkExecutionImageJpaEntity.Kind.AFTER, photo));
            }
        }
    }

    private static boolean samePhotos(List<WorkExecutionImageJpaEntity> images, CompletionReport report) {
        return photos(images, WorkExecutionImageJpaEntity.Kind.BEFORE).equals(report.beforePhotos())
                && photos(images, WorkExecutionImageJpaEntity.Kind.AFTER).equals(report.afterPhotos());
    }

    private static List<String> photos(
            List<WorkExecutionImageJpaEntity> images, WorkExecutionImageJpaEntity.Kind kind) {
        List<String> result = new ArrayList<>();
        for (WorkExecutionImageJpaEntity image : images) {
            if (image.kind() == kind) {
                result.add(image.imageUrl());
            }
        }
        return result;
    }

    private static CompletionReport toCompletionReport(WorkJpaEntity entity) {
        return new CompletionReport(
                photos(entity.images(), WorkExecutionImageJpaEntity.Kind.BEFORE),
                photos(entity.images(), WorkExecutionImageJpaEntity.Kind.AFTER),
                entity.usedParts(),
                entity.workNote(),
                entity.actualFee() == null ? null : new Money(entity.actualFee()),
                entity.actualPaymentMethod());
    }

    private static AssignmentHistory toHistory(WorkAssignmentJpaEntity row) {
        Rejection rejection =
                row.rejectionReason() == null ? null : new Rejection(row.rejectionReason(), row.rejectionNote());
        AssignmentEnding ending = row.endedAt() == null
                ? null
                : new AssignmentEnding(row.endedAt(), new MembershipId(row.endedBy()), row.endReason());
        return AssignmentHistory.restore(
                new WorkSchedule(new TechnicianId(row.technicianId()), row.scheduledStartAt(), row.expectedDuration()),
                row.assignedAt(),
                new MembershipId(row.assignedBy()),
                row.result(),
                rejection,
                row.decidedAt(),
                ending);
    }
}
