package com.orbit.media.adapter.in.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.orbit.media.application.port.in.command.ExpireStoredObjectsUseCase;
import com.orbit.media.application.port.in.command.dto.ExpireStoredObjectsCommand;
import com.orbit.media.domain.UploadedFilesExpired;

/**
 * 만료 기록이 커밋된 뒤에만 객체에 만료 표시를 남긴다. 저장소 호출이 요청 스레드와 DB 연결을 붙잡지 않도록 다른 스레드에서 실행한다. 모듈 안의 사실이라
 * {@code @ApplicationModuleListener} 대신 트랜잭션 이벤트 리스너를 쓰며, 별도 트랜잭션을 열지 않는다(DB를 쓰지 않음).
 */
@Component
class UploadedFilesExpiredListener {

    private final ExpireStoredObjectsUseCase expireStoredObjectsUseCase;

    UploadedFilesExpiredListener(ExpireStoredObjectsUseCase expireStoredObjectsUseCase) {
        this.expireStoredObjectsUseCase = expireStoredObjectsUseCase;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(UploadedFilesExpired event) {
        expireStoredObjectsUseCase.expire(new ExpireStoredObjectsCommand(event.objectKeys()));
    }
}
