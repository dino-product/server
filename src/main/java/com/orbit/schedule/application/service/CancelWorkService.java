package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;

import com.orbit.schedule.application.port.in.command.CancelWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.CancelWorkCommand;

/**
 * 작업 취소. 완료 전(등록·수락대기·수락됨·작업중) 작업을 취소하고 지금 시각·처리자·사유를 작업에 남긴다. 배정이 있던 작업은 그 배정도 같은 시각·처리자의
 * 취소로 끝난다. 이미 취소됐거나 완료된 작업은 409(SCHEDULE-002)다. 취소는 기사의 활성 작업을 줄이기만 하므로 기사 잠금은 잡지 않는다. 기사의 요청과 같은
 * 순간에 들어와 저장 자리에서 충돌하면 새 트랜잭션에서 한 번 다시 실행해 다시 읽은 상태로 판정한다(기사가 먼저 완료했으면 409 SCHEDULE-002, {@link
 * WorkVersions#retryOnConflict}). 오류 확인 순서는 schedule 지침의
 * 공통 순서를 따른다. 저장한 뒤 취소 당시 담당 기사(대기함이면 없음)와 함께 {@link com.orbit.schedule.WorkCancelled}를 발행한다.
 */
@Service
public class CancelWorkService implements CancelWorkUseCase {

    private final CancelWorkTransaction transaction;

    public CancelWorkService(CancelWorkTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public void cancel(CancelWorkCommand command) {
        WorkVersions.retryOnConflict(() -> transaction.cancel(command));
    }
}
