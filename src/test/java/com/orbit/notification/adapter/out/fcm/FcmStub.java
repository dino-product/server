package com.orbit.notification.adapter.out.fcm;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * FCM HTTP v1 발신 엔드포인트와 Google OAuth 토큰 엔드포인트를 로컬 HTTP 서버로 대체한다. 서비스 계정 키는 실행 중 만든 RSA 키이고, 토큰 엔드포인트는
 * assertion이 그 키로 서명되고 발급자·대상·범위가 맞을 때만 access token을 준다(아니면 400 invalid_grant). 발신 엔드포인트는 정해 둔 응답을 차례로 돌려주고
 * 없으면 성공한다. 실제 FCM과의 E2E는 아니다.
 */
final class FcmStub implements AutoCloseable {

    static final String PROJECT_ID = "test-project";
    static final String CLIENT_EMAIL = "push@test-project.iam.gserviceaccount.com";
    static final String PRIVATE_KEY_ID = "test-private-key-id";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    private final KeyPair keyPair;
    private final HttpServer server;
    private final AtomicInteger issuedTokens = new AtomicInteger();
    private final AtomicInteger sentMessages = new AtomicInteger();
    private final Queue<Response> tokenResponses = new ConcurrentLinkedQueue<>();
    private final Queue<Response> sendResponses = new ConcurrentLinkedQueue<>();
    private final List<SendRequest> sendRequests = new CopyOnWriteArrayList<>();
    private volatile Duration sendDelay = Duration.ZERO;

    FcmStub() throws IOException {
        this.keyPair = rsaKeyPair();
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", this::token);
        server.createContext("/v1/projects/" + PROJECT_ID + "/messages:send", this::send);
        server.start();
    }

    String apiUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String tokenUri() {
        return apiUri() + "/token";
    }

    /** 이 스텁의 키와 토큰 엔드포인트를 담은 서비스 계정 키(JSON)를 base64로 돌려준다. */
    String credentials() {
        return credentials(serviceAccount());
    }

    static String credentials(Map<String, Object> serviceAccount) {
        return Base64.getEncoder()
                .encodeToString(JSON.writeValueAsString(serviceAccount).getBytes(StandardCharsets.UTF_8));
    }

    Map<String, Object> serviceAccount() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", "service_account");
        json.put("project_id", PROJECT_ID);
        json.put("private_key_id", PRIVATE_KEY_ID);
        json.put("private_key", privateKeyPem());
        json.put("client_email", CLIENT_EMAIL);
        json.put("token_uri", tokenUri());
        return json;
    }

    String privateKeyPem() {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    void respondToToken(int status, String body) {
        tokenResponses.add(new Response(status, body));
    }

    void respondToSend(int status, String body) {
        sendResponses.add(new Response(status, body));
    }

    void delaySend(Duration delay) {
        this.sendDelay = delay;
    }

    int issuedTokens() {
        return issuedTokens.get();
    }

    List<SendRequest> sendRequests() {
        return List.copyOf(sendRequests);
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Response scripted = tokenResponses.poll();
        if (scripted != null) {
            write(exchange, scripted);
            return;
        }
        if (!FcmAccessTokens.GRANT_TYPE.equals(form.get("grant_type")) || !validAssertion(form.get("assertion"))) {
            write(exchange, new Response(400, "{\"error\":\"invalid_grant\"}"));
            return;
        }
        write(
                exchange,
                new Response(
                        200,
                        "{\"access_token\":\"stub-access-token-" + issuedTokens.incrementAndGet()
                                + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"));
    }

    private boolean validAssertion(String assertion) {
        try {
            SignedJWT jwt = SignedJWT.parse(assertion);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            return jwt.verify(new RSASSAVerifier((RSAPublicKey) keyPair.getPublic()))
                    && PRIVATE_KEY_ID.equals(jwt.getHeader().getKeyID())
                    && CLIENT_EMAIL.equals(claims.getIssuer())
                    && claims.getAudience().equals(List.of(tokenUri()))
                    && FcmAccessTokens.SCOPE.equals(claims.getStringClaim("scope"))
                    && claims.getIssueTime() != null
                    && claims.getExpirationTime() != null;
        } catch (ParseException | JOSEException | RuntimeException exception) {
            return false;
        }
    }

    private void send(HttpExchange exchange) throws IOException {
        Map<String, Object> body = JSON.readValue(exchange.getRequestBody().readAllBytes(), JSON_OBJECT);
        sendRequests.add(new SendRequest(exchange.getRequestHeaders().getFirst("Authorization"), body));
        pause();
        Response scripted = sendResponses.poll();
        write(
                exchange,
                scripted != null
                        ? scripted
                        : new Response(
                                200,
                                "{\"name\":\"projects/" + PROJECT_ID + "/messages/" + sentMessages.incrementAndGet()
                                        + "\"}"));
    }

    private void pause() {
        if (sendDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(sendDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, String> form(String body) {
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

    private static void write(HttpExchange exchange, Response response) throws IOException {
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    record SendRequest(String authorization, Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        Map<String, Object> message() {
            return (Map<String, Object>) body.get("message");
        }
    }

    private record Response(int status, String body) {}
}
