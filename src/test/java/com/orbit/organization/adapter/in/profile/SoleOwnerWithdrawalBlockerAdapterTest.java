package com.orbit.organization.adapter.in.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.query.GetWithdrawalBlockingOwnershipsUseCase;
import com.orbit.organization.application.port.in.query.dto.GetWithdrawalBlockingOwnershipsQuery;
import com.orbit.organization.application.port.in.query.dto.WithdrawalBlockingOwnershipsInfo;
import com.orbit.profile.WithdrawalBlock;
import com.orbit.profile.WithdrawalBlockReason;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@DisplayName("profile 탈퇴 차단 판정 요구 인터페이스 구현")
class SoleOwnerWithdrawalBlockerAdapterTest {

    private final RecordingUseCase useCase = new RecordingUseCase();
    private final SoleOwnerWithdrawalBlockerAdapter adapter = new SoleOwnerWithdrawalBlockerAdapter(useCase);

    @Test
    @DisplayName("그 계정의 판정을 요청하고 유일한 총관리자인 발주사 수를 SOLE_OWNER 건수로 옮긴다")
    void translatesSoleOwnerOrganizationsIntoReason() {
        useCase.result = new WithdrawalBlockingOwnershipsInfo(2);

        assertThat(adapter.blocksFor(7L)).containsExactly(new WithdrawalBlock(WithdrawalBlockReason.SOLE_OWNER, 2));
        assertThat(useCase.queries).containsExactly(new GetWithdrawalBlockingOwnershipsQuery(7L));
    }

    @Test
    @DisplayName("유일한 총관리자인 발주사가 없으면 빈 목록이다")
    void returnsNothingWithoutSoleOwnership() {
        useCase.result = new WithdrawalBlockingOwnershipsInfo(0);

        assertThat(adapter.blocksFor(7L)).isEmpty();
    }

    @Test
    @DisplayName("발주사 잠금 대기 한도를 넘기면 profile 탈퇴 API가 문서화한 동시 변경 충돌(COMMON-409)로 바꾼다")
    void translatesOrganizationBusyIntoCommonConflict() {
        BusinessException busy = new BusinessException(OrganizationErrorCode.ORGANIZATION_BUSY);
        useCase.failure = busy;

        assertThatThrownBy(() -> adapter.blocksFor(7L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.CONFLICT))
                .hasCause(busy);
    }

    @Test
    @DisplayName("잠금 대기 밖의 오류는 그대로 둔다")
    void keepsOtherFailures() {
        BusinessException other = new BusinessException(OrganizationErrorCode.LAST_OWNER);
        useCase.failure = other;

        assertThatThrownBy(() -> adapter.blocksFor(7L)).isSameAs(other);
    }

    private static final class RecordingUseCase implements GetWithdrawalBlockingOwnershipsUseCase {

        private final List<GetWithdrawalBlockingOwnershipsQuery> queries = new ArrayList<>();
        private WithdrawalBlockingOwnershipsInfo result;
        private RuntimeException failure;

        @Override
        public WithdrawalBlockingOwnershipsInfo get(GetWithdrawalBlockingOwnershipsQuery query) {
            queries.add(query);
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
