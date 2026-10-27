package com.orbit.auth.adapter.out.kakao;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.orbit.auth.application.port.out.ExchangeKakaoAuthorizationCodePort;
import com.orbit.auth.application.port.out.KakaoTokenApiException;
import com.orbit.auth.application.port.out.KakaoTokenApiException.Failure;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 토큰 API로 웹 로그인 인가 코드를 교환한다. 카카오가 인가 코드를 거절한 {@code invalid_grant}만 REJECTED이고, 같은 {@code invalid_grant}라도
 * 콜백 주소 불일치(KOE303)·grant_type 오류(KOE310)는 서버 설정 문제이므로 그 밖의 오류 응답·통신 실패·id_token 없는 응답과 함께 UNAVAILABLE이다. 받은
 * 카카오 access·refresh 토큰은 쓰지 않고 버린다. 인가 코드·토큰·클라이언트 시크릿과 인가 코드를 담는 {@code error_description}은 로그에 남기지
 * 않는다.
 */
@Slf4j
@Component
class KakaoTokenClient implements ExchangeKakaoAuthorizationCodePort {

    private static final String INVALID_GRANT = "invalid_grant";
    private static final Set<String> CONFIGURATION_GRANT_ERRORS = Set.of("KOE303", "KOE310");
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

    private final KakaoWebLoginProperties properties;
    private final RestClient restClient;

    KakaoTokenClient(KakaoWebLoginProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String exchange(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.restApiKey());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);
        form.add("client_secret", properties.clientSecret());
        Map<String, Object> response = post(form);
        Object idToken = response == null ? null : response.get("id_token");
        if (!(idToken instanceof String token) || token.isBlank()) {
            log.warn("Kakao token response has no id_token; check OpenID Connect activation and the openid scope");
            throw new KakaoTokenApiException(Failure.UNAVAILABLE, "Kakao token response has no id_token");
        }
        return token;
    }

    private Map<String, Object> post(MultiValueMap<String, String> form) {
        try {
            return restClient
                    .post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JSON_OBJECT);
        } catch (RestClientResponseException exception) {
            Map<?, ?> body = errorBody(exception);
            String error = stringField(body, "error");
            String errorCode = stringField(body, "error_code");
            log.warn(
                    "Kakao token API rejected request: status={}, error={}, errorCode={}",
                    exception.getStatusCode(),
                    error,
                    errorCode);
            Failure failure = INVALID_GRANT.equals(error) && !CONFIGURATION_GRANT_ERRORS.contains(errorCode)
                    ? Failure.REJECTED
                    : Failure.UNAVAILABLE;
            throw new KakaoTokenApiException(failure, "Kakao token API responded " + exception.getStatusCode());
        } catch (RestClientException exception) {
            log.warn("Kakao token API call failed: {}", exception.getClass().getSimpleName());
            throw new KakaoTokenApiException(Failure.UNAVAILABLE, "Kakao token API call failed", exception);
        }
    }

    private static Map<?, ?> errorBody(RestClientResponseException exception) {
        try {
            return exception.getResponseBodyAs(Map.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String stringField(Map<?, ?> body, String name) {
        return body != null && body.get(name) instanceof String value ? value : null;
    }
}
