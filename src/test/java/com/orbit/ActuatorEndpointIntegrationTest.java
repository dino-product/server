package com.orbit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.support.TestcontainersConfiguration;

/**
 * Actuator는 관리 포트를 따로 띄워야 검증할 수 있어 MockMvc 기반 {@code IntegrationTestSupport} 대신 실제 포트로 기동한다. 수집기(Prometheus)와
 * kubelet probe는 토큰 없이 관리 포트에 접근한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ActuatorEndpointIntegrationTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void exposesPrometheusMetricsOnManagementPortWithoutToken() throws Exception {
        get(serverPort, "/api/v1/auth/examples/subjects/0");

        HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("application=\"orbit-server\"")
                .contains("http_server_requests_seconds_bucket");
    }

    @Test
    void exposesProbesOnManagementPortWithoutToken() throws Exception {
        assertThat(get(managementPort, "/actuator/health/liveness").statusCode())
                .isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/readiness").statusCode())
                .isEqualTo(200);
    }

    @Test
    void requiresTokenForInfoOnManagementPort() throws Exception {
        assertThat(get(managementPort, "/actuator/info").statusCode()).isEqualTo(401);
    }

    @Test
    void doesNotServePrometheusMetricsOnApiPort() throws Exception {
        HttpResponse<String> response = get(serverPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body()).doesNotContain("jvm_memory_used_bytes");
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
