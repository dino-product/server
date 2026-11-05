package com.orbit.media;

/** 업로드 주소를 발급할 수 없는 요청. 호출한 업무 모듈이 사유에 맞는 오류로 바꾼다. */
public class UploadNotAllowedException extends RuntimeException {

    /** 발급 거부 사유. */
    public enum Reason {
        /** 허용 형식(이미지)이 아님. */
        CONTENT_TYPE_NOT_ALLOWED,
        /** 크기가 1바이트 미만이거나 최대 크기를 넘음. */
        SIZE_NOT_ALLOWED
    }

    private final Reason reason;

    public UploadNotAllowedException(Reason reason) {
        super("upload not allowed: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
