package com.orbit.schedule.application.port.out;

/**
 * {@link LockTechnicianSchedulePort}가 대기 한도 안에 기사를 잠그지 못했을 때. 어댑터는 오류 코드를 모르므로 이 예외로 알리고, 서비스가 사용자 오류로 바꾼다.
 * 실패한 트랜잭션은 더 쓸 수 없으므로 호출자는 이 예외를 삼키지 않는다.
 */
public class TechnicianScheduleBusyException extends RuntimeException {

    public TechnicianScheduleBusyException(Throwable cause) {
        super("technician schedule lock was not acquired within the wait limit", cause);
    }
}
