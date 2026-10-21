package com.orbit.schedule.adapter.in.event;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import com.orbit.profile.AccountWithdrawn;
import com.orbit.schedule.application.port.in.command.ReleaseWithdrawnTechnicianWorksUseCase;
import com.orbit.schedule.application.port.in.command.dto.ReleaseWithdrawnTechnicianWorksCommand;

/**
 * 계정 탈퇴가 커밋되면 다른 스레드에서 그 계정의 기사 계약에 남은 배정됨·작업전 작업을 대기함으로 돌리고, 그 기사 계약을 미리 입력한 담당기사로 둔 대기함
 * 작업에서는 담당기사를 비운다. 롤백된 탈퇴는 받지 않는다. 반환 유즈케이스가 기사를 잠그므로 READ COMMITTED 트랜잭션을 직접 시작해야 해서, 리스너는
 * 트랜잭션을 열지 않는다({@code NOT_SUPPORTED}). 같은 탈퇴를 다시 받아도 유즈케이스가 이미 돌린 작업을 건너뛴다.
 */
@Component
class AccountWithdrawnListener {

    private final ReleaseWithdrawnTechnicianWorksUseCase releaseWithdrawnTechnicianWorksUseCase;

    AccountWithdrawnListener(ReleaseWithdrawnTechnicianWorksUseCase releaseWithdrawnTechnicianWorksUseCase) {
        this.releaseWithdrawnTechnicianWorksUseCase = releaseWithdrawnTechnicianWorksUseCase;
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    public void on(AccountWithdrawn event) {
        releaseWithdrawnTechnicianWorksUseCase.release(new ReleaseWithdrawnTechnicianWorksCommand(event.accountId()));
    }
}
