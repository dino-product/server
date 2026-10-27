package com.orbit.schedule.adapter.in.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.profile.WithdrawalBlock;
import com.orbit.profile.WithdrawalBlockReason;
import com.orbit.schedule.application.port.in.query.GetWithdrawalBlockingWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetWithdrawalBlockingWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WithdrawalBlockingWorksInfo;

@DisplayName("profile 탈퇴 차단 판정 요구 인터페이스 구현")
class WithdrawalBlockerAdapterTest {

    private final RecordingUseCase useCase = new RecordingUseCase();
    private final WithdrawalBlockerAdapter adapter = new WithdrawalBlockerAdapter(useCase);

    @Test
    @DisplayName("그 계정의 판정을 요청하고 작업중·배정됨 작업 수를 각 사유의 건수로 옮긴다")
    void translatesBlockingWorksIntoReasons() {
        useCase.result = new WithdrawalBlockingWorksInfo(1, 2);

        assertThat(adapter.blocksFor(7L))
                .containsExactly(
                        new WithdrawalBlock(WithdrawalBlockReason.WORK_IN_PROGRESS, 1),
                        new WithdrawalBlock(WithdrawalBlockReason.WORK_PENDING_ACCEPT, 2));
        assertThat(useCase.queries).containsExactly(new GetWithdrawalBlockingWorksQuery(7L));
    }

    @Test
    @DisplayName("작업중 작업만 있으면 작업중 사유만 돌려준다")
    void returnsOnlyInProgressReason() {
        useCase.result = new WithdrawalBlockingWorksInfo(3, 0);

        assertThat(adapter.blocksFor(7L))
                .containsExactly(new WithdrawalBlock(WithdrawalBlockReason.WORK_IN_PROGRESS, 3));
    }

    @Test
    @DisplayName("응답 전(배정됨) 작업만 있으면 배정됨 사유만 돌려준다")
    void returnsOnlyPendingAcceptReason() {
        useCase.result = new WithdrawalBlockingWorksInfo(0, 4);

        assertThat(adapter.blocksFor(7L))
                .containsExactly(new WithdrawalBlock(WithdrawalBlockReason.WORK_PENDING_ACCEPT, 4));
    }

    @Test
    @DisplayName("막을 작업이 없으면 빈 목록이다")
    void returnsNothingWithoutBlockingWorks() {
        useCase.result = new WithdrawalBlockingWorksInfo(0, 0);

        assertThat(adapter.blocksFor(7L)).isEmpty();
    }

    private static final class RecordingUseCase implements GetWithdrawalBlockingWorksUseCase {

        private final List<GetWithdrawalBlockingWorksQuery> queries = new ArrayList<>();
        private WithdrawalBlockingWorksInfo result;

        @Override
        public WithdrawalBlockingWorksInfo get(GetWithdrawalBlockingWorksQuery query) {
            queries.add(query);
            return result;
        }
    }
}
