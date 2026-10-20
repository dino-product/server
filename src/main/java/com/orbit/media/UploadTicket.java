package com.orbit.media;

import java.time.Instant;
import java.util.Map;

/**
 * 발급한 업로드 주소.
 *
 * @param fileId 업로드한 뒤 업무 요청에 넣을 파일 식별자(UUID 문자열)
 * @param uploadUrl {@code PUT}으로 파일 바이트를 보낼 주소
 * @param requiredHeaders 업로드 요청에 그대로 붙일 헤더(서명에 들어간 헤더, {@code Host} 제외). 값을 바꾸면 저장소가 거부한다
 * @param expiresAt 주소가 만료되는 시각
 */
public record UploadTicket(String fileId, String uploadUrl, Map<String, String> requiredHeaders, Instant expiresAt) {

    public UploadTicket {
        requiredHeaders = Map.copyOf(requiredHeaders);
    }
}
