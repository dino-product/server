package com.orbit.schedule.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;

import com.orbit.schedule.CompletionReportRequested;
import com.orbit.schedule.CompletionReportSubmitted;
import com.orbit.schedule.WorkAccepted;
import com.orbit.schedule.WorkAssigned;
import com.orbit.schedule.WorkCancelled;
import com.orbit.schedule.WorkChanged;
import com.orbit.schedule.WorkReassigned;
import com.orbit.schedule.WorkRejected;
import com.orbit.schedule.WorkRescheduled;
import com.orbit.schedule.domain.CompletionReportRequest;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 변경 유즈케이스가 저장한 뒤 발행하는 공개 이벤트를 만든다. 이벤트는 일어난 사실이며, 누구에게 무엇을 알릴지는 구독하는 쪽이 정한다. 발생 시각은 그 변경에 쓴
 * 시각이다. 저장하지 않은 변경(겹침 미확인 보류, 중복 요청, 거부)에는 부르지 않는다. 저장한 모든 변경은 {@link WorkChanged}를 내고, 알릴 사실이 있는 변경은 그
 * 사실 이벤트를 먼저 낸다. 대기함에 둔 등록(일정을 비우거나 일부만 준 등록)·배정 해제·기본정보 수정·시작·대기함 반환·작업 유형 해제는 아직 사실 이벤트를 구독하는
 * 모듈이 없어 {@link WorkChanged}만 낸다.
 */
final class WorkEvents {

    private WorkEvents() {}

    /**
     * 배정·재배정·일정 변경·취소 전의 상태와 현재 일정의 담당 기사(대기함이면 없음, 미리 입력한 담당기사는 담지 않음). 변경 뒤 어떤 사실이었는지(대기함 배정·재배정·
     * 일정 변경)와 이전 기사를 알려고 변경 전에 잡아 둔다.
     */
    record AssignmentBefore(WorkStatus status, Optional<TechnicianId> technicianId) {

        static AssignmentBefore of(Work work) {
            return new AssignmentBefore(work.status(), work.schedule().map(WorkSchedule::technicianId));
        }
    }

    /**
     * 반영한 배정·재배정·일정 변경을 하나의 사실로 발행하고 이어서 {@link WorkChanged}를 발행한다. 대기함 작업의 배정은 {@link WorkAssigned}, 담당
     * 기사가 바뀌었으면 {@link WorkReassigned}, 같은 기사의 시간 변경은 {@link WorkRescheduled}다. 재배정·일정 변경은 변경 전 수락 상태였는지를 함께
     * 담는다.
     */
    static void publishAssignmentChange(
            ApplicationEventPublisher publisher, AssignmentBefore before, Work changed, Instant at) {
        publisher.publishEvent(assignmentFact(before, changed, at));
        publisher.publishEvent(changed(changed, at));
    }

    private static Object assignmentFact(AssignmentBefore before, Work changed, Instant at) {
        WorkSchedule schedule =
                changed.schedule().orElseThrow(() -> new IllegalStateException("changed work must have a schedule"));
        if (before.status() == WorkStatus.REGISTERED) {
            return assigned(changed, at);
        }
        TechnicianId previous = before.technicianId()
                .orElseThrow(() -> new IllegalStateException("assigned work must have a technician"));
        boolean wasAccepted = before.status() == WorkStatus.ACCEPTED;
        if (previous.equals(schedule.technicianId())) {
            return new WorkRescheduled(
                    workId(changed),
                    changed.organizationId().value(),
                    schedule.technicianId().value(),
                    wasAccepted,
                    changed.name(),
                    schedule.startTime(),
                    at);
        }
        return new WorkReassigned(
                workId(changed),
                changed.organizationId().value(),
                previous.value(),
                schedule.technicianId().value(),
                wasAccepted,
                changed.name(),
                schedule.startTime(),
                at);
    }

    /** 작업 하나를 저장했다. 등록이면 저장소가 돌려준 작업을 넘겨 식별자를 얻는다. */
    static WorkChanged changed(Work work, Instant at) {
        return new WorkChanged(work.organizationId().value(), List.of(workId(work)), at);
    }

    /** 한 조직에서 함께 저장한 작업들을 한 건으로 발행한다. 저장한 작업이 없으면 발행하지 않는다. */
    static void publishChanged(
            ApplicationEventPublisher publisher, OrganizationId organizationId, List<WorkId> workIds, Instant at) {
        if (workIds.isEmpty()) {
            return;
        }
        publisher.publishEvent(new WorkChanged(
                organizationId.value(), workIds.stream().map(WorkId::value).toList(), at));
    }

    /** 대기함 작업을 배정했다(배정 변경으로 배정하거나 일정을 함께 준 등록). 등록이면 저장소가 돌려준 작업을 넘겨 식별자를 얻는다. */
    static WorkAssigned assigned(Work work, Instant at) {
        WorkSchedule schedule =
                work.schedule().orElseThrow(() -> new IllegalStateException("assigned work must have a schedule"));
        return new WorkAssigned(
                workId(work),
                work.organizationId().value(),
                schedule.technicianId().value(),
                work.name(),
                schedule.startTime(),
                at);
    }

    /** 작업을 취소했다. 담당 기사는 취소 전 일정의 기사이며 대기함 작업이면 없다(null). */
    static WorkCancelled cancelled(AssignmentBefore before, Work cancelled, Instant at) {
        return new WorkCancelled(
                workId(cancelled),
                cancelled.organizationId().value(),
                before.technicianId().map(TechnicianId::value).orElse(null),
                cancelled.name(),
                at);
    }

    static WorkAccepted accepted(Work work, TechnicianId technician, Instant at) {
        return new WorkAccepted(
                workId(work),
                work.organizationId().value(),
                work.registrarId().value(),
                technician.value(),
                work.name(),
                at);
    }

    /** 사유는 표시 문구가 아니라 거절 사유 이름과 기사가 적은 메모(없으면 null)로 담는다. 문구는 받는 쪽이 정한다. */
    static WorkRejected rejected(Work work, TechnicianId technician, Rejection rejection, Instant at) {
        return new WorkRejected(
                workId(work),
                work.organizationId().value(),
                work.registrarId().value(),
                technician.value(),
                work.name(),
                rejection.reason().name(),
                rejection.note(),
                at);
    }

    static CompletionReportSubmitted completionReportSubmitted(Work work, TechnicianId technician, Instant at) {
        return new CompletionReportSubmitted(
                workId(work),
                work.organizationId().value(),
                work.registrarId().value(),
                technician.value(),
                work.name(),
                at);
    }

    /** 작업중 작업의 담당 기사에게 완료보고를 요청했다. 발생 시각은 요청 기록의 시각이다. */
    static CompletionReportRequested completionReportRequested(Work work, CompletionReportRequest request) {
        TechnicianId technician = work.schedule()
                .map(WorkSchedule::technicianId)
                .orElseThrow(() -> new IllegalStateException("in-progress work must have a schedule"));
        return new CompletionReportRequested(
                workId(work), work.organizationId().value(), technician.value(), work.name(), request.requestedAt());
    }

    private static Long workId(Work work) {
        return work.id()
                .orElseThrow(() -> new IllegalStateException("stored work must have an id"))
                .value();
    }
}
