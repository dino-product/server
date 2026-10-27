package com.orbit.schedule.application.port.in.command.dto;

import java.util.List;
import java.util.Locale;

/**
 * 사진 식별자 목록의 입구 정규화. media 계약({@code StageRequest#fileIds})은 발급한 소문자 UUID 정규형만 받으므로,
 * 대문자로 보낸 식별자도 거부하지 않고 소문자로 바꿔 받는다. 형식 확인은 하지 않는다(null 목록·null 항목은 그대로 두어 도메인이 확인한다).
 */
final class PhotoIds {

    private PhotoIds() {}

    static List<String> lowercase(List<String> photoIds) {
        if (photoIds == null) {
            return null;
        }
        return photoIds.stream()
                .map(photoId -> photoId == null ? null : photoId.toLowerCase(Locale.ROOT))
                .toList();
    }
}
