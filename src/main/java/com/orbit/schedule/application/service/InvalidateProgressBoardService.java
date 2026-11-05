package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;

import com.orbit.schedule.application.port.in.command.InvalidateProgressBoardUseCase;
import com.orbit.schedule.application.port.out.ProgressBoardSnapshotPort;
import com.orbit.schedule.domain.OrganizationId;

/** 저장해 둔 진행 보드를 버린다. DB를 쓰지 않으므로 트랜잭션을 열지 않는다(커밋 뒤에 부른다). */
@Service
public class InvalidateProgressBoardService implements InvalidateProgressBoardUseCase {

    private final ProgressBoardSnapshotPort snapshotPort;

    public InvalidateProgressBoardService(ProgressBoardSnapshotPort snapshotPort) {
        this.snapshotPort = snapshotPort;
    }

    @Override
    public void invalidate(Long organizationId) {
        snapshotPort.invalidate(new OrganizationId(organizationId));
    }
}
