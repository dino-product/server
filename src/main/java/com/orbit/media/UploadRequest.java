package com.orbit.media;

import java.util.Objects;

/**
 * 업로드 주소 발급 요청. 발주사·계정은 호출한 업무 모듈이 권한을 확인한 값이다.
 *
 * @param organizationId 파일이 속할 발주사
 * @param uploaderAccountId 올리는 계정. 연결은 이 계정의 요청만 받는다
 * @param contentType 클라이언트가 밝힌 형식(예: {@code image/jpeg}). 업로드 서명에 들어가 다른 형식으로는 올릴 수 없다
 * @param sizeBytes 클라이언트가 밝힌 크기. 업로드 서명에 들어가 다른 크기로는 올릴 수 없다
 */
public record UploadRequest(
        Long organizationId, Long uploaderAccountId, UploadSpec spec, String contentType, long sizeBytes) {

    public UploadRequest {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(uploaderAccountId, "uploaderAccountId");
        Objects.requireNonNull(spec, "spec");
    }
}
