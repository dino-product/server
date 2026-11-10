package com.orbit.media.application.port.out;

/** 저장소에 닿지 못했거나(제한 시간 초과 포함) 저장소가 처리하지 못한 오류로 응답함. */
public class ObjectStorageUnavailableException extends RuntimeException {

    public ObjectStorageUnavailableException(Throwable cause) {
        super("object storage unavailable", cause);
    }
}
