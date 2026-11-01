package com.orbit.notification.adapter.out.fcm;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.orbit.notification.application.port.out.PushMessage;
import com.orbit.notification.application.port.out.PushSendResult;
import com.orbit.notification.application.port.out.PushSender;

import lombok.extern.slf4j.Slf4j;

/**
 * FCM HTTP v1로 기기 하나에 푸시를 보낸다. {@code app.notification.push.fcm.enabled=true}일 때만 등록하며, 서비스 계정 키를 처음에 해석해 틀리면 기동이
 * 실패한다.
 *
 * <p>표시는 알림 문구뿐이고(제목 없음, 기본 소리), 데이터에는 앱이 탭한 알림을 읽음으로 바꾸고 이동할 화면을 고를 알림 식별자·종류·관련 작업 또는 참여 요청을 담는다.
 * 같은 알림을 다시 보내도 기기에서 하나로 덮어쓰도록 Android tag와 APNs collapse id를 알림 식별자로 둔다.
 *
 * <p>FCM이 등록 해제({@code UNREGISTERED})로 답하거나 토큰 필드가 틀렸다고 하면 기기가 더 받지 않는 것이다. 토큰이 아닌 요청 형식 오류(400)는 다시 보내도
 * 성공할 수 없다. 그 밖의 응답(429·5xx·인증·발신자 불일치·주소 오류)과 통신 실패·제한 시간 초과는 고치거나 기다리면 성공할 수 있어 다시 보낼 실패다. 실패 사유에는
 * HTTP 상태와 FCM 오류 코드만 남긴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.notification.push.fcm", name = "enabled", havingValue = "true")
class FcmPushSender implements PushSender {

    private static final String FCM_ERROR = "type.googleapis.com/google.firebase.fcm.v1.FcmError";
    private static final String BAD_REQUEST = "type.googleapis.com/google.rpc.BadRequest";
    private static final String TOKEN_FIELD = "message.token";
    private static final String UNREGISTERED = "UNREGISTERED";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final FcmAccessTokens accessTokens;
    private final String sendUri;

    FcmPushSender(FcmProperties properties, Clock clock) {
        FcmServiceAccount account = FcmServiceAccount.fromBase64Json(properties.credentials());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.accessTokens = new FcmAccessTokens(account, restClient, clock);
        this.sendUri = properties.apiUri() + "/v1/projects/" + account.projectId() + "/messages:send";
    }

    @Override
    public PushSendResult send(PushMessage message, String deviceToken) {
        String accessToken;
        try {
            accessToken = accessTokens.get();
        } catch (FcmAccessTokenException exception) {
            log.warn("Cannot get FCM access token: {}", exception.getMessage());
            return new PushSendResult.RetryableFailure(exception.getMessage());
        }
        try {
            Map<String, Object> response = restClient
                    .post()
                    .uri(sendUri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", message(message, deviceToken)))
                    .retrieve()
                    .body(JSON_OBJECT);
            return new PushSendResult.Sent(
                    response != null && response.get("name") instanceof String name ? name : null);
        } catch (RestClientResponseException exception) {
            return classify(exception);
        } catch (RestClientException exception) {
            return new PushSendResult.RetryableFailure(
                    "FCM call failed: " + exception.getClass().getSimpleName());
        }
    }

    private static Map<String, Object> message(PushMessage message, String deviceToken) {
        String collapseKey = "notification-" + message.notificationId();
        Map<String, Object> fcmMessage = new LinkedHashMap<>();
        fcmMessage.put("token", deviceToken);
        fcmMessage.put("notification", Map.of("body", message.message()));
        fcmMessage.put("data", data(message));
        fcmMessage.put("android", Map.of("priority", "HIGH", "notification", Map.of("tag", collapseKey)));
        fcmMessage.put(
                "apns",
                Map.of(
                        "headers",
                        Map.of("apns-priority", "10", "apns-collapse-id", collapseKey),
                        "payload",
                        Map.of("aps", Map.of("sound", "default"))));
        return fcmMessage;
    }

    /** FCM 데이터 값은 문자열만 받는다. 없는 관련 대상은 넣지 않는다. */
    private static Map<String, String> data(PushMessage message) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notificationId", String.valueOf(message.notificationId()));
        data.put("type", message.type().name());
        if (message.workId() != null) {
            data.put("workId", String.valueOf(message.workId()));
        }
        if (message.participationRequestId() != null) {
            data.put("participationRequestId", String.valueOf(message.participationRequestId()));
        }
        return data;
    }

    private static PushSendResult classify(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        Map<?, ?> error = error(exception);
        String errorCode = fcmErrorCode(error);
        String reason = "FCM " + status + " " + (errorCode != null ? errorCode : text(error.get("status")));
        if (UNREGISTERED.equals(errorCode)) {
            return new PushSendResult.Unregistered(reason);
        }
        if (status == 400 && violatesTokenField(error)) {
            return new PushSendResult.Unregistered(reason + " (" + TOKEN_FIELD + ")");
        }
        if (status == 400) {
            return new PushSendResult.PermanentFailure(reason);
        }
        return new PushSendResult.RetryableFailure(reason);
    }

    private static Map<?, ?> error(RestClientResponseException exception) {
        try {
            Map<?, ?> body = exception.getResponseBodyAs(Map.class);
            return body != null && body.get("error") instanceof Map<?, ?> error ? error : Map.of();
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static String fcmErrorCode(Map<?, ?> error) {
        return details(error, FCM_ERROR).stream()
                .map(detail -> text(detail.get("errorCode")))
                .filter(code -> code != null)
                .findFirst()
                .orElse(null);
    }

    private static boolean violatesTokenField(Map<?, ?> error) {
        return details(error, BAD_REQUEST).stream()
                .filter(detail -> detail.get("fieldViolations") instanceof List<?>)
                .flatMap(detail -> ((List<?>) detail.get("fieldViolations")).stream())
                .anyMatch(violation -> violation instanceof Map<?, ?> field && TOKEN_FIELD.equals(field.get("field")));
    }

    private static List<Map<?, ?>> details(Map<?, ?> error, String type) {
        if (!(error.get("details") instanceof List<?> details)) {
            return List.of();
        }
        return details.stream()
                .filter(detail -> detail instanceof Map<?, ?> map && type.equals(map.get("@type")))
                .<Map<?, ?>>map(detail -> (Map<?, ?>) detail)
                .toList();
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
