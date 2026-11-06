package com.orbit.organization;

/**
 * {@link TechnicianWorkRelease} 구현이 같은 기사의 앞선 일정 변경이나 기사 요청(수락·거절·시작)을 기다리다 대기 한도를 넘겼거나, 반환할 작업을 같은 순간에
 * 다른 요청(취소·기본정보 수정 등)이 먼저 바꿔 저장이 충돌했다. 둘 다 같은 요청을 다시 보내면 처리될 수 있는 실패다. 구현은 자기 모듈의 잠금 실패·저장 충돌을 이
 * 예외로 바꿔 던지고, 호출자는 잡아서 정상 반환하지 않고 자기 오류로 바꿔 다시 던진다(트랜잭션은 롤백된다).
 */
public class TechnicianWorkReleaseBusyException extends RuntimeException {

    public TechnicianWorkReleaseBusyException(Throwable cause) {
        super("technician schedule stayed busy past the wait limit", cause);
    }
}
