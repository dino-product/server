package com.orbit.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.notification.NotificationCreated;
import com.orbit.notification.application.port.in.command.dto.CreateWorkNotificationCommand;
import com.orbit.notification.application.service.fake.FakeOrganizationAccountLookup;
import com.orbit.notification.application.service.fake.InMemoryNotificationRepository;
import com.orbit.notification.application.service.fake.InMemoryNotificationSettingRepository;
import com.orbit.notification.application.service.fake.RecordingPushNotificationPort;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationStatus;
import com.orbit.notification.domain.NotificationType;

@DisplayName("작업 알림 만들기")
class CreateWorkNotificationServiceTest {

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long WORK_ID = 10L;
    private static final String WORK_NAME = "보일러 점검";
    private static final Long TECHNICIAN_ID = 3L;
    private static final Long TECHNICIAN_ACCOUNT_ID = 30L;
    private static final Long REGISTRAR_ID = 5L;
    private static final Long REGISTRAR_ACCOUNT_ID = 50L;
    private static final Instant START = Instant.parse("2026-10-21T00:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-19T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-19T01:00:05Z");

    private final FakeOrganizationAccountLookup accounts = new FakeOrganizationAccountLookup()
            .technician(ORGANIZATION_ID, TECHNICIAN_ID, TECHNICIAN_ACCOUNT_ID)
            .staff(ORGANIZATION_ID, REGISTRAR_ID, REGISTRAR_ACCOUNT_ID);
    private final InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();
    private final InMemoryNotificationSettingRepository settings = new InMemoryNotificationSettingRepository();
    private final RecordingPushNotificationPort push = new RecordingPushNotificationPort();
    private final List<Object> published = new ArrayList<>();

    private CreateWorkNotificationService service;

    @BeforeEach
    void setUp() {
        service = new CreateWorkNotificationService(
                accounts, notifications, settings, push, published::add, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("배정 알림을 담당 기사 계정에 안 읽음으로 남기고 푸시하며 알림 저장을 발행한다")
    void recordsAndPushesAssignmentToTechnician() {
        service.create(assigned(TECHNICIAN_ID, OCCURRED_AT));

        assertThat(notifications.all()).singleElement().satisfies(notification -> {
            assertThat(notification.recipientAccountId()).isEqualTo(TECHNICIAN_ACCOUNT_ID);
            assertThat(notification.organizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(notification.workId()).contains(WORK_ID);
            assertThat(notification.type()).isEqualTo(NotificationType.WORK_ASSIGNED);
            assertThat(notification.message()).isEqualTo("10월 21일 09:00 [보일러 점검] 작업이 배정되었습니다.");
            assertThat(notification.status()).isEqualTo(NotificationStatus.UNREAD);
            assertThat(notification.notifiedAt()).isEqualTo(OCCURRED_AT);
        });
        assertThat(push.sent()).singleElement().satisfies(sent -> assertThat(sent.id())
                .isEqualTo(notifications.all().getFirst().id()));
        assertThat(published).containsExactly(new NotificationCreated(TECHNICIAN_ACCOUNT_ID, NOW));
    }

    @Test
    @DisplayName("같은 일이 다시 전달되면(이벤트 재전송) 알림도 푸시도 다시 만들지 않고, 다른 시각의 같은 종류 일은 새로 만든다")
    void doesNotRecreateNotificationForRedeliveredEvent() {
        service.create(assigned(TECHNICIAN_ID, OCCURRED_AT));
        service.create(assigned(TECHNICIAN_ID, OCCURRED_AT));

        assertThat(notifications.all()).hasSize(1);
        assertThat(push.sent()).hasSize(1);
        assertThat(published).hasSize(1);

        service.create(assigned(TECHNICIAN_ID, OCCURRED_AT.plusSeconds(60)));

        assertThat(notifications.all()).as("다시 배정된 것은 다른 일이다").hasSize(2);
        assertThat(push.sent()).hasSize(2);
        assertThat(published).hasSize(2);
    }

    @Test
    @DisplayName("받을 계정이 없으면(비활성·없는 기사 계약이나 소속, 담당 기사 없는 작업의 취소) 알림도 푸시도 만들지 않고 발행하지 않는다")
    void skipsWhenRecipientAccountIsMissing() {
        service.create(assigned(99L, OCCURRED_AT));
        service.create(assigned(null, OCCURRED_AT));
        service.create(
                CreateWorkNotificationCommand.workCancelled(ORGANIZATION_ID, WORK_ID, WORK_NAME, null, OCCURRED_AT));
        service.create(CreateWorkNotificationCommand.workAccepted(
                ORGANIZATION_ID, WORK_ID, WORK_NAME, 99L, TECHNICIAN_ID, OCCURRED_AT));

        assertThat(notifications.all()).isEmpty();
        assertThat(push.sent()).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("일정 변경은 기사가 수락한 작업일 때만 알리고 수락 전 작업의 변경은 알리지 않는다")
    void notifiesRescheduleOnlyForAcceptedWork() {
        service.create(rescheduled(false));

        assertThat(notifications.all()).isEmpty();
        assertThat(published).isEmpty();

        service.create(rescheduled(true));

        assertThat(notifications.all()).singleElement().satisfies(notification -> {
            assertThat(notification.type()).isEqualTo(NotificationType.WORK_RESCHEDULED);
            assertThat(notification.message()).isEqualTo("[보일러 점검] 작업 시간이 10월 21일 09:00으로 변경되었습니다.");
        });
        assertThat(push.sent()).hasSize(1);
    }

    @Test
    @DisplayName("다른 기사에게 넘어감은 이전 기사가 수락했던 작업일 때만 이전 기사에게 알린다")
    void notifiesReassignedAwayOnlyWhenPreviousTechnicianAccepted() {
        service.create(reassignedAway(false));

        assertThat(notifications.all()).isEmpty();

        service.create(reassignedAway(true));

        assertThat(notifications.all()).singleElement().satisfies(notification -> {
            assertThat(notification.recipientAccountId()).isEqualTo(TECHNICIAN_ACCOUNT_ID);
            assertThat(notification.type()).isEqualTo(NotificationType.WORK_REASSIGNED_AWAY);
            assertThat(notification.message()).isEqualTo("[보일러 점검] 작업이 다른 기사에게 넘어갔습니다.");
        });
    }

    @Test
    @DisplayName("기사가 끈 분류의 알림은 푸시하지 않고 알림센터에만 남긴다")
    void recordsWithoutPushWhenCategoryIsDisabled() {
        settings.put(TECHNICIAN_ACCOUNT_ID, false, false, false);

        service.create(assigned(TECHNICIAN_ID, OCCURRED_AT));
        service.create(rescheduled(true));
        service.create(reassignedAway(true));
        service.create(CreateWorkNotificationCommand.workCancelled(
                ORGANIZATION_ID, WORK_ID, WORK_NAME, TECHNICIAN_ID, OCCURRED_AT));

        assertThat(notifications.all())
                .extracting(Notification::type)
                .containsExactly(
                        NotificationType.WORK_ASSIGNED,
                        NotificationType.WORK_RESCHEDULED,
                        NotificationType.WORK_REASSIGNED_AWAY,
                        NotificationType.WORK_CANCELED);
        assertThat(push.sent()).isEmpty();
    }

    @Test
    @DisplayName("완료보고 요청은 알림 설정을 모두 꺼도 푸시한다")
    void alwaysPushesCompletionReportRequest() {
        settings.put(TECHNICIAN_ACCOUNT_ID, false, false, false);

        service.create(CreateWorkNotificationCommand.completionReportRequested(
                ORGANIZATION_ID, WORK_ID, WORK_NAME, TECHNICIAN_ID, OCCURRED_AT));

        assertThat(notifications.all()).singleElement().satisfies(notification -> assertThat(notification.message())
                .isEqualTo("[보일러 점검] 작업의 완료보고를 올려 주세요."));
        assertThat(push.sent()).hasSize(1);
    }

    @Test
    @DisplayName("수락·완료보고 제출은 등록한 직원 계정의 알림센터에만 남기고 푸시하지 않으며, 저장한 알림마다 발행한다")
    void recordsOfficeNotificationWithoutPush() {
        service.create(CreateWorkNotificationCommand.workAccepted(
                ORGANIZATION_ID, WORK_ID, WORK_NAME, REGISTRAR_ID, TECHNICIAN_ID, OCCURRED_AT));
        service.create(CreateWorkNotificationCommand.completionReportSubmitted(
                ORGANIZATION_ID, WORK_ID, WORK_NAME, REGISTRAR_ID, TECHNICIAN_ID, OCCURRED_AT));

        assertThat(notifications.all())
                .allSatisfy(notification ->
                        assertThat(notification.recipientAccountId()).isEqualTo(REGISTRAR_ACCOUNT_ID))
                .extracting(Notification::type)
                .containsExactly(NotificationType.WORK_ACCEPTED, NotificationType.COMPLETION_REPORT_SUBMITTED);
        assertThat(push.sent()).isEmpty();
        assertThat(published)
                .containsExactly(
                        new NotificationCreated(REGISTRAR_ACCOUNT_ID, NOW),
                        new NotificationCreated(REGISTRAR_ACCOUNT_ID, NOW));
    }

    @Test
    @DisplayName("거절하면 등록자에게 사유 문구와 함께 알리고 거절한 기사가 그 작업에 대해 거절 시점까지 받은 알림을 숨긴다")
    void hidesRejectedWorkNotificationsOfTechnician() {
        service.create(assigned(TECHNICIAN_ID, OCCURRED_AT));
        service.create(CreateWorkNotificationCommand.workAssigned(
                ORGANIZATION_ID, 11L, "다른 작업", TECHNICIAN_ID, START, OCCURRED_AT));

        service.create(rejected(REGISTRAR_ID, OCCURRED_AT.plusSeconds(60)));

        assertThat(notifications
                        .listVisible(TECHNICIAN_ACCOUNT_ID, false, 0, 20)
                        .notifications())
                .singleElement()
                .satisfies(visible -> assertThat(visible.workId()).contains(11L));
        assertThat(notifications.all())
                .filteredOn(notification ->
                        notification.workId().filter(WORK_ID::equals).isPresent()
                                && notification.isReceivedBy(TECHNICIAN_ACCOUNT_ID))
                .singleElement()
                .satisfies(hidden -> assertThat(hidden.isHidden()).isTrue());
        assertThat(notifications.listVisible(REGISTRAR_ACCOUNT_ID, false, 0, 20).notifications())
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.type()).isEqualTo(NotificationType.WORK_REJECTED);
                    assertThat(notification.message()).isEqualTo("담당 기사가 [보일러 점검] 작업을 거절했습니다. 사유: 일정이 맞지 않습니다");
                });
        assertThat(published)
                .containsExactly(
                        new NotificationCreated(TECHNICIAN_ACCOUNT_ID, NOW),
                        new NotificationCreated(TECHNICIAN_ACCOUNT_ID, NOW),
                        new NotificationCreated(REGISTRAR_ACCOUNT_ID, NOW));
    }

    @Test
    @DisplayName("거절한 기사 계정이 직원이던 때 그 작업에 대해 받은 사무실 알림은 숨기지 않는다")
    void keepsOfficeNotificationsOfRejectingTechnicianAccount() {
        accounts.staff(ORGANIZATION_ID, 7L, TECHNICIAN_ACCOUNT_ID);
        service.create(
                CreateWorkNotificationCommand.workAccepted(ORGANIZATION_ID, WORK_ID, WORK_NAME, 7L, 4L, OCCURRED_AT));
        service.create(assigned(TECHNICIAN_ID, OCCURRED_AT.plusSeconds(10)));

        service.create(rejected(REGISTRAR_ID, OCCURRED_AT.plusSeconds(60)));

        assertThat(notifications
                        .listVisible(TECHNICIAN_ACCOUNT_ID, false, 0, 20)
                        .notifications())
                .singleElement()
                .satisfies(visible -> assertThat(visible.type()).isEqualTo(NotificationType.WORK_ACCEPTED));
    }

    @Test
    @DisplayName("거절 뒤 같은 작업이 다시 배정돼 생긴 알림은 늦게 처리된 거절에도 숨기지 않는다")
    void keepsNotificationOfReassignmentAfterRejection() {
        service.create(assigned(TECHNICIAN_ID, OCCURRED_AT));
        service.create(assigned(TECHNICIAN_ID, OCCURRED_AT.plusSeconds(120)));

        service.create(rejected(REGISTRAR_ID, OCCURRED_AT.plusSeconds(60)));

        assertThat(notifications
                        .listVisible(TECHNICIAN_ACCOUNT_ID, false, 0, 20)
                        .notifications())
                .singleElement()
                .satisfies(visible -> assertThat(visible.notifiedAt()).isEqualTo(OCCURRED_AT.plusSeconds(120)));
    }

    @Test
    @DisplayName("등록자 계정이 없어도 거절한 기사의 알림은 숨기고, 숨김은 발행하지 않는다")
    void hidesTechnicianNotificationsEvenWithoutRegistrarAccount() {
        service.create(assigned(TECHNICIAN_ID, OCCURRED_AT));

        service.create(rejected(99L, OCCURRED_AT.plusSeconds(60)));

        assertThat(notifications.all()).singleElement().satisfies(notification -> assertThat(notification.isHidden())
                .isTrue());
        assertThat(published).containsExactly(new NotificationCreated(TECHNICIAN_ACCOUNT_ID, NOW));
    }

    private static CreateWorkNotificationCommand assigned(Long technicianId, Instant occurredAt) {
        return CreateWorkNotificationCommand.workAssigned(
                ORGANIZATION_ID, WORK_ID, WORK_NAME, technicianId, START, occurredAt);
    }

    private static CreateWorkNotificationCommand rescheduled(boolean accepted) {
        return CreateWorkNotificationCommand.workRescheduled(
                ORGANIZATION_ID, WORK_ID, WORK_NAME, TECHNICIAN_ID, accepted, START, OCCURRED_AT);
    }

    private static CreateWorkNotificationCommand reassignedAway(boolean previousAccepted) {
        return CreateWorkNotificationCommand.workReassignedAway(
                ORGANIZATION_ID, WORK_ID, WORK_NAME, TECHNICIAN_ID, previousAccepted, OCCURRED_AT);
    }

    private static CreateWorkNotificationCommand rejected(Long registrarMembershipId, Instant occurredAt) {
        return CreateWorkNotificationCommand.workRejected(
                ORGANIZATION_ID,
                WORK_ID,
                WORK_NAME,
                registrarMembershipId,
                TECHNICIAN_ID,
                "SCHEDULE_CONFLICT",
                null,
                occurredAt);
    }
}
