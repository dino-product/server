package com.orbit.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
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
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Apple의 JWKS·토큰·철회 엔드포인트를 로컬 HTTP 서버로 대체한다. 서명키와 응답만 바꿔 끼우고 검증기·HTTP 호출 경로는 실제 구현을 그대로 거친다. 실제 Apple과의
 * E2E는 아니다.
 *
 * <p>토큰 엔드포인트는 Apple처럼 동작한다: client_secret이 이 스텁의 EC 키로 ES256 서명되고 sub가 client_id와 같아야 하며(아니면 400
 * invalid_client), authorization code는 {@link #issueAuthorizationCode}로 만든 것을 한 번만 받는다(아니면 400 invalid_grant). 성공하면
 * code에 묶인 sub의 id_token과 새 refresh token을 준다. {@link #failNextTokenRequests}로 장애 응답을 흉내 낸다.
 */
public final class AppleAuthStub implements AutoCloseable {

    public static final String ISSUER = "https://appleid.apple.com";
    public static final String BUNDLE_ID = "test-apple-bundle-id";
    public static final String SERVICES_ID = "test-apple-services-id";
    public static final String TEAM_ID = "TESTTEAM01";
    public static final String KEY_ID = "TESTKEY001";

    private final RSAKey signingKey;
    private final RSAKey foreignKey;
    private final KeyPair clientSecretKey;
    private final HttpServer server;
    private final Map<String, String> authorizationCodes = new ConcurrentHashMap<>();
    private final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> revokeRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger failingTokenRequests = new AtomicInteger();
    private volatile int failureStatus = 500;
    private volatile String failureBody = "{}";

    private AppleAuthStub(RSAKey signingKey, RSAKey foreignKey, KeyPair clientSecretKey, HttpServer server) {
        this.signingKey = signingKey;
        this.foreignKey = foreignKey;
        this.clientSecretKey = clientSecretKey;
        this.server = server;
    }

    public static AppleAuthStub start() {
        try {
            RSAKey signingKey =
                    new RSAKeyGenerator(2048).keyID("apple-test-key").generate();
            RSAKey foreignKey =
                    new RSAKeyGenerator(2048).keyID("apple-test-key").generate();
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AppleAuthStub stub = new AppleAuthStub(signingKey, foreignKey, generator.generateKeyPair(), server);
            byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/auth/keys", exchange -> respond(exchange, 200, jwks));
            server.createContext("/auth/token", stub::handleToken);
            server.createContext("/auth/revoke", stub::handleRevoke);
            server.start();
            return stub;
        } catch (JOSEException | IOException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot start Apple auth stub", exception);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public String jwkSetUri() {
        return baseUri() + "/auth/keys";
    }

    public String tokenUri() {
        return baseUri() + "/auth/token";
    }

    public String revokeUri() {
        return baseUri() + "/auth/revoke";
    }

    private String baseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** client_secret 서명에 쓸 .p8 개인키(PKCS#8 PEM). 이 스텁의 토큰 엔드포인트가 대응하는 공개키로 서명을 확인한다. */
    public String clientSecretPrivateKeyPem() {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(clientSecretKey.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    /** Apple이 sub 사용자에게 발급한 것처럼 일회성 authorization code를 만든다. */
    public String issueAuthorizationCode(String subject) {
        String code = "code-" + UUID.randomUUID();
        authorizationCodes.put(code, subject);
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

    public List<Map<String, String>> revokeRequests() {
        return List.copyOf(revokeRequests);
    }

    /** Apple이 iOS 앱(Bundle ID)에 발급한 것처럼 유효한 id_token을 만든다. nonce에는 앱이 넣은 값을 그대로 싣는다. */
    public String idToken(String subject, String nonce) {
        return idToken(subject, nonce, BUNDLE_ID);
    }

    /** 지정한 클라이언트를 대상으로 유효한 id_token을 만든다. nonce가 null이면 싣지 않는다. */
    public String idToken(String subject, String nonce, String audience) {
        return sign(signingKey, claims(subject, nonce, audience).build());
    }

    /** kid는 같지만 Apple JWKS에 없는 키로 서명한 위조 id_token을 만든다. */
    public String forgedIdToken(String subject, String nonce) {
        return sign(foreignKey, claims(subject, nonce, BUNDLE_ID).build());
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        tokenRequests.add(form);
        if (failingTokenRequests.getAndUpdate(remaining -> Math.max(0, remaining - 1)) > 0) {
            respond(exchange, failureStatus, failureBody.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!validClientSecret(form.get("client_id"), form.get("client_secret"))) {
            respondError(exchange, "invalid_client");
            return;
        }
        String subject = form.get("code") == null ? null : authorizationCodes.remove(form.get("code"));
        if (!"authorization_code".equals(form.get("grant_type")) || subject == null) {
            respondError(exchange, "invalid_grant");
            return;
        }
        String body = "{\"access_token\":\"apple-access-" + UUID.randomUUID() + "\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3600,\"refresh_token\":\"apple-refresh-" + UUID.randomUUID() + "\","
                + "\"id_token\":\"" + idToken(subject, null, form.get("client_id")) + "\"}";
        respond(exchange, 200, body.getBytes(StandardCharsets.UTF_8));
    }

    private void handleRevoke(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        revokeRequests.add(form);
        if (!validClientSecret(form.get("client_id"), form.get("client_secret"))) {
            respondError(exchange, "invalid_client");
            return;
        }
        respond(exchange, 200, new byte[0]);
    }

    private boolean validClientSecret(String clientId, String clientSecret) {
        if (clientId == null || clientSecret == null) {
            return false;
        }
        try {
            SignedJWT jwt = SignedJWT.parse(clientSecret);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            return JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm())
                    && KEY_ID.equals(jwt.getHeader().getKeyID())
                    && jwt.verify(new ECDSAVerifier((ECPublicKey) clientSecretKey.getPublic()))
                    && TEAM_ID.equals(claims.getIssuer())
                    && clientId.equals(claims.getSubject())
                    && claims.getAudience().contains(ISSUER)
                    && claims.getExpirationTime().toInstant().isAfter(Instant.now());
        } catch (ParseException | JOSEException exception) {
            return false;
        }
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

    private static void respondError(HttpExchange exchange, String error) throws IOException {
        respond(exchange, 400, ("{\"error\":\"" + error + "\"}").getBytes(StandardCharsets.UTF_8));
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
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)));
        return nonce == null ? builder : builder.claim("nonce", nonce).claim("nonce_supported", true);
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
}
