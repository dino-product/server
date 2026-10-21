package com.orbit.schedule.application.port.out;

/**
 * {@link WorkRepository#save}가 같은 작업을 다른 트랜잭션이 먼저 바꾼 것을 알아챘을 때. 이 트랜잭션은 그 작업을 읽은 뒤 저장하기 전에 다른 변경이 커밋됐으므로
 * 반영하지 않는다.
 */
public class ConcurrentWorkChangeException extends RuntimeException {

    public ConcurrentWorkChangeException(Throwable cause) {
        super("work was changed by another transaction", cause);
    }
}
