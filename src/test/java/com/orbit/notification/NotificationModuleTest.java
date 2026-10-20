package com.orbit.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.notification.application.port.in.query.ListNotificationsUseCase;
import com.orbit.notification.application.port.in.query.dto.ListNotificationsQuery;
import com.orbit.notification.application.port.in.query.dto.NotificationInfo;
import com.orbit.notification.application.port.in.query.dto.NotificationPageInfo;
import com.orbit.notification.application.port.out.PushNotificationPort;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationType;
import com.orbit.organization.OrganizationAccountLookup;
import com.orbit.schedule.WorkAssigned;
import com.orbit.schedule.WorkReassigned;
import com.orbit.schedule.WorkRejected;
import com.orbit.support.SharedContainers;

/**
 * notification만 띄워 schedule 이벤트가 커밋 뒤 알림이 되는지 확인한다. 계정 식별자를 테스트마다 달리해 같은 DB의 다른 테스트 행과 섞이지 않게 한다.
 * 푸시 출력 포트는 호출 여부를 보려고 대체한다.
 */
@ApplicationModuleTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class NotificationModuleTest implements SharedContainers {

    private static final Instant START = Instant.parse("2026-10-21T00:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-19T01:00:00Z");

    // organization 공개 계약. 조립 테스트는 notification만 띄우므로 대체하며, 넣지 않으면 받을 계정이 없다.
    @MockitoBean
    private OrganizationAccountLookup accountLookup;

    @MockitoBean
    private PushNotificationPort pushNotificationPort;

    @Autowired
    private ListNotificationsUseCase listNotificationsUseCase;

    @Test
    void recordsAndPushesAssignmentAfterScheduleCommit(Scenario scenario) {
        when(accountLookup.findActiveTechnicianAccountId(100L, 3L)).thenReturn(Optional.of(9_201L));

        scenario.publish(new WorkAssigned(10L, 100L, 3L, "보일러 점검", START, OCCURRED_AT))
                .andWaitForStateChange(() -> visible(9_201L), page -> page.totalCount() > 0)
                .andVerify(
                        page -> assertThat(page.notifications()).singleElement().satisfies(notification -> {
                            assertThat(notification.type()).isEqualTo(NotificationType.WORK_ASSIGNED);
                            assertThat(notification.organizationId()).isEqualTo(100L);
                            assertThat(notification.workId()).isEqualTo(10L);
                            assertThat(notification.message()).isEqualTo("10월 21일 09:00 [보일러 점검] 작업이 배정되었습니다.");
                            assertThat(notification.read()).isFalse();
                        }));
        verify(pushNotificationPort).send(any(Notification.class));
    }

    @Test
    void rejectionNotifiesRegistrarWithoutPushAndHidesTechnicianNotifications(Scenario scenario) {
        when(accountLookup.findActiveTechnicianAccountId(101L, 4L)).thenReturn(Optional.of(9_202L));
        when(accountLookup.findActiveStaffAccountId(101L, 6L)).thenReturn(Optional.of(9_203L));
        scenario.publish(new WorkAssigned(11L, 101L, 4L, "배관 설치", START, OCCURRED_AT))
                .andWaitForStateChange(() -> visible(9_202L), page -> page.totalCount() > 0)
                .andVerify(page -> assertThat(page.notifications()).hasSize(1));

        scenario.publish(new WorkRejected(
                        11L, 101L, 6L, 4L, "배관 설치", "SCHEDULE_CONFLICT", null, OCCURRED_AT.plusSeconds(60)))
                .andWaitForStateChange(() -> visible(9_203L), page -> page.totalCount() > 0)
                .andVerify(page -> assertThat(page.notifications())
                        .extracting(NotificationInfo::message)
                        .containsExactly("담당 기사가 [배관 설치] 작업을 거절했습니다. 사유: 일정이 맞지 않습니다"));

        assertThat(visible(9_202L).notifications()).isEmpty();
        verify(pushNotificationPort, never())
                .send(argThat(notification -> notification.recipientAccountId().equals(9_203L)));
    }

    @Test
    void reassignmentNotifiesNewTechnicianAndPreviousTechnicianWhoAccepted(Scenario scenario) {
        when(accountLookup.findActiveTechnicianAccountId(102L, 7L)).thenReturn(Optional.of(9_204L));
        when(accountLookup.findActiveTechnicianAccountId(102L, 8L)).thenReturn(Optional.of(9_205L));

        scenario.publish(new WorkReassigned(12L, 102L, 7L, 8L, true, "필터 교체", START, OCCURRED_AT))
                .andWaitForStateChange(
                        () -> visible(9_204L).totalCount() + visible(9_205L).totalCount(), total -> total == 2)
                .andVerify(total -> {
                    assertThat(visible(9_205L).notifications())
                            .extracting(NotificationInfo::message)
                            .containsExactly("10월 21일 09:00 [필터 교체] 작업이 배정되었습니다.");
                    assertThat(visible(9_204L).notifications())
                            .extracting(NotificationInfo::message)
                            .containsExactly("[필터 교체] 작업이 다른 기사에게 넘어갔습니다.");
                });
    }

    private NotificationPageInfo visible(Long accountId) {
        return listNotificationsUseCase.list(new ListNotificationsQuery(accountId, false, 0, 20));
    }
}
