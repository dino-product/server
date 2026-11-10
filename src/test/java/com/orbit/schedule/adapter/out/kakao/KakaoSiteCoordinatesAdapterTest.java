package com.orbit.schedule.adapter.out.kakao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.orbit.schedule.domain.SiteCoordinates;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** 카카오 로컬 API 주소 검색을 로컬 HTTP 서버로 대체해, 어댑터의 실제 HTTP 호출·응답 해석·실패 처리를 확인한다. 실제 카카오와의 E2E는 아니다. */
@DisplayName("카카오 주소 검색 좌표 어댑터")
class KakaoSiteCoordinatesAdapterTest {

    private static final String REST_API_KEY = "test-kakao-rest-api-key";
    private static final String ADDRESS = "서울 마포구 양화로 45";
    private static final String FOUND = """
            {"meta":{"total_count":2,"pageable_count":2,"is_end":true},
             "documents":[
               {"address_name":"서울 마포구 서교동 353-1","address_type":"REGION_ADDR",
                "x":"126.922803498134","y":"37.5561519999999","address":{},"road_address":{}},
               {"address_name":"다른 주소","address_type":"REGION_ADDR","x":"127.0","y":"37.0"}]}
            """;

    private static HttpServer server;
    private static final List<Request> REQUESTS = new CopyOnWriteArrayList<>();
    private static volatile Reply reply;

    private final KakaoSiteCoordinatesAdapter adapter = adapterWith(searchUri(), Duration.ofSeconds(2));

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/local/search/address.json", KakaoSiteCoordinatesAdapterTest::handle);
        server.start();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    @BeforeEach
    void reset() {
        REQUESTS.clear();
        reply = new Reply(200, FOUND, Duration.ZERO);
    }

    @Test
    @DisplayName("REST API 키로 주소를 검색해 첫 번째 결과의 y를 위도, x를 경도로 소수점 6자리까지 돌려준다")
    void returnsFirstDocumentCoordinates() {
        assertThat(adapter.find(ADDRESS))
                .contains(new SiteCoordinates(new BigDecimal("37.556152"), new BigDecimal("126.922803")));

        Request request = REQUESTS.getLast();
        assertThat(request.authorization()).isEqualTo("KakaoAK " + REST_API_KEY);
        assertThat(request.query()).containsEntry("query", ADDRESS).containsEntry("size", "5");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"서울 {테스트} 1", "A&B=C+D", "서울 #3 100%", "서울 {} 1"})
    @DisplayName("주소의 특수문자를 모두 인코딩해 원문 그대로 검색한다")
    void encodesSpecialCharacters(String address) {
        assertThat(adapter.find(address)).isPresent();

        assertThat(REQUESTS.getLast().query()).containsEntry("query", address);
    }

    @Test
    @DisplayName("시·구·동이나 도로 전체만 맞은 결과는 건너뛰고 주소 하나까지 맞은 첫 결과를 쓴다")
    void skipsPartialMatches() {
        reply = new Reply(200, """
                {"documents":[
                  {"address_type":"REGION","x":"126.9","y":"37.5"},
                  {"address_type":"ROAD","x":"126.8","y":"37.4"},
                  {"address_type":"ROAD_ADDR","x":"126.922803","y":"37.556151"}]}
                """, Duration.ZERO);

        assertThat(adapter.find(ADDRESS))
                .contains(new SiteCoordinates(new BigDecimal("37.556151"), new BigDecimal("126.922803")));
    }

    @Test
    @DisplayName("대략적으로만 맞은 결과뿐이면 빈 값이다")
    void returnsEmptyWhenOnlyPartialMatches() {
        reply = new Reply(
                200, "{\"documents\":[{\"address_type\":\"REGION\",\"x\":\"126.9\",\"y\":\"37.5\"}]}", Duration.ZERO);

        assertThat(adapter.find(ADDRESS)).isEmpty();
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 값이다")
    void returnsEmptyWhenNotFound() {
        reply = new Reply(200, "{\"meta\":{\"total_count\":0},\"documents\":[]}", Duration.ZERO);

        assertThat(adapter.find(ADDRESS)).isEmpty();
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {400, 401, 429, 500})
    @DisplayName("오류 응답이면 예외 대신 빈 값이다")
    void returnsEmptyOnErrorResponse(int status) {
        reply = new Reply(status, "{\"errorType\":\"stub\",\"message\":\"stub error\"}", Duration.ZERO);

        assertThat(adapter.find(ADDRESS)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "not json",
                "{\"documents\":[{\"address_type\":\"ROAD_ADDR\",\"x\":\"abc\",\"y\":\"37.5\"}]}",
                "{\"documents\":[{\"address_type\":\"ROAD_ADDR\",\"x\":\"126.9\"}]}",
                "{\"documents\":[{\"address_type\":\"ROAD_ADDR\",\"x\":\"126.9\",\"y\":\"137.5\"}]}"
            })
    @DisplayName("해석할 수 없거나 범위를 벗어난 응답이면 빈 값이다")
    void returnsEmptyOnUnreadableResponse(String body) {
        reply = new Reply(200, body, Duration.ZERO);

        assertThat(adapter.find(ADDRESS)).isEmpty();
    }

    @Test
    @DisplayName("제한 시간 안에 응답하지 않으면 빈 값이다")
    void returnsEmptyOnTimeout() {
        reply = new Reply(200, FOUND, Duration.ofSeconds(2));
        KakaoSiteCoordinatesAdapter impatient = adapterWith(searchUri(), Duration.ofMillis(200));

        assertThat(impatient.find(ADDRESS)).isEmpty();
    }

    @Test
    @DisplayName("연결할 수 없으면 빈 값이다")
    void returnsEmptyWhenUnreachable() {
        KakaoSiteCoordinatesAdapter unreachable =
                adapterWith("http://127.0.0.1:1/v2/local/search/address.json", Duration.ofSeconds(1));

        assertThat(unreachable.find(ADDRESS)).isEmpty();
    }

    private static KakaoSiteCoordinatesAdapter adapterWith(String uri, Duration timeout) {
        return new KakaoSiteCoordinatesAdapter(new KakaoLocalApiProperties(uri, REST_API_KEY, timeout));
    }

    private static String searchUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v2/local/search/address.json";
    }

    private static void handle(HttpExchange exchange) throws IOException {
        REQUESTS.add(
                new Request(exchange.getRequestHeaders().getFirst("Authorization"), queryOf(exchange.getRequestURI())));
        Reply current = reply;
        try {
            Thread.sleep(current.delay());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(current.status(), body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static Map<String, String> queryOf(URI uri) {
        Map<String, String> query = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null) {
            return query;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            query.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return query;
    }

    private record Request(String authorization, Map<String, String> query) {}

    private record Reply(int status, String body, Duration delay) {}
}
