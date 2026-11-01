package com.orbit.notification.adapter.out.fcm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * 서비스 계정으로 FCM 호출용 OAuth 2.0 access token을 받는다(JWT bearer grant, RFC 7523). 서비스 계정 키로 RS256 서명한 assertion을 토큰
 * 엔드포인트에 보내고, 받은 토큰을 만료 5분 전까지 다시 쓴다. assertion·access token은 로그와 예외 메시지에 남기지 않는다.
 */
final class FcmAccessTokens {

    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final Duration ASSERTION_LIFETIME = Duration.ofHours(1);
    private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofMinutes(5);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

    private final FcmServiceAccount account;
    private final RestClient restClient;
    private final Clock clock;
    private final RSASSASigner signer;
    private CachedToken cached;

    FcmAccessTokens(FcmServiceAccount account, RestClient restClient, Clock clock) {
        this.account = account;
        this.restClient = restClient;
        this.clock = clock;
        this.signer = new RSASSASigner(account.privateKey());
    }

    /** 쓸 수 있는 access token을 돌려준다. 받지 못하면 {@link FcmAccessTokenException}이다. */
    synchronized String get() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (cached == null || !now.isBefore(cached.renewAt())) {
            cached = request(now);
        }
        return cached.value();
    }

    private CachedToken request(Instant now) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("assertion", assertion(now));
        Map<String, Object> response;
        try {
            response = restClient
                    .post()
                    .uri(account.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JSON_OBJECT);
        } catch (RestClientResponseException exception) {
            throw new FcmAccessTokenException("FCM access token request rejected: status="
                    + exception.getStatusCode().value() + ", error=" + errorCode(exception));
        } catch (RestClientException exception) {
            throw new FcmAccessTokenException(
                    "FCM access token request failed: " + exception.getClass().getSimpleName());
        }
        Object token = response == null ? null : response.get("access_token");
        Object expiresIn = response == null ? null : response.get("expires_in");
        if (!(token instanceof String value) || value.isBlank() || !(expiresIn instanceof Number seconds)) {
            throw new FcmAccessTokenException("FCM access token response has no access_token or expires_in");
        }
        return new CachedToken(value, now.plusSeconds(seconds.longValue()).minus(RENEW_BEFORE_EXPIRY));
    }

    private String assertion(Instant now) {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(account.privateKeyId())
                        .build(),
                new JWTClaimsSet.Builder()
                        .issuer(account.clientEmail())
                        .audience(account.tokenUri())
                        .claim("scope", SCOPE)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plus(ASSERTION_LIFETIME)))
                        .build());
        try {
            jwt.sign(signer);
        } catch (JOSEException exception) {
            throw new FcmAccessTokenException("Cannot sign FCM access token assertion");
        }
        return jwt.serialize();
    }

    private static String errorCode(RestClientResponseException exception) {
        try {
            Map<?, ?> body = exception.getResponseBodyAs(Map.class);
            return body != null && body.get("error") instanceof String error ? error : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record CachedToken(String value, Instant renewAt) {}
}
