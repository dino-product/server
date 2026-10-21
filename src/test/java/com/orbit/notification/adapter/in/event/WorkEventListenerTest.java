package com.orbit.notification.adapter.in.event;

import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.notification.application.port.in.command.CreateWorkNotificationUseCase;
import com.orbit.notification.application.port.in.command.dto.CreateWorkNotificationCommand;
import com.orbit.notification.domain.NotificationType;
import com.orbit.schedule.CompletionReportRequested;
import com.orbit.schedule.CompletionReportSubmitted;
import com.orbit.schedule.WorkAccepted;
import com.orbit.schedule.WorkAssigned;
import com.orbit.schedule.WorkCancelled;
import com.orbit.schedule.WorkReassigned;
import com.orbit.schedule.WorkRejected;
import com.orbit.schedule.WorkRescheduled;

/**
 * 이벤트 값을 Command로 옮기는지만 본다. 수락 여부에 따라 알림을 만들지는 서비스 테스트가, 커밋 뒤 다른 스레드에서 처리하는지는 {@code NotificationModuleTest}가
 * 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("작업 이벤트 리스너")
class WorkEventListenerTest {

    private static final Instant START = Instant.parse("2026-10-21T00:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-19T01:00:00Z");

    @Mock
    private CreateWorkNotificationUseCase useCase;

    @Test
    @DisplayName("기사 대상 이벤트는 담당 기사 계약과 수락 여부를 담은 Command가 된다")
    void convertsTechnicianEvents() {
        WorkEventListener listener = new WorkEventListener(useCase);

        listener.on(new WorkAssigned(10L, 1L, 3L, "보일러 점검", START, OCCURRED_AT));
        listener.on(new WorkRescheduled(10L, 1L, 3L, true, "보일러 점검", START, OCCURRED_AT));
        listener.on(new WorkCancelled(10L, 1L, null, "보일러 점검", OCCURRED_AT));
        listener.on(new CompletionReportRequested(10L, 1L, 3L, "보일러 점검", OCCURRED_AT));

        verify(useCase).create(command(NotificationType.WORK_ASSIGNED, 3L, false, START));
        verify(useCase).create(command(NotificationType.WORK_RESCHEDULED, 3L, true, START));
        verify(useCase).create(command(NotificationType.WORK_CANCELED, null, false, null));
        verify(useCase).create(command(NotificationType.COMPLETION_REPORT_REQUESTED, 3L, false, null));
    }

    @Test
    @DisplayName("재배정은 새 기사의 배정 Command와 이전 기사의 넘어감 Command(이전 수락 여부 포함)가 된다")
    void convertsReassignmentToNewAndPreviousTechnicianCommands() {
        WorkEventListener listener = new WorkEventListener(useCase);

        listener.on(new WorkReassigned(10L, 1L, 4L, 3L, true, "보일러 점검", START, OCCURRED_AT));

        verify(useCase).create(command(NotificationType.WORK_ASSIGNED, 3L, false, START));
        verify(useCase).create(command(NotificationType.WORK_REASSIGNED_AWAY, 4L, true, null));
    }

    @Test
    @DisplayName("사무실 대상 이벤트는 등록자 소속으로 받는 사람을 정하고 거절은 사유 코드·메모와 기사를 함께 담는다")
    void convertsOfficeEvents() {
        WorkEventListener listener = new WorkEventListener(useCase);

        listener.on(new WorkAccepted(10L, 1L, 5L, 3L, "보일러 점검", OCCURRED_AT));
        listener.on(new WorkRejected(10L, 1L, 5L, 3L, "보일러 점검", "OTHER", "자재가 없습니다", OCCURRED_AT));
        listener.on(new CompletionReportSubmitted(10L, 1L, 5L, 3L, "보일러 점검", OCCURRED_AT));

        verify(useCase).create(officeCommand(NotificationType.WORK_ACCEPTED, null, null));
        verify(useCase).create(officeCommand(NotificationType.WORK_REJECTED, "OTHER", "자재가 없습니다"));
        verify(useCase).create(officeCommand(NotificationType.COMPLETION_REPORT_SUBMITTED, null, null));
    }

    private static CreateWorkNotificationCommand command(
            NotificationType type, Long technicianId, boolean accepted, Instant scheduledStartAt) {
        return new CreateWorkNotificationCommand(
                type, 1L, 10L, "보일러 점검", technicianId, accepted, null, scheduledStartAt, null, null, OCCURRED_AT);
    }

    private static CreateWorkNotificationCommand officeCommand(NotificationType type, String code, String note) {
        return new CreateWorkNotificationCommand(type, 1L, 10L, "보일러 점검", 3L, false, 5L, null, code, note, OCCURRED_AT);
    }
}
