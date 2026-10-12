package com.orbit.auth.application.port.out;

/**
 * Apple 토큰 API 호출 실패. Apple이 요청한 code·토큰을 거절했으면 {@link Failure#REJECTED}, 통신·Apple 장애·서버 설정(client_secret) 오류나 예상과 다른
 * 응답이면 {@link Failure#UNAVAILABLE}이다. 메시지에 code·토큰·client_secret을 담지 않는다.
 */
public class AppleTokenApiException extends RuntimeException {

    public enum Failure {
        REJECTED,
        UNAVAILABLE
    }

    private final Failure failure;

    public AppleTokenApiException(Failure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public AppleTokenApiException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }
}
