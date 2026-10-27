package com.orbit.organization;

/**
 * {@link TechnicianWorkRelease} 구현이 같은 기사의 앞선 일정 변경이나 기사 요청(수락·거절·시작)을 기다리다 대기 한도를 넘겼다. 구현은 자기 모듈의 잠금 실패를 이 예외로 바꿔 던지고,
 * 호출자는 잡아서 정상 반환하지 않고 자기 오류로 바꿔 다시 던진다(트랜잭션은 롤백된다).
 */
public class TechnicianWorkReleaseBusyException extends RuntimeException {

    public TechnicianWorkReleaseBusyException(Throwable cause) {
        super("technician schedule stayed busy past the wait limit", cause);
    }
}
