package com.orbit.organization.adapter.in.event;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.orbit.organization.application.port.in.command.DeactivateWithdrawnAccountUseCase;
import com.orbit.organization.application.port.in.command.dto.DeactivateWithdrawnAccountCommand;
import com.orbit.profile.AccountWithdrawn;

/**
 * 계정 탈퇴가 커밋되면 다른 스레드의 새 트랜잭션에서 그 계정의 모든 직원 소속·기사 계약을 비활성화하고 승인 대기 참여 요청을 취소한다. 롤백된 탈퇴는 받지
 * 않는다. 발주사 잠금 대기 한도를 넘기면 실패로 남아 이벤트 발행 기록이 다시 전달하고, 같은 탈퇴를 다시 받아도 유즈케이스가 탈퇴 표시된 행을 건너뛴다.
 */
@Component
class AccountWithdrawnMembershipListener {

    private final DeactivateWithdrawnAccountUseCase deactivateWithdrawnAccountUseCase;

    AccountWithdrawnMembershipListener(DeactivateWithdrawnAccountUseCase deactivateWithdrawnAccountUseCase) {
        this.deactivateWithdrawnAccountUseCase = deactivateWithdrawnAccountUseCase;
    }

    @ApplicationModuleListener
    public void on(AccountWithdrawn event) {
        deactivateWithdrawnAccountUseCase.deactivate(new DeactivateWithdrawnAccountCommand(event.accountId()));
    }
}
