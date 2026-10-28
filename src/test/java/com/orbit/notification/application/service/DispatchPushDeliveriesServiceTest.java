package com.orbit.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.notification.application.port.in.command.dto.DispatchPushDeliveriesCommand;
import com.orbit.notification.application.port.out.NotificationDeliveryRepository;
import com.orbit.notification.application.port.out.NotificationRepository;
import com.orbit.notification.application.port.out.PushMessage;
import com.orbit.notification.application.port.out.PushSendResult;
import com.orbit.notification.application.port.out.PushSender;
import com.orbit.notification.application.service.fake.InMemoryPushDeviceRepository;
import com.orbit.notification.domain.DeliveryChannel;
import com.orbit.notification.domain.DeliveryStatus;
import com.orbit.notification.domain.Notification;
import com.orbit.notification.domain.NotificationDelivery;
import com.orbit.notification.domain.NotificationDeliveryId;
import com.orbit.notification.domain.NotificationStatus;
import com.orbit.notification.domain.NotificationType;
import com.orbit.notification.domain.PushDevice;
import com.orbit.notification.domain.PushPlatform;
import com.orbit.notification.domain.RetryBackoff;

@ExtendWith(MockitoExtension.class)
@DisplayName("기사 앱 푸시 발신 대기열 처리")
class DispatchPushDeliveriesServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final Duration IN_FLIGHT_TIMEOUT = Duration.ofMinutes(5);
    private static final DispatchPushDeliveriesCommand COMMAND = new DispatchPushDeliveriesCommand(
            100, IN_FLIGHT_TIMEOUT, new RetryBackoff(Duration.ofSeconds(30), Duration.ofMinutes(30)));
    private static final Long RECIPIENT = 200L;

    @Mock
    private NotificationDeliveryRepository deliveries;

    @Mock
    private NotificationRepository notifications;

    @Mock
    private PushSender pushSender;

    private final InMemoryPushDeviceRepository devices = new InMemoryPushDeviceRepository();

    private DispatchPushDeliveriesService service;

    @BeforeEach
    void setUp() {
        service = new DispatchPushDeliveriesService(
                deliveries, notifications, devices, pushSender, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("보낼 때가 된 발신을 점유 만료 시각과 함께 집어 수신 계정의 기기마다 보낸다")
    void claimsDueDeliveriesWithLeaseAndSendsToEveryDevice() {
        registerDevice(RECIPIENT, "token-a");
        registerDevice(RECIPIENT, "token-b");
        registerDevice(999L, "token-of-other-account");
        NotificationDelivery first = claimed(1L, 1);
        NotificationDelivery second = claimed(2L, 1);
        when(deliveries.claimDue(NOW, NOW.plus(IN_FLIGHT_TIMEOUT), 100)).thenReturn(List.of(first, second));
        when(notifications.findById(100L)).thenReturn(Optional.of(notification(false)));
        when(pushSender.send(any(), anyString())).thenReturn(new PushSendResult.Sent("provider-1"));
        when(deliveries.saveResult(any())).thenReturn(true);

        service.dispatch(COMMAND);

        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(pushSender, times(4)).send(messages.capture(), tokens.capture());
        assertThat(tokens.getAllValues()).containsExactly("token-a", "token-b", "token-a", "token-b");
        assertThat(messages.getAllValues())
                .extracting(PushMessage::deliveryId)
                .containsExactly(
                        new NotificationDeliveryId(1L),
                        new NotificationDeliveryId(1L),
                        new NotificationDeliveryId(2L),
                        new NotificationDeliveryId(2L));
        PushMessage message = messages.getAllValues().getFirst();
        assertThat(message.notificationId()).isEqualTo(100L);
        assertThat(message.recipientAccountId()).isEqualTo(RECIPIENT);
        assertThat(message.type()).isEqualTo(NotificationType.WORK_ASSIGNED);
        assertThat(message.message()).as("문구는 보내기 직전에 알림에서 읽는다").isEqualTo("배정 알림");
        assertThat(message.workId()).isEqualTo(300L);
        assertThat(message.participationRequestId()).isNull();
    }

    @Test
    @DisplayName("보내면 발신 완료와 제공자 메시지 식별자를 저장한다")
    void savesSentResult() {
        registerDevice(RECIPIENT, "token-a");

        NotificationDelivery saved = dispatchOne(claimed(1L, 1), new PushSendResult.Sent("provider-1"));

        assertThat(saved.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(saved.providerMessageId()).contains("provider-1");
        assertThat(saved.sentAt()).contains(NOW);
    }

    @Test
    @DisplayName("다시 보낼 실패는 시도 횟수 제한 없이 대기 시간 뒤로 다시 미룬다")
    void retriesRetryableFailureWithoutAttemptLimit() {
        registerDevice(RECIPIENT, "token-a");

        NotificationDelivery firstRetry = dispatchOne(claimed(1L, 1), new PushSendResult.RetryableFailure("제공자 일시 오류"));

        assertThat(firstRetry.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(firstRetry.lastError()).contains("제공자 일시 오류");
        assertThat(firstRetry.nextAttemptAt()).isBetween(NOW.plusSeconds(24), NOW.plusSeconds(30));

        NotificationDelivery manyAttempts =
                dispatchOne(claimed(2L, 1_000), new PushSendResult.RetryableFailure("제공자 인증 오류"));

        assertThat(manyAttempts.status()).as("시도가 아무리 많아도 포기하지 않는다").isEqualTo(DeliveryStatus.PENDING);
        assertThat(manyAttempts.nextAttemptAt())
                .isBetween(NOW.plus(Duration.ofMinutes(24)), NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("발신 어댑터가 예외를 던지면 다시 보낼 실패로 본다")
    void treatsSenderExceptionAsRetryable() {
        registerDevice(RECIPIENT, "token-a");
        when(deliveries.claimDue(any(), any(), anyInt())).thenReturn(List.of(claimed(1L, 1)));
        when(notifications.findById(100L)).thenReturn(Optional.of(notification(false)));
        when(pushSender.send(any(), anyString())).thenThrow(new IllegalStateException("연결 끊김"));
        when(deliveries.saveResult(any())).thenReturn(true);

        service.dispatch(COMMAND);

        NotificationDelivery saved = savedResult();
        assertThat(saved.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(saved.lastError())
                .hasValueSatisfying(error -> assertThat(error).contains("연결 끊김"));
        assertThat(devices.findByToken("token-a")).as("예외로는 기기를 지우지 않는다").isPresent();
    }

    @Test
    @DisplayName("다시 보내도 성공할 수 없는 실패는 실패로 끝낸다")
    void endsPermanentFailure() {
        registerDevice(RECIPIENT, "token-a");

        NotificationDelivery failed = dispatchOne(claimed(1L, 1), new PushSendResult.PermanentFailure("요청 형식 오류"));

        assertThat(failed.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(failed.lastError()).contains("요청 형식 오류");
        assertThat(devices.findByToken("token-a")).isPresent();
    }

    @Test
    @DisplayName("등록된 기기가 없으면 제공자를 부르지 않고 건너뜀으로 끝낸다")
    void skipsWithoutSendingWhenNoDeviceIsRegistered() {
        registerDevice(999L, "token-of-other-account");
        when(deliveries.claimDue(any(), any(), anyInt())).thenReturn(List.of(claimed(1L, 1)));
        when(notifications.findById(100L)).thenReturn(Optional.of(notification(false)));
        when(deliveries.saveResult(any())).thenReturn(true);

        service.dispatch(COMMAND);

        NotificationDelivery saved = savedResult();
        assertThat(saved.status()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(saved.lastError()).contains("no registered device");
        verify(pushSender, never()).send(any(), anyString());
    }

    @Test
    @DisplayName("제공자가 받지 않는 토큰의 기기는 지우고, 남은 기기가 없으면 건너뜀으로 끝낸다")
    void removesUnregisteredDeviceAndSkipsWhenNoneReceived() {
        registerDevice(RECIPIENT, "token-a");

        NotificationDelivery skipped = dispatchOne(claimed(1L, 1), new PushSendResult.Unregistered("UNREGISTERED"));

        assertThat(skipped.status()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(devices.findByToken("token-a")).isEmpty();
    }

    @Test
    @DisplayName("한 기기라도 받으면 다른 기기가 실패해도 보낸 것으로 끝내고 받지 않는 토큰의 기기는 지운다")
    void countsAsSentWhenAnyDeviceReceives() {
        registerDevice(RECIPIENT, "token-gone");
        registerDevice(RECIPIENT, "token-busy");
        registerDevice(RECIPIENT, "token-ok");
        when(deliveries.claimDue(any(), any(), anyInt())).thenReturn(List.of(claimed(1L, 1)));
        when(notifications.findById(100L)).thenReturn(Optional.of(notification(false)));
        when(pushSender.send(any(), eq("token-gone"))).thenReturn(new PushSendResult.Unregistered("UNREGISTERED"));
        when(pushSender.send(any(), eq("token-busy"))).thenReturn(new PushSendResult.RetryableFailure("UNAVAILABLE"));
        when(pushSender.send(any(), eq("token-ok"))).thenReturn(new PushSendResult.Sent("provider-ok"));
        when(deliveries.saveResult(any())).thenReturn(true);

        service.dispatch(COMMAND);

        NotificationDelivery saved = savedResult();
        assertThat(saved.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(saved.providerMessageId()).contains("provider-ok");
        assertThat(devices.findByToken("token-gone")).isEmpty();
        assertThat(devices.findByToken("token-busy")).isPresent();
    }

    @Test
    @DisplayName("받은 기기가 없으면 다시 보낼 실패가 하나라도 있을 때 다시 보내고, 없으면 실패로 끝낸다")
    void retriesWhenAnyDeviceMayReceiveLaterOtherwiseFails() {
        registerDevice(RECIPIENT, "token-gone");
        registerDevice(RECIPIENT, "token-busy");
        when(deliveries.claimDue(any(), any(), anyInt())).thenReturn(List.of(claimed(1L, 1)));
        when(notifications.findById(100L)).thenReturn(Optional.of(notification(false)));
        when(pushSender.send(any(), eq("token-gone"))).thenReturn(new PushSendResult.Unregistered("UNREGISTERED"));
        when(pushSender.send(any(), eq("token-busy"))).thenReturn(new PushSendResult.RetryableFailure("UNAVAILABLE"));
        when(deliveries.saveResult(any())).thenReturn(true);

        service.dispatch(COMMAND);

        NotificationDelivery retried = savedResult();
        assertThat(retried.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(retried.lastError()).contains("UNAVAILABLE");
        assertThat(devices.findByToken("token-gone")).isEmpty();

        registerDevice(RECIPIENT, "token-bad-payload");
        reset(deliveries, pushSender);
        when(deliveries.claimDue(any(), any(), anyInt())).thenReturn(List.of(claimed(2L, 1)));
        when(pushSender.send(any(), eq("token-busy"))).thenReturn(new PushSendResult.Unregistered("UNREGISTERED"));
        when(pushSender.send(any(), eq("token-bad-payload")))
                .thenReturn(new PushSendResult.PermanentFailure("INVALID_ARGUMENT"));
        when(deliveries.saveResult(any())).thenReturn(true);

        service.dispatch(COMMAND);

        NotificationDelivery failed = savedResult();
        assertThat(failed.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(failed.lastError()).contains("INVALID_ARGUMENT");
    }

    @Test
    @DisplayName("기다리는 동안 거절로 숨겨진 알림은 보내지 않고 건너뛰고, 알림이 없으면 실패로 끝낸다")
    void skipsHiddenAndFailsMissingNotification() {
        registerDevice(RECIPIENT, "token-a");

        NotificationDelivery hidden = dispatchWithout(claimed(1L, 1), Optional.of(notification(true)));
        NotificationDelivery missing = dispatchWithout(claimed(2L, 1), Optional.empty());

        assertThat(hidden.status()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(missing.status()).isEqualTo(DeliveryStatus.FAILED);
        verify(pushSender, never()).send(any(), anyString());
    }

    @Test
    @DisplayName("보낼 발신이 없으면 보내지 않는다")
    void doesNothingWhenNothingIsDue() {
        when(deliveries.claimDue(any(), any(), anyInt())).thenReturn(List.of());

        service.dispatch(COMMAND);

        verify(pushSender, never()).send(any(), anyString());
        verify(deliveries, never()).saveResult(any());
    }

    private NotificationDelivery dispatchWithout(NotificationDelivery claimed, Optional<Notification> notification) {
        reset(deliveries, notifications);
        when(deliveries.claimDue(any(), any(), anyInt())).thenReturn(List.of(claimed));
        when(notifications.findById(100L)).thenReturn(notification);
        when(deliveries.saveResult(any())).thenReturn(true);

        service.dispatch(COMMAND);

        return savedResult();
    }

    private NotificationDelivery dispatchOne(NotificationDelivery claimed, PushSendResult result) {
        reset(deliveries, notifications, pushSender);
        when(deliveries.claimDue(any(), any(), anyInt())).thenReturn(List.of(claimed));
        when(notifications.findById(100L)).thenReturn(Optional.of(notification(false)));
        when(pushSender.send(any(), anyString())).thenReturn(result);
        when(deliveries.saveResult(any())).thenReturn(true);

        service.dispatch(COMMAND);

        NotificationDelivery saved = savedResult();
        assertThat(saved.attempts()).as("결과 저장은 집은 시점의 시도 횟수를 유지한다").isEqualTo(claimed.attempts());
        return saved;
    }

    private NotificationDelivery savedResult() {
        ArgumentCaptor<NotificationDelivery> saved = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries).saveResult(saved.capture());
        return saved.getValue();
    }

    private void registerDevice(Long accountId, String token) {
        devices.register(PushDevice.register(accountId, PushPlatform.ANDROID, token, NOW));
    }

    private static Notification notification(boolean hidden) {
        return Notification.reconstitute(
                100L,
                RECIPIENT,
                1L,
                NotificationType.WORK_ASSIGNED,
                300L,
                null,
                "배정 알림",
                NOW,
                NotificationStatus.UNREAD,
                null,
                hidden);
    }

    private static NotificationDelivery claimed(Long id, int attempts) {
        return NotificationDelivery.reconstitute(
                new NotificationDeliveryId(id),
                100L,
                RECIPIENT,
                DeliveryChannel.PUSH,
                DeliveryStatus.IN_FLIGHT,
                attempts,
                NOW.plus(IN_FLIGHT_TIMEOUT),
                null,
                null,
                null);
    }
}
