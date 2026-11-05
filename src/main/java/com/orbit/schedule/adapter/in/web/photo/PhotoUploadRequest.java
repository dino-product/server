package com.orbit.schedule.adapter.in.web.photo;

import io.swagger.v3.oas.annotations.media.Schema;

public record PhotoUploadRequest(
        @Schema(description = "올릴 사진의 형식(Content-Type). 이미지(JPEG·PNG·WebP·HEIC/HEIF)만 받는다", example = "image/jpeg")
        String contentType,

        @Schema(description = "올릴 사진의 크기(바이트). 업로드할 때 이 크기 그대로 보내야 한다", example = "2457600")
        Long sizeBytes) {}
