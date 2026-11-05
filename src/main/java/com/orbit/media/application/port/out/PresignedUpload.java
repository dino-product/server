package com.orbit.media.application.port.out;

import java.time.Instant;
import java.util.Map;

/**
 * 서명한 업로드 주소.
 *
 * @param url {@code PUT} 주소
 * @param headers 업로드 요청에 그대로 붙일 서명된 헤더({@code Host} 제외)
 * @param expiresAt 만료 시각
 */
public record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {

    public PresignedUpload {
        headers = Map.copyOf(headers);
    }
}
