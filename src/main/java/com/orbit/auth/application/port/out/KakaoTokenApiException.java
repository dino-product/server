package com.orbit.auth.application.port.out;

/**
 * 카카오 토큰 API 호출 실패. 카카오가 인가 코드를 거절했으면(만료·재사용·없음) {@link Failure#REJECTED}, 통신·카카오 장애·서버 설정(앱 키·클라이언트
 * 시크릿·콜백 주소) 오류나 예상과 다른 응답이면 {@link Failure#UNAVAILABLE}이다. 메시지에 인가 코드·토큰·클라이언트 시크릿을 담지 않는다.
 */
public class KakaoTokenApiException extends RuntimeException {

    public enum Failure {
        REJECTED,
        UNAVAILABLE
    }

    private final Failure failure;

    public KakaoTokenApiException(Failure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public KakaoTokenApiException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }
}
