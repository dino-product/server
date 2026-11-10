package com.orbit.schedule.application.port.in.command.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 발급한 사진 업로드 주소. 클라이언트는 {@code uploadUrl}에 {@code requiredHeaders}를 그대로 붙여 {@code PUT}으로 사진을 올린 뒤
 * {@code fileId}를 작업 등록·기본정보 수정·완료보고 제출에 넣는다.
 */
public record PhotoUploadInfo(String fileId, String uploadUrl, Map<String, String> requiredHeaders, Instant expiresAt) {

    public PhotoUploadInfo {
        requiredHeaders = Map.copyOf(requiredHeaders);
    }
}
