package com.orbit.auth.adapter.in.web;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 웹 로그인이 끝난 뒤 돌아갈 클라이언트 주소 목록. 클라이언트 이름(예: {@code web}, {@code android})별로 등록한 절대 주소로만 보내 임의 주소로의 리다이렉트를
 * 막는다. 이름은 대소문자를 구분하지 않는다.
 */
final class ClientReturnUris {

    private ClientReturnUris() {}

    /** 설정 속성 이름({@code property})을 오류 메시지에 써서, 비었거나 절대 주소가 아닌 값이 있으면 기동하지 않게 한다. */
    static Map<String, String> normalize(String property, Map<String, String> returnUris) {
        if (returnUris == null || returnUris.isEmpty()) {
            throw new IllegalArgumentException(property + " must not be empty");
        }
        return returnUris.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().toLowerCase(Locale.ROOT),
                        entry -> requireAbsolute(property, entry.getValue())));
    }

    static Optional<String> find(Map<String, String> returnUris, String client) {
        return client == null ? Optional.empty() : Optional.ofNullable(returnUris.get(client.toLowerCase(Locale.ROOT)));
    }

    private static String requireAbsolute(String property, String value) {
        if (value == null || value.isBlank() || !URI.create(value).isAbsolute()) {
            throw new IllegalArgumentException(property + " values must be absolute URIs");
        }
        return value;
    }
}
