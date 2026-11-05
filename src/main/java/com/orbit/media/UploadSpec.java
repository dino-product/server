package com.orbit.media;

import java.util.Objects;

/**
 * 업무 모듈이 정하는 업로드 조건. 형식은 이미지(설정 {@code app.media.image-content-types})만 받는다.
 *
 * @param purpose 용도 코드(대문자·숫자·밑줄, 최대 {@link #MAX_PURPOSE_LENGTH}자). 같은 용도로 발급한 파일만 같은 용도로 연결된다
 * @param maxBytes 한 파일의 최대 크기(바이트). 업무 규칙이 정하지 않았으면 {@link #STORAGE_MAX_BYTES}
 */
public record UploadSpec(String purpose, long maxBytes) {

    public static final int MAX_PURPOSE_LENGTH = 50;

    /** 저장소가 한 번의 PUT으로 받는 최대 크기(S3 단일 업로드 5GiB). 업무 규칙 값이 아니다. */
    public static final long STORAGE_MAX_BYTES = 5L * 1024 * 1024 * 1024;

    public UploadSpec {
        requirePurpose(purpose);
        if (maxBytes < 1 || maxBytes > STORAGE_MAX_BYTES) {
            throw new IllegalArgumentException("maxBytes must be between 1 and " + STORAGE_MAX_BYTES);
        }
    }

    static void requirePurpose(String purpose) {
        Objects.requireNonNull(purpose, "purpose");
        if (purpose.isEmpty() || purpose.length() > MAX_PURPOSE_LENGTH || !purpose.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("purpose must be 1-" + MAX_PURPOSE_LENGTH + " of [A-Z0-9_]");
        }
    }
}
