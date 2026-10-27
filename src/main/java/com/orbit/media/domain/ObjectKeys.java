package com.orbit.media.domain;

import java.util.UUID;

/** 저장소 객체 키 규칙(infra와 공유하는 계약, ADR-007). 업로드 대기는 {@code incoming/{fileId}}, 연결된 파일은 {@code media/{orgId}/{fileId}}. */
public final class ObjectKeys {

    private ObjectKeys() {}

    public static String incoming(UUID fileId) {
        return "incoming/" + fileId;
    }

    /** 연결된 파일. 식별자는 저장된 값 그대로 쓴다(UUID가 아니면 저장소에 객체가 없을 뿐이다). */
    public static String media(long organizationId, String fileId) {
        return "media/" + organizationId + "/" + fileId;
    }
}
