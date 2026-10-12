package com.orbit.auth.adapter.out.apple;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.orbit.auth.adapter.out.oidc.OidcIdTokenDecoder;
import com.orbit.auth.application.port.out.AppleCodeExchange;
import com.orbit.auth.application.port.out.AppleRefreshToken;
import com.orbit.auth.application.port.out.AppleTokenApiException;
import com.orbit.auth.application.port.out.AppleTokenApiException.Failure;
import com.orbit.auth.application.port.out.ExchangeAppleAuthorizationCodePort;
import com.orbit.auth.application.port.out.RevokeAppleTokenPort;

import lombok.extern.slf4j.Slf4j;

/**
 * Apple 토큰 API를 호출한다. code 교환·토큰 철회는 해당 클라이언트의 client_secret과 함께 보내고, code 교환 응답의 id_token은 Apple JWKS로
 * 다시 검증해 sub를 읽는다. Apple이 거절한 {@code invalid_grant}만 REJECTED이고 그 밖의 오류 응답·통신 실패·설정 오류는 UNAVAILABLE이다.
 * code·토큰·client_secret은 로그에 남기지 않는다.
 */
@Slf4j
@Component
class AppleTokenClient implements ExchangeAppleAuthorizationCodePort, RevokeAppleTokenPort {

    private static final String INVALID_GRANT = "invalid_grant";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

    private final AppleTokenApiProperties properties;
    private final AppleClientSecretFactory clientSecrets;
    private final OidcIdTokenDecoder idTokenDecoder;
    private final RestClient restClient;

    AppleTokenClient(
            AppleTokenApiProperties properties,
            AppleClientSecretFactory clientSecrets,
            @Qualifier("appleJwkSource") JWKSource<SecurityContext> appleJwkSource,
            AppleOidcProperties oidcProperties,
            Clock clock) {
        this.properties = properties;
        this.clientSecrets = clientSecrets;
        this.idTokenDecoder = new OidcIdTokenDecoder(
                "Apple token endpoint",
                appleJwkSource,
                oidcProperties.issuer(),
                oidcProperties.allowedAudiences(),
                clock);
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public AppleCodeExchange exchange(String clientId, String authorizationCode, String redirectUri) {
        MultiValueMap<String, String> form = clientForm(clientId);
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");
        if (redirectUri != null) {
            form.add("redirect_uri", redirectUri);
        }
        Map<String, Object> response = post(properties.tokenUri(), form);
        Object refreshToken = response == null ? null : response.get("refresh_token");
        Object idToken = response == null ? null : response.get("id_token");
        if (!(refreshToken instanceof String refresh) || refresh.isBlank() || !(idToken instanceof String token)) {
            throw new AppleTokenApiException(
                    Failure.UNAVAILABLE, "Apple token response has no refresh_token or id_token");
        }
        String subject = idTokenDecoder
                .decode(token)
                .flatMap(jwt -> OidcIdTokenDecoder.nonBlankClaim(jwt, JwtClaimNames.SUB))
                .orElseThrow(() -> new AppleTokenApiException(
                        Failure.UNAVAILABLE, "Apple token response has an invalid id_token"));
        return new AppleCodeExchange(subject, clientId, refresh);
    }

    @Override
    public void revoke(AppleRefreshToken token) {
        MultiValueMap<String, String> form = clientForm(token.clientId());
        form.add("token", token.refreshToken());
        form.add("token_type_hint", "refresh_token");
        post(properties.revokeUri(), form);
    }

    private MultiValueMap<String, String> clientForm(String clientId) {
        String clientSecret;
        try {
            clientSecret = clientSecrets.create(clientId);
        } catch (IllegalStateException exception) {
            log.error("Apple client_secret is not configured correctly: {}", exception.getMessage());
            throw new AppleTokenApiException(Failure.UNAVAILABLE, "Apple client_secret is not configured correctly");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    private Map<String, Object> post(String uri, MultiValueMap<String, String> form) {
        try {
            return restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JSON_OBJECT);
        } catch (RestClientResponseException exception) {
            String error = errorCode(exception);
            log.warn("Apple token API rejected request: status={}, error={}", exception.getStatusCode(), error);
            Failure failure = INVALID_GRANT.equals(error) ? Failure.REJECTED : Failure.UNAVAILABLE;
            throw new AppleTokenApiException(failure, "Apple token API responded " + exception.getStatusCode());
        } catch (RestClientException exception) {
            log.warn("Apple token API call failed: {}", exception.getClass().getSimpleName());
            throw new AppleTokenApiException(Failure.UNAVAILABLE, "Apple token API call failed", exception);
        }
    }

    private static String errorCode(RestClientResponseException exception) {
        try {
            Map<?, ?> body = exception.getResponseBodyAs(Map.class);
            return body != null && body.get("error") instanceof String error ? error : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
