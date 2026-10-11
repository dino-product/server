package com.orbit.auth.adapter.in.web;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹·Android Apple 로그인이 끝난 뒤 돌아갈 클라이언트 주소. 클라이언트 이름(예: {@code web}, {@code android})별로 등록한 주소로만 보내 임의 주소로의
 * 리다이렉트를 막는다. 웹은 관리자 페이지 URL, Android는 앱 스킴 주소를 등록한다.
 */
@ConfigurationProperties(prefix = "app.auth.apple.web")
public record AppleWebReturnProperties(Map<String, String> returnUris) {

    public AppleWebReturnProperties {
        if (returnUris == null || returnUris.isEmpty()) {
            throw new IllegalArgumentException("app.auth.apple.web.return-uris must not be empty");
        }
        returnUris = returnUris.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().toLowerCase(Locale.ROOT), entry -> requireAbsolute(entry.getValue())));
    }

    Optional<String> returnUri(String client) {
        return client == null ? Optional.empty() : Optional.ofNullable(returnUris.get(client.toLowerCase(Locale.ROOT)));
    }

    private static String requireAbsolute(String value) {
        if (value == null || value.isBlank() || !URI.create(value).isAbsolute()) {
            throw new IllegalArgumentException("app.auth.apple.web.return-uris values must be absolute URIs");
        }
        return value;
    }
}
