package com.orbit.schedule.adapter.in.profile;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.orbit.profile.WithdrawalBlock;
import com.orbit.profile.WithdrawalBlockReason;
import com.orbit.profile.WithdrawalBlocker;
import com.orbit.schedule.application.port.in.query.GetWithdrawalBlockingWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetWithdrawalBlockingWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WithdrawalBlockingWorksInfo;

/**
 * profile이 선언한 탈퇴 차단 판정 요구 인터페이스를 schedule의 판정 유즈케이스로 잇는 어댑터. profile의 탈퇴 가능 여부 확인·탈퇴 트랜잭션 안에서 불리며, 작업
 * 수를 profile의 사유로 옮기기만 한다. 응답 전(배정됨) 작업은 레포 상태 {@code PENDING_ACCEPTANCE}이고 사유 이름은 [작업] 정책 §9 코드를 따른
 * {@link WithdrawalBlockReason#WORK_PENDING_ACCEPT}라 이름이 다르다(S-33). 건수가 0인 사유는 돌려주지 않는다.
 *
 * <p>판정 유즈케이스는 읽기 전용으로 선언돼 있지만 profile의 쓰기 트랜잭션에 참여하므로 읽기 전용 설정은 적용되지 않는다. 판정은 저장하지 않으므로 결과는 같다.
 */
@Component
class WithdrawalBlockerAdapter implements WithdrawalBlocker {

    private final GetWithdrawalBlockingWorksUseCase getWithdrawalBlockingWorksUseCase;

    WithdrawalBlockerAdapter(GetWithdrawalBlockingWorksUseCase getWithdrawalBlockingWorksUseCase) {
        this.getWithdrawalBlockingWorksUseCase = getWithdrawalBlockingWorksUseCase;
    }

    @Override
    public List<WithdrawalBlock> blocksFor(Long accountId) {
        WithdrawalBlockingWorksInfo info =
                getWithdrawalBlockingWorksUseCase.get(new GetWithdrawalBlockingWorksQuery(accountId));
        List<WithdrawalBlock> blocks = new ArrayList<>();
        if (info.inProgressCount() > 0) {
            blocks.add(new WithdrawalBlock(WithdrawalBlockReason.WORK_IN_PROGRESS, info.inProgressCount()));
        }
        if (info.pendingAcceptanceCount() > 0) {
            blocks.add(new WithdrawalBlock(WithdrawalBlockReason.WORK_PENDING_ACCEPT, info.pendingAcceptanceCount()));
        }
        return List.copyOf(blocks);
    }
}
