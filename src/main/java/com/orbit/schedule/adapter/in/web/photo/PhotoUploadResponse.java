package com.orbit.schedule.adapter.in.web.photo;

import java.time.Instant;
import java.util.Map;

import com.orbit.schedule.application.port.in.command.dto.PhotoUploadInfo;

import io.swagger.v3.oas.annotations.media.Schema;

public record PhotoUploadResponse(
        @Schema(description = "올린 뒤 작업 등록·기본정보 수정·완료보고 제출에 넣을 사진 파일 식별자(UUID)")
        String fileId,

        @Schema(description = "사진 바이트를 PUT으로 보낼 주소(서버를 거치지 않고 저장소로 바로 올라감)")
        String uploadUrl,

        @Schema(
                description = "업로드 요청에 붙일 서명된 헤더(content-type·content-length). content-type은 이 값 그대로 붙인다. "
                        + "content-length는 본문 길이와 같아야 하며 HTTP 클라이언트가 붙인다(브라우저는 직접 붙일 수 없으므로 발급 때 밝힌 크기의 "
                        + "파일을 그대로 본문으로 보낸다). 형식이나 길이가 다르면 저장소가 403으로 거부한다",
                example = "{\"content-type\":\"image/jpeg\",\"content-length\":\"2457600\"}")
        Map<String, String> requiredHeaders,

        @Schema(description = "업로드 주소 만료 시각(UTC). 지나면 다시 발급받는다")
        Instant expiresAt) {

    public static PhotoUploadResponse from(PhotoUploadInfo info) {
        return new PhotoUploadResponse(info.fileId(), info.uploadUrl(), info.requiredHeaders(), info.expiresAt());
    }
}
