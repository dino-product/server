package com.orbit.notification.adapter.out.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.orbit.notification.application.port.out.PushMessage;
import com.orbit.notification.application.port.out.PushSendResult;
import com.orbit.notification.domain.NotificationDeliveryId;
import com.orbit.notification.domain.NotificationType;

/** FCM과 Google 토큰 엔드포인트는 로컬 스텁({@link FcmStub})이다. 실제 FCM 호출 경로(HTTP·JSON·서명)는 그대로 거친다. */
@DisplayName("FCM 푸시 발신")
class FcmPushSenderTest {

    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");
    private static final String DEVICE_TOKEN = "device-token-SECRET-abcdefghijklmnop";
    private static final PushMessage WORK_MESSAGE = new PushMessage(
            new NotificationDeliveryId(5L),
            10L,
            20L,
            NotificationType.WORK_ASSIGNED,
            "10월 28일 10:00 [에어컨 설치] 작업이 배정되었습니다.",
            30L,
            null,
            1);

    private FcmStub stub;
    private MutableClock clock;
    private FcmPushSender sender;

    @BeforeEach
    void setUp() throws Exception {
        stub = new FcmStub();
        clock = new MutableClock(NOW);
        sender = new FcmPushSender(
                new FcmProperties(true, stub.credentials(), stub.apiUri(), Duration.ofMillis(500)), clock);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    @DisplayName("알림 문구와 탭했을 때 이동할 데이터를 서비스 계정 토큰으로 기기 하나에 보낸다")
    void sendsMessageBodyAndDeepLinkDataToDevice() {
        PushSendResult result = sender.send(WORK_MESSAGE, DEVICE_TOKEN);

        assertThat(result).isEqualTo(new PushSendResult.Sent("projects/" + FcmStub.PROJECT_ID + "/messages/1"));
        FcmStub.SendRequest request = stub.sendRequests().getFirst();
        assertThat(request.authorization()).isEqualTo("Bearer stub-access-token-1");
        Map<String, Object> message = request.message();
        assertThat(message).containsEntry("token", DEVICE_TOKEN);
        assertThat(message.get("notification"))
                .as("Notion에 없는 제목은 넣지 않는다")
                .isEqualTo(Map.of("body", WORK_MESSAGE.message()));
        assertThat(message.get("data"))
                .isEqualTo(Map.of("notificationId", "10", "type", "WORK_ASSIGNED", "workId", "30"));
        assertThat(message.get("android"))
                .isEqualTo(Map.of("priority", "HIGH", "notification", Map.of("tag", "notification-10")));
        assertThat(message.get("apns"))
                .isEqualTo(Map.of(
                        "headers",
                        Map.of("apns-priority", "10", "apns-collapse-id", "notification-10"),
                        "payload",
                        Map.of("aps", Map.of("sound", "default"))));
    }

    @Test
    @DisplayName("참여 요청 결과 알림은 작업 대신 참여 요청 식별자를 데이터로 보낸다")
    void sendsParticipationRequestInData() {
        PushMessage participation = new PushMessage(
                new NotificationDeliveryId(6L),
                11L,
                20L,
                NotificationType.PARTICIPATION_REQUEST_APPROVED,
                "참여 요청이 승인되었습니다.",
                null,
                40L,
                1);

        sender.send(participation, DEVICE_TOKEN);

        assertThat(stub.sendRequests().getFirst().message().get("data"))
                .isEqualTo(Map.of(
                        "notificationId",
                        "11",
                        "type",
                        "PARTICIPATION_REQUEST_APPROVED",
                        "participationRequestId",
                        "40"));
    }

    @Test
    @DisplayName("access token은 만료 5분 전까지 다시 쓰고 그 뒤에 새로 받는다")
    void reusesAccessTokenUntilShortlyBeforeExpiry() {
        sender.send(WORK_MESSAGE, DEVICE_TOKEN);
        clock.advance(Duration.ofMinutes(54));
        sender.send(WORK_MESSAGE, DEVICE_TOKEN);

        assertThat(stub.issuedTokens()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        sender.send(WORK_MESSAGE, DEVICE_TOKEN);

        assertThat(stub.issuedTokens()).isEqualTo(2);
        assertThat(stub.sendRequests().getLast().authorization()).isEqualTo("Bearer stub-access-token-2");
    }

    @Test
    @DisplayName("FCM이 등록 해제로 답하거나 토큰 필드가 틀렸다고 하면 기기가 더 받지 않는 것으로 돌려준다")
    void reportsUnregisteredToken() {
        stub.respondToSend(404, fcmError(404, "NOT_FOUND", "UNREGISTERED"));
        stub.respondToSend(400, """
                {"error": {"code": 400, "status": "INVALID_ARGUMENT", "details": [
                  {"@type": "type.googleapis.com/google.rpc.BadRequest",
                   "fieldViolations": [{"field": "message.token", "description": "Invalid registration token"}]},
                  {"@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", "errorCode": "INVALID_ARGUMENT"}]}}
                """);

        assertThat(sender.send(WORK_MESSAGE, DEVICE_TOKEN)).isInstanceOf(PushSendResult.Unregistered.class);
        assertThat(sender.send(WORK_MESSAGE, DEVICE_TOKEN)).isInstanceOf(PushSendResult.Unregistered.class);
    }

    @Test
    @DisplayName("토큰이 아닌 요청 형식 오류는 다시 보내도 성공할 수 없는 실패다")
    void failsPermanentlyOnInvalidPayload() {
        stub.respondToSend(400, fcmError(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT"));

        assertThat(sender.send(WORK_MESSAGE, DEVICE_TOKEN))
                .isEqualTo(new PushSendResult.PermanentFailure("FCM 400 INVALID_ARGUMENT"));
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @CsvSource({
        "503, UNAVAILABLE, UNAVAILABLE",
        "500, INTERNAL, INTERNAL",
        "429, RESOURCE_EXHAUSTED, QUOTA_EXCEEDED",
        "401, UNAUTHENTICATED, THIRD_PARTY_AUTH_ERROR",
        "403, PERMISSION_DENIED, SENDER_ID_MISMATCH",
        "403, PERMISSION_DENIED, ''",
        "404, NOT_FOUND, ''"
    })
    @DisplayName("일시 오류와 인증·설정 오류는 다시 보낼 실패이고 기기를 지우게 하지 않는다")
    void retriesProviderAndConfigurationErrors(int status, String errorStatus, String errorCode) {
        stub.respondToSend(status, fcmError(status, errorStatus, errorCode));

        PushSendResult result = sender.send(WORK_MESSAGE, DEVICE_TOKEN);

        assertThat(result).isInstanceOf(PushSendResult.RetryableFailure.class);
        assertThat(((PushSendResult.RetryableFailure) result).reason())
                .startsWith("FCM " + status)
                .contains(errorCode.isEmpty() ? errorStatus : errorCode);
    }

    @Test
    @DisplayName("access token을 받지 못하면 FCM을 부르지 않고 다시 보낼 실패로 돌려준다")
    void retriesWithoutSendingWhenAccessTokenIsRejected() {
        stub.respondToToken(400, "{\"error\":\"invalid_grant\"}");

        PushSendResult result = sender.send(WORK_MESSAGE, DEVICE_TOKEN);

        assertThat(result)
                .isEqualTo(new PushSendResult.RetryableFailure(
                        "FCM access token request rejected: status=400, error=invalid_grant"));
        assertThat(stub.sendRequests()).isEmpty();
    }

    @Test
    @DisplayName("응답이 제한 시간 안에 오지 않으면 다시 보낼 실패로 돌려준다")
    void retriesWhenSendTimesOut() {
        stub.delaySend(Duration.ofSeconds(2));

        assertThat(sender.send(WORK_MESSAGE, DEVICE_TOKEN)).isInstanceOf(PushSendResult.RetryableFailure.class);
    }

    @Test
    @DisplayName("실패 사유에는 기기 토큰·알림 문구·access token을 남기지 않는다")
    void keepsSecretsOutOfFailureReasons() {
        stub.respondToSend(
                400,
                "{\"error\": {\"code\": 400, \"status\": \"INVALID_ARGUMENT\", \"message\": \"bad " + DEVICE_TOKEN
                        + "\"}}");

        PushSendResult result = sender.send(WORK_MESSAGE, DEVICE_TOKEN);

        assertThat(result.toString())
                .doesNotContain("SECRET")
                .doesNotContain("에어컨")
                .doesNotContain("stub-access-token");
    }

    private static String fcmError(int status, String errorStatus, String errorCode) {
        String details = errorCode.isEmpty()
                ? "[]"
                : "[{\"@type\": \"type.googleapis.com/google.firebase.fcm.v1.FcmError\", \"errorCode\": \"" + errorCode
                        + "\"}]";
        return "{\"error\": {\"code\": " + status + ", \"message\": \"error\", \"status\": \"" + errorStatus
                + "\", \"details\": " + details + "}}";
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
