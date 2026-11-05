package com.orbit.schedule.application.port.in.command;

/** 조직의 작업이 바뀌어 저장해 둔 진행 보드를 버린다. 작업 변경이 커밋된 뒤, 화면에 다시 조회하라는 신호보다 먼저 부른다. 실패해도 던지지 않는다. */
public interface InvalidateProgressBoardUseCase {

    void invalidate(Long organizationId);
}
