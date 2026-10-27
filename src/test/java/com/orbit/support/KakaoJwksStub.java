package com.orbit.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 카카오 JWKS·토큰 엔드포인트를 로컬 HTTP 서버로 대체한다. 서명키와 응답만 바꿔 끼우고 검증기·JWKS 조회·HTTP 호출 경로는 실제 구현을 그대로 거친다. 실제
 * 카카오와의 E2E는 아니다. 사용한 테스트 클래스가 끝나면 {@link #close()}로 서버와 디스패처 스레드를 정리한다.
 *
 * <p>토큰 엔드포인트는 카카오처럼 동작한다: REST API 키와 클라이언트 시크릿이 맞아야 하고(아니면 401 invalid_client, KOE010), 인가 코드는
 * {@link #issueAuthorizationCode}로 만든 것을 한 번만 받는다(아니면 400 invalid_grant, KOE320). 성공하면 code에 묶인 회원번호·nonce로 REST
 * API 키 대상 id_token을 준다. {@link #failNextTokenRequests}로 장애 응답을 흉내 낸다.
 */
public final class KakaoJwksStub implements AutoCloseable {

    public static final String ISSUER = "https://kauth.kakao.com";
    public static final String AUDIENCE = "test-kakao-app-key";
    public static final String REST_API_KEY = "test-kakao-rest-api-key";
    public static final String CLIENT_SECRET = "test-kakao-client-secret";

    private final RSAKey signingKey;
    private final RSAKey foreignKey;
    private final HttpServer server;
    private final Map<String, IssuedCode> authorizationCodes = new ConcurrentHashMap<>();
    private final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger failingTokenRequests = new AtomicInteger();
    private volatile int failureStatus = 500;
    private volatile String failureBody = "{}";

    private KakaoJwksStub(RSAKey signingKey, RSAKey foreignKey, HttpServer server) {
        this.signingKey = signingKey;
        this.foreignKey = foreignKey;
        this.server = server;
    }

    public static KakaoJwksStub start() {
        try {
            RSAKey signingKey =
                    new RSAKeyGenerator(2048).keyID("kakao-test-key").generate();
            RSAKey foreignKey =
                    new RSAKeyGenerator(2048).keyID("kakao-test-key").generate();
            byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            KakaoJwksStub stub = new KakaoJwksStub(signingKey, foreignKey, server);
            server.createContext("/.well-known/jwks.json", exchange -> respond(exchange, 200, jwks));
            server.createContext("/oauth/token", stub::handleToken);
            server.start();
            return stub;
        } catch (JOSEException | IOException exception) {
            throw new IllegalStateException("Cannot start Kakao JWKS stub", exception);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public String jwkSetUri() {
        return baseUri() + "/.well-known/jwks.json";
    }

    public String tokenUri() {
        return baseUri() + "/oauth/token";
    }

    private String baseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 카카오가 발급한 것처럼 유효한 id_token을 만든다. */
    public String idToken(String subject, String nonce) {
        return sign(signingKey, claims(subject, nonce, AUDIENCE).build());
    }

    /** kid는 같지만 카카오 JWKS에 없는 키로 서명한 위조 id_token을 만든다. */
    public String forgedIdToken(String subject, String nonce) {
        return sign(foreignKey, claims(subject, nonce, AUDIENCE).build());
    }

    /** 카카오가 회원번호 subject 사용자의 웹 로그인에 발급한 것처럼 일회성 인가 코드를 만든다. id_token에는 인가 요청의 nonce를 싣는다. */
    public String issueAuthorizationCode(String subject, String nonce) {
        String code = "kakao-code-" + UUID.randomUUID();
        authorizationCodes.put(code, new IssuedCode(subject, nonce, false));
        return code;
    }

    /** 교환하면 JWKS에 없는 키로 서명한 id_token을 주는 인가 코드를 만든다. */
    public String issueAuthorizationCodeWithForgedIdToken(String subject, String nonce) {
        String code = "kakao-code-" + UUID.randomUUID();
        authorizationCodes.put(code, new IssuedCode(subject, nonce, true));
        return code;
    }

    /** 다음 토큰 요청 count번을 지정한 상태·본문으로 실패시킨다. */
    public void failNextTokenRequests(int count, int status, String body) {
        failureStatus = status;
        failureBody = body;
        failingTokenRequests.set(count);
    }

    public List<Map<String, String>> tokenRequests() {
        return List.copyOf(tokenRequests);
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        tokenRequests.add(form);
        if (failingTokenRequests.getAndUpdate(remaining -> Math.max(0, remaining - 1)) > 0) {
            respond(exchange, failureStatus, failureBody.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!REST_API_KEY.equals(form.get("client_id")) || !CLIENT_SECRET.equals(form.get("client_secret"))) {
            respondError(exchange, 401, "invalid_client", "KOE010");
            return;
        }
        IssuedCode issued = form.get("code") == null ? null : authorizationCodes.remove(form.get("code"));
        if (!"authorization_code".equals(form.get("grant_type")) || issued == null) {
            respondError(exchange, 400, "invalid_grant", "KOE320");
            return;
        }
        JWTClaimsSet claims =
                claims(issued.subject(), issued.nonce(), REST_API_KEY).build();
        String idToken = sign(issued.forged() ? foreignKey : signingKey, claims);
        String body = "{\"token_type\":\"bearer\",\"access_token\":\"kakao-access-" + UUID.randomUUID() + "\","
                + "\"id_token\":\"" + idToken + "\",\"expires_in\":21599,"
                + "\"refresh_token\":\"kakao-refresh-" + UUID.randomUUID() + "\",\"refresh_token_expires_in\":5183999,"
                + "\"scope\":\"openid\"}";
        respond(exchange, 200, body.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                form.put(
                        URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
            }
        }
        return form;
    }

    private static void respondError(HttpExchange exchange, int status, String error, String errorCode)
            throws IOException {
        String body = "{\"error\":\"" + error + "\",\"error_description\":\"rejected by stub\",\"error_code\":\""
                + errorCode + "\"}";
        respond(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
        exchange.close();
    }

    private static JWTClaimsSet.Builder claims(String subject, String nonce, String audience) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .claim("nonce", nonce);
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(key.getKeyID())
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Cannot sign test id_token", exception);
        }
    }

    private record IssuedCode(String subject, String nonce, boolean forged) {}
}
