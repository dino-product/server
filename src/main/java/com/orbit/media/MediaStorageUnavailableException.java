package com.orbit.media;

/** 파일 저장소에 닿지 못했거나 저장소가 오류로 응답했거나, 주소를 서명할 자격 증명을 얻지 못함. 잠시 뒤 같은 요청을 다시 보내면 된다. */
public class MediaStorageUnavailableException extends RuntimeException {

    public MediaStorageUnavailableException(Throwable cause) {
        super("media storage unavailable", cause);
    }
}
