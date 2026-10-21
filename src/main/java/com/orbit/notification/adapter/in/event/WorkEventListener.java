package com.orbit.notification.adapter.in.event;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.orbit.notification.application.port.in.command.CreateWorkNotificationUseCase;
import com.orbit.notification.application.port.in.command.dto.CreateWorkNotificationCommand;
import com.orbit.schedule.CompletionReportRequested;
import com.orbit.schedule.CompletionReportSubmitted;
import com.orbit.schedule.WorkAccepted;
import com.orbit.schedule.WorkAssigned;
import com.orbit.schedule.WorkCancelled;
import com.orbit.schedule.WorkReassigned;
import com.orbit.schedule.WorkRejected;
import com.orbit.schedule.WorkRescheduled;

/**
 * schedule의 작업 이벤트를 받는 사람별 알림 Command로 바꾼다. 실제로 만들지(수락한 작업만 알리는 종류 등)는 서비스가 정한다. schedule 커밋 뒤 다른
 * 스레드·트랜잭션에서 실행되므로 실패해도 schedule 변경은 되돌아가지 않는다. 실패하면 이벤트 발행 기록이 처리될 때까지 다시 보내므로(ADR-004) 같은 이벤트가 두 번
 * 이상 올 수 있다.
 */
@Component
public class WorkEventListener {

    private final CreateWorkNotificationUseCase useCase;

    public WorkEventListener(CreateWorkNotificationUseCase useCase) {
        this.useCase = useCase;
    }

    @ApplicationModuleListener
    public void on(WorkAssigned event) {
        useCase.create(CreateWorkNotificationCommand.workAssigned(
                event.organizationId(),
                event.workId(),
                event.workName(),
                event.technicianId(),
                event.scheduledStartAt(),
                event.occurredAt()));
    }

    /** 새 기사에게는 배정 알림을, 이전 기사에게는 넘어감 알림을 보낸다. 둘은 한 트랜잭션에서 함께 남는다. */
    @ApplicationModuleListener
    public void on(WorkReassigned event) {
        useCase.create(CreateWorkNotificationCommand.workAssigned(
                event.organizationId(),
                event.workId(),
                event.workName(),
                event.technicianId(),
                event.scheduledStartAt(),
                event.occurredAt()));
        useCase.create(CreateWorkNotificationCommand.workReassignedAway(
                event.organizationId(),
                event.workId(),
                event.workName(),
                event.previousTechnicianId(),
                event.previousAccepted(),
                event.occurredAt()));
    }

    @ApplicationModuleListener
    public void on(WorkRescheduled event) {
        useCase.create(CreateWorkNotificationCommand.workRescheduled(
                event.organizationId(),
                event.workId(),
                event.workName(),
                event.technicianId(),
                event.accepted(),
                event.scheduledStartAt(),
                event.occurredAt()));
    }

    @ApplicationModuleListener
    public void on(WorkCancelled event) {
        useCase.create(CreateWorkNotificationCommand.workCancelled(
                event.organizationId(), event.workId(), event.workName(), event.technicianId(), event.occurredAt()));
    }

    @ApplicationModuleListener
    public void on(CompletionReportRequested event) {
        useCase.create(CreateWorkNotificationCommand.completionReportRequested(
                event.organizationId(), event.workId(), event.workName(), event.technicianId(), event.occurredAt()));
    }

    @ApplicationModuleListener
    public void on(WorkAccepted event) {
        useCase.create(CreateWorkNotificationCommand.workAccepted(
                event.organizationId(),
                event.workId(),
                event.workName(),
                event.registrarMembershipId(),
                event.technicianId(),
                event.occurredAt()));
    }

    @ApplicationModuleListener
    public void on(WorkRejected event) {
        useCase.create(CreateWorkNotificationCommand.workRejected(
                event.organizationId(),
                event.workId(),
                event.workName(),
                event.registrarMembershipId(),
                event.technicianId(),
                event.reasonCode(),
                event.reasonNote(),
                event.occurredAt()));
    }

    @ApplicationModuleListener
    public void on(CompletionReportSubmitted event) {
        useCase.create(CreateWorkNotificationCommand.completionReportSubmitted(
                event.organizationId(),
                event.workId(),
                event.workName(),
                event.registrarMembershipId(),
                event.technicianId(),
                event.occurredAt()));
    }
}
