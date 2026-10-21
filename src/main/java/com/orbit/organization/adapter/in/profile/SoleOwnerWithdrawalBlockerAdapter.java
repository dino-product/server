package com.orbit.organization.adapter.in.profile;

import java.util.List;

import org.springframework.stereotype.Component;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.query.GetWithdrawalBlockingOwnershipsUseCase;
import com.orbit.organization.application.port.in.query.dto.GetWithdrawalBlockingOwnershipsQuery;
import com.orbit.profile.WithdrawalBlock;
import com.orbit.profile.WithdrawalBlockReason;
import com.orbit.profile.WithdrawalBlocker;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * profile이 선언한 탈퇴 차단 판정 요구 인터페이스를 organization의 유일한 총관리자 판정 유즈케이스로 잇는 어댑터. profile의 탈퇴 가능 여부 확인·탈퇴
 * 트랜잭션 안에서 불리며, 유일한 총관리자인 발주사 수를 {@link WithdrawalBlockReason#SOLE_OWNER} 건수로 옮긴다. 0이면 돌려주지 않는다. 판정이 잡은
 * 발주사 잠금은 profile 트랜잭션이 끝날 때까지 유지된다.
 *
 * <p>발주사 잠금 대기 한도를 넘긴 {@code ORGANIZATION-010}은 {@link WithdrawalBlocker}가 정한 재시도 가능한 실패 {@code COMMON-409}로 바꾼다. 두
 * 코드 모두 다시 시도하면 되는 409이고, profile API 응답에 organization 오류 코드가 새지 않게 한다.
 */
@Component
class SoleOwnerWithdrawalBlockerAdapter implements WithdrawalBlocker {

    private final GetWithdrawalBlockingOwnershipsUseCase getWithdrawalBlockingOwnershipsUseCase;

    SoleOwnerWithdrawalBlockerAdapter(GetWithdrawalBlockingOwnershipsUseCase getWithdrawalBlockingOwnershipsUseCase) {
        this.getWithdrawalBlockingOwnershipsUseCase = getWithdrawalBlockingOwnershipsUseCase;
    }

    @Override
    public List<WithdrawalBlock> blocksFor(Long accountId) {
        int soleOwnerOrganizations;
        try {
            soleOwnerOrganizations = getWithdrawalBlockingOwnershipsUseCase
                    .get(new GetWithdrawalBlockingOwnershipsQuery(accountId))
                    .soleOwnerOrganizationCount();
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == OrganizationErrorCode.ORGANIZATION_BUSY) {
                throw new BusinessException(CommonErrorCode.CONFLICT, exception);
            }
            throw exception;
        }
        if (soleOwnerOrganizations == 0) {
            return List.of();
        }
        return List.of(new WithdrawalBlock(WithdrawalBlockReason.SOLE_OWNER, soleOwnerOrganizations));
    }
}
