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
import com.orbit.notification.application.port.in.command.dto.CreateParticipationNotificationCommand;
import com.orbit.notification.application.service.fake.FakeParticipationRequestLookup;
import com.orbit.notification.application.service.fake.InMemoryNotificationRepository;
import com.orbit.notification.application.service.fake.InMemoryNotificationSettingRepository;
import com.orbit.notification.application.service.fake.RecordingPushNotificationPort;
import com.orbit.notification.domain.NotificationSetting;
import com.orbit.notification.domain.NotificationStatus;
import com.orbit.notification.domain.NotificationType;
import com.orbit.notification.domain.ParticipationType;

@DisplayName("참여 요청 결과 알림 만들기")
class CreateParticipationNotificationServiceTest {

    private static final Long REQUEST_ID = 31L;
    private static final Long ORGANIZATION_ID = 1L;
    private static final Long APPLICANT_ACCOUNT_ID = 70L;
    private static final Instant DECIDED_AT = Instant.parse("2026-10-27T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-27T01:00:05Z");

    private final FakeParticipationRequestLookup requests = new FakeParticipationRequestLookup();
    private final InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();
    private final InMemoryNotificationSettingRepository settings = new InMemoryNotificationSettingRepository();
    private final RecordingPushNotificationPort push = new RecordingPushNotificationPort();
    private final List<Object> published = new ArrayList<>();

    private CreateParticipationNotificationService service;

    @BeforeEach
    void setUp() {
        service = new CreateParticipationNotificationService(
                requests, notifications, push, published::add, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("승인 알림을 요청자 계정에 관련 참여 요청과 함께 안 읽음으로 남기고 푸시하며 알림 저장을 발행한다")
    void recordsAndPushesApprovalToApplicant() {
        service.create(approved(ParticipationType.TECHNICIAN, ParticipationType.TECHNICIAN));

        assertThat(notifications.all()).singleElement().satisfies(notification -> {
            assertThat(notification.recipientAccountId()).isEqualTo(APPLICANT_ACCOUNT_ID);
            assertThat(notification.organizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(notification.type()).isEqualTo(NotificationType.PARTICIPATION_REQUEST_APPROVED);
            assertThat(notification.participationRequestId()).contains(REQUEST_ID);
            assertThat(notification.workId()).isEmpty();
            assertThat(notification.message()).isEqualTo("참여 요청이 승인되었습니다.");
            assertThat(notification.status()).isEqualTo(NotificationStatus.UNREAD);
            assertThat(notification.notifiedAt()).isEqualTo(DECIDED_AT);
        });
        assertThat(push.sent()).singleElement().satisfies(sent -> assertThat(sent.id())
                .isEqualTo(notifications.all().getFirst().id()));
        assertThat(published).containsExactly(new NotificationCreated(APPLICANT_ACCOUNT_ID, NOW));
    }

    @Test
    @DisplayName("확정 유형이 희망 유형과 다르면 승인 문구에 희망 → 확정 유형을 덧붙인다")
    void addsTypeChangeToApprovalMessage() {
        service.create(approved(ParticipationType.TECHNICIAN, ParticipationType.STAFF));

        assertThat(notifications.all()).singleElement().satisfies(notification -> assertThat(notification.message())
                .isEqualTo("참여 요청이 승인되었습니다. 참여 유형: 기사 → 직원"));
    }

    @Test
    @DisplayName("거절 알림은 이벤트에 없는 거절 사유를 참여 요청 조회로 읽어 덧붙이고 푸시한다")
    void readsRejectReasonForRejectionMessage() {
        requests.rejectReason(REQUEST_ID, "인원 마감");

        service.create(rejected());

        assertThat(notifications.all()).singleElement().satisfies(notification -> {
            assertThat(notification.type()).isEqualTo(NotificationType.PARTICIPATION_REQUEST_REJECTED);
            assertThat(notification.participationRequestId()).contains(REQUEST_ID);
            assertThat(notification.message()).isEqualTo("참여 요청이 거절되었습니다. 사유: 인원 마감");
        });
        assertThat(push.sent()).hasSize(1);
    }

    @Test
    @DisplayName("사유 없이 거절됐으면 거절 문구에 사유를 쓰지 않는다")
    void omitsMissingRejectReason() {
        service.create(rejected());

        assertThat(notifications.all()).singleElement().satisfies(notification -> assertThat(notification.message())
                .isEqualTo("참여 요청이 거절되었습니다."));
    }

    @Test
    @DisplayName("요청자가 알림 설정을 모두 꺼도 참여 요청 결과는 푸시한다")
    void pushesRegardlessOfNotificationSetting() {
        NotificationSetting allOff = NotificationSetting.defaults(APPLICANT_ACCOUNT_ID);
        allOff.change(false, false, false);
        settings.save(allOff);

        service.create(approved(ParticipationType.STAFF, ParticipationType.STAFF));
        service.create(rejected());

        assertThat(push.sent()).hasSize(2);
    }

    @Test
    @DisplayName("같은 참여 요청의 결과가 다시 전달되면(이벤트 재전송) 알림도 푸시도 다시 만들지 않고 발행하지 않는다")
    void doesNotRecreateNotificationForRedeliveredEvent() {
        service.create(rejected());
        service.create(rejected());

        assertThat(notifications.all()).hasSize(1);
        assertThat(push.sent()).hasSize(1);
        assertThat(published).containsExactly(new NotificationCreated(APPLICANT_ACCOUNT_ID, NOW));
    }

    private static CreateParticipationNotificationCommand approved(
            ParticipationType desiredType, ParticipationType confirmedType) {
        return CreateParticipationNotificationCommand.approved(
                REQUEST_ID, ORGANIZATION_ID, APPLICANT_ACCOUNT_ID, desiredType, confirmedType, DECIDED_AT);
    }

    private static CreateParticipationNotificationCommand rejected() {
        return CreateParticipationNotificationCommand.rejected(
                REQUEST_ID, ORGANIZATION_ID, APPLICANT_ACCOUNT_ID, DECIDED_AT);
    }
}
