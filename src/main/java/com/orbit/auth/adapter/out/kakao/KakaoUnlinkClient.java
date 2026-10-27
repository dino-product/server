package com.orbit.auth.adapter.out.kakao;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.orbit.auth.application.port.out.KakaoUnlinkException;
import com.orbit.auth.application.port.out.KakaoUnlinkException.Failure;
import com.orbit.auth.application.port.out.UnlinkKakaoAccountPort;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 관리자 API로 회원번호의 앱 연결을 끊는다. 카카오 오류 코드 중 앱과 연결되지 않은 사용자(-101)는 이미 끊긴 것으로, 휴면·없는 계정(-103)과 잘못된
 * 인자(-2)는 거절(REJECTED)로 보고, 그 밖의 오류 응답·어드민 키 오류·통신 실패는 다시 시도할 실패(UNAVAILABLE)로 본다.
 */
@Slf4j
@Component
class KakaoUnlinkClient implements UnlinkKakaoAccountPort {

    private static final int NOT_LINKED_USER = -101;
    private static final Set<Integer> REJECTED_CODES = Set.of(-103, -2);

    private final KakaoAdminApiProperties properties;
    private final RestClient restClient;

    KakaoUnlinkClient(KakaoAdminApiProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public void unlink(String kakaoUserId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", kakaoUserId);
        try {
            restClient
                    .post()
                    .uri(properties.unlinkUri())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            Integer code = errorCode(exception);
            if (code != null && code == NOT_LINKED_USER) {
                return;
            }
            log.warn("Kakao unlink API rejected request: status={}, code={}", exception.getStatusCode(), code);
            Failure failure = code != null && REJECTED_CODES.contains(code) ? Failure.REJECTED : Failure.UNAVAILABLE;
            throw new KakaoUnlinkException(failure, "Kakao unlink API responded " + exception.getStatusCode());
        } catch (RestClientException exception) {
            log.warn("Kakao unlink API call failed: {}", exception.getClass().getSimpleName());
            throw new KakaoUnlinkException(Failure.UNAVAILABLE, "Kakao unlink API call failed", exception);
        }
    }

    private static Integer errorCode(RestClientResponseException exception) {
        try {
            Map<?, ?> body = exception.getResponseBodyAs(Map.class);
            return body != null && body.get("code") instanceof Number code ? code.intValue() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
