package com.orbit.realtime.adapter.in.web;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import com.orbit.realtime.domain.ClientType;

/**
 * 요청 헤더로 연결을 연 화면 종류를 고른다. 화면이 보낸 {@code X-Orbit-Client}(desktop-web·mobile-web·app)를 먼저 따르고, 없거나 모르는 값이면 브라우저의
 * {@code Sec-CH-UA-Mobile}(?1 모바일, ?0 데스크톱), 그다음 User-Agent에 "Mobi"가 있는지로 정한다. 셋 다 없으면 알 수 없음이다. 지표에만 쓰므로 틀려도
 * 연결 동작은 같다.
 */
final class ClientTypeResolver {

    static final String CLIENT_HEADER = "X-Orbit-Client";
    static final String MOBILE_HINT_HEADER = "Sec-CH-UA-Mobile";

    private ClientTypeResolver() {}

    static ClientType resolve(String client, String mobileHint, String userAgent) {
        return declared(client).or(() -> hinted(mobileHint)).orElseGet(() -> fromUserAgent(userAgent));
    }

    private static Optional<ClientType> declared(String client) {
        if (client == null) {
            return Optional.empty();
        }
        String tag = client.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(ClientType.values())
                .filter(type -> type != ClientType.UNKNOWN && type.tag().equals(tag))
                .findFirst();
    }

    private static Optional<ClientType> hinted(String mobileHint) {
        if (mobileHint == null) {
            return Optional.empty();
        }
        return switch (mobileHint.trim()) {
            case "?1" -> Optional.of(ClientType.MOBILE_WEB);
            case "?0" -> Optional.of(ClientType.DESKTOP_WEB);
            default -> Optional.empty();
        };
    }

    private static ClientType fromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return ClientType.UNKNOWN;
        }
        return userAgent.contains("Mobi") ? ClientType.MOBILE_WEB : ClientType.DESKTOP_WEB;
    }
}
