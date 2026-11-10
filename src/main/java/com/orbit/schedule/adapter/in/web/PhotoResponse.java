package com.orbit.schedule.adapter.in.web;

import com.orbit.schedule.application.port.in.query.dto.PhotoInfo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사진(완료보고 사진·현장 사진)")
public record PhotoResponse(
        @Schema(description = "업로드한 사진 식별자") String photoId,

        @Schema(description = "내려받을 시간 제한 주소(발급 10분 뒤 만료, 다시 조회해 갱신)")
        String url) {

    public static PhotoResponse from(PhotoInfo info) {
        return new PhotoResponse(info.photoId(), info.url());
    }
}
