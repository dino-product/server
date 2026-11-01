package com.orbit.realtime.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.realtime.domain.ClientType;

@DisplayName("실시간 연결의 화면 종류 판별")
class ClientTypeResolverTest {

    private static final String DESKTOP_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/130.0 Safari/537.36";
    private static final String MOBILE_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";

    @Test
    @DisplayName("화면이 보낸 X-Orbit-Client를 브라우저 헤더보다 먼저 따른다")
    void prefersDeclaredClient() {
        assertThat(ClientTypeResolver.resolve("app", "?0", DESKTOP_AGENT)).isEqualTo(ClientType.APP);
        assertThat(ClientTypeResolver.resolve(" Mobile-Web ", null, DESKTOP_AGENT))
                .isEqualTo(ClientType.MOBILE_WEB);
    }

    @Test
    @DisplayName("모르는 X-Orbit-Client는 무시하고 Sec-CH-UA-Mobile, 그다음 User-Agent로 정한다")
    void fallsBackToBrowserHeaders() {
        assertThat(ClientTypeResolver.resolve("unknown", "?1", DESKTOP_AGENT)).isEqualTo(ClientType.MOBILE_WEB);
        assertThat(ClientTypeResolver.resolve(null, "?0", MOBILE_AGENT)).isEqualTo(ClientType.DESKTOP_WEB);
        assertThat(ClientTypeResolver.resolve(null, null, MOBILE_AGENT)).isEqualTo(ClientType.MOBILE_WEB);
        assertThat(ClientTypeResolver.resolve(null, "bogus", DESKTOP_AGENT)).isEqualTo(ClientType.DESKTOP_WEB);
    }

    @Test
    @DisplayName("판별할 헤더가 하나도 없으면 알 수 없음이다")
    void isUnknownWithoutHeaders() {
        assertThat(ClientTypeResolver.resolve(null, null, null)).isEqualTo(ClientType.UNKNOWN);
        assertThat(ClientTypeResolver.resolve(" ", null, " ")).isEqualTo(ClientType.UNKNOWN);
    }
}
