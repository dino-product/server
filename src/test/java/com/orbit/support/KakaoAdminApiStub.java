package com.orbit.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 카카오 관리자 API의 연결 끊기 엔드포인트를 로컬 HTTP 서버로 대체한다. 어댑터의 HTTP 호출 경로는 실제 구현을 그대로 거친다. 실제 카카오와의 E2E는 아니다.
 *
 * <p>기본 응답은 카카오처럼 {@code {"id": target_id}}이며, {@link #respondNext}로 다음 요청의 상태·본문을 차례로 바꾼다. 요청마다 받은
 * {@code Authorization} 헤더와 폼 값을 기록한다.
 */
public final class KakaoAdminApiStub implements AutoCloseable {

    public static final String ADMIN_KEY = "test-kakao-admin-key";

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Response> nextResponses = new ConcurrentLinkedQueue<>();

    private KakaoAdminApiStub(HttpServer server) {
        this.server = server;
    }

    public static KakaoAdminApiStub start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            KakaoAdminApiStub stub = new KakaoAdminApiStub(server);
            server.createContext("/v1/user/unlink", stub::handleUnlink);
            server.start();
            return stub;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start Kakao admin API stub", exception);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public String unlinkUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/user/unlink";
    }

    /** 다음 요청 하나를 지정한 상태·본문으로 응답한다. 여러 번 부르면 순서대로 쓴다. */
    public void respondNext(int status, String body) {
        nextResponses.add(new Response(status, body));
    }

    /** 카카오 오류 응답 형식({@code {"msg": ..., "code": ...}})으로 다음 요청 하나에 응답한다. */
    public void failNext(int status, int kakaoCode) {
        respondNext(status, "{\"msg\":\"stub error\",\"code\":" + kakaoCode + "}");
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
        nextResponses.clear();
    }

    private void handleUnlink(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        requests.add(new Request(exchange.getRequestHeaders().getFirst("Authorization"), form));
        Response response = nextResponses.poll();
        if (response == null) {
            response = new Response(200, "{\"id\":" + form.get("target_id") + "}");
        }
        respond(exchange, response.status(), response.body().getBytes(StandardCharsets.UTF_8));
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

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    /** 받은 요청의 {@code Authorization} 헤더와 폼 값. */
    public record Request(String authorization, Map<String, String> form) {}

    private record Response(int status, String body) {}
}
