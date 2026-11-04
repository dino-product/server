package com.orbit.auth.adapter.in.web;

import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 웹 Apple 로그인이 끝난 뒤 돌아갈 클라이언트 주소. 웹은 관리자 페이지 URL을 등록한다({@link ClientReturnUris}). */
@ConfigurationProperties(prefix = "app.auth.apple.web")
public record AppleWebReturnProperties(Map<String, String> returnUris) {

    public AppleWebReturnProperties {
        returnUris = ClientReturnUris.normalize("app.auth.apple.web.return-uris", returnUris);
    }

    Optional<String> returnUri(String client) {
        return ClientReturnUris.find(returnUris, client);
    }
}
