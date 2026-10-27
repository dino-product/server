package com.orbit.auth.application.port.out;

/**
 * 카카오 연결 끊기 실패. 카카오가 요청을 거절했으면(휴면·없는 계정, 잘못된 인자) {@link Failure#REJECTED}, 통신·카카오 장애·어드민 키 같은 설정 오류나 예상과 다른
 * 응답이면 {@link Failure#UNAVAILABLE}이다. 메시지에 회원번호·어드민 키를 담지 않는다.
 */
public class KakaoUnlinkException extends RuntimeException {

    public enum Failure {
        REJECTED,
        UNAVAILABLE
    }

    private final Failure failure;

    public KakaoUnlinkException(Failure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public KakaoUnlinkException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }
}
