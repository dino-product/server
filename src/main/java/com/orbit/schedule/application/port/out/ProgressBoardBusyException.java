package com.orbit.schedule.application.port.out;

/** {@link ProgressBoardSnapshotPort}가 지금 보드를 만들 수 없을 때(동시 상한 초과·기다림 한도 초과). 서비스가 잠시 뒤 다시 조회하라는 오류로 바꾼다. */
public class ProgressBoardBusyException extends RuntimeException {

    public ProgressBoardBusyException(String message) {
        super(message);
    }
}
