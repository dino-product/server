package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("참여 요청")
class ParticipationRequestTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-06T00:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-10-07T00:00:00Z");
    private static final ParticipationRequestId ID = new ParticipationRequestId(30L);
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final MembershipId DECIDER = new MembershipId(3L);

    @Test
    @DisplayName("새 요청은 희망 유형을 가진 승인 대기 요청이며 확정 유형·처리 시각·거절 사유가 없다")
    void createsPendingRequest() {
        ParticipationRequest request =
                ParticipationRequest.request(ORGANIZATION_ID, ACCOUNT_ID, ParticipationType.TECHNICIAN, REQUESTED_AT);

        assertThat(request.id()).isEmpty();
        assertThat(request.isPending()).isTrue();
        assertThat(request.desiredType()).isEqualTo(ParticipationType.TECHNICIAN);
        assertThat(request.confirmedType()).isEmpty();
        assertThat(request.decidedAt()).isEmpty();
        assertThat(request.rejectReason()).isEmpty();
    }

    @Test
    @DisplayName("희망과 다른 확정 유형으로 승인된 요청을 복원한다")
    void restoresApprovedRequestWithDifferentConfirmedType() {
        ParticipationRequest request =
                restore(ParticipationType.STAFF, ParticipationRequestStatus.APPROVED, null, DECIDED_AT);

        assertThat(request.desiredType()).isEqualTo(ParticipationType.TECHNICIAN);
        assertThat(request.confirmedType()).contains(ParticipationType.STAFF);
        assertThat(request.isPending()).isFalse();
    }

    @Test
    @DisplayName("거절 사유는 거절된 요청에만 있을 수 있다")
    void allowsRejectReasonOnlyForRejectedRequest() {
        assertThat(restore(null, ParticipationRequestStatus.REJECTED, "인원 마감", DECIDED_AT)
                        .rejectReason())
                .contains("인원 마감");
        assertThatThrownBy(() -> restore(null, ParticipationRequestStatus.CANCELED, "인원 마감", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("처리 시각은 승인·거절된 요청에만, 확정 유형은 승인된 요청에만 있다")
    void requiresDecisionFieldsMatchingStatus() {
        assertThatThrownBy(() -> restore(null, ParticipationRequestStatus.PENDING, null, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(null, ParticipationRequestStatus.REJECTED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(null, ParticipationRequestStatus.APPROVED, null, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> restore(ParticipationType.STAFF, ParticipationRequestStatus.REJECTED, null, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("처리 시각은 요청 시각보다 앞설 수 없다")
    void rejectsDecisionBeforeRequest() {
        assertThatThrownBy(() -> restore(null, ParticipationRequestStatus.REJECTED, null, REQUESTED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("승인 대기 요청을 취소하면 처리 시각 없이 취소 상태가 된다")
    void cancelsPendingRequest() {
        ParticipationRequest request =
                ParticipationRequest.request(ORGANIZATION_ID, ACCOUNT_ID, ParticipationType.STAFF, REQUESTED_AT);

        request.cancel();

        assertThat(request.status()).isEqualTo(ParticipationRequestStatus.CANCELED);
        assertThat(request.isPending()).isFalse();
        assertThat(request.decidedAt()).isEmpty();
    }

    @Test
    @DisplayName("승인 대기가 아닌 요청은 취소할 수 없다")
    void rejectsCancelOfDecidedOrCanceledRequest() {
        ParticipationRequest rejected = restore(null, ParticipationRequestStatus.REJECTED, null, DECIDED_AT);
        ParticipationRequest canceled = restore(null, ParticipationRequestStatus.CANCELED, null, null);

        assertThatThrownBy(() -> rejected.cancel()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> canceled.cancel()).isInstanceOf(IllegalStateException.class);
        assertThat(rejected.status()).isEqualTo(ParticipationRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("처리한 총관리자 소속은 승인·거절된 요청에만 있다")
    void requiresDeciderOnlyForDecidedRequest() {
        assertThatThrownBy(() -> ParticipationRequest.reconstitute(
                        ID,
                        ORGANIZATION_ID,
                        ACCOUNT_ID,
                        ParticipationType.STAFF,
                        null,
                        ParticipationRequestStatus.REJECTED,
                        null,
                        REQUESTED_AT,
                        DECIDED_AT,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParticipationRequest.reconstitute(
                        ID,
                        ORGANIZATION_ID,
                        ACCOUNT_ID,
                        ParticipationType.STAFF,
                        null,
                        ParticipationRequestStatus.CANCELED,
                        null,
                        REQUESTED_AT,
                        null,
                        DECIDER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("희망 유형 그대로 승인하면 확정 유형·처리자·처리 시각을 남긴다")
    void approvesWithDesiredType() {
        ParticipationRequest request = pending();

        request.approve(ParticipationType.TECHNICIAN, DECIDER, DECIDED_AT);

        assertThat(request.status()).isEqualTo(ParticipationRequestStatus.APPROVED);
        assertThat(request.confirmedType()).contains(ParticipationType.TECHNICIAN);
        assertThat(request.decidedBy()).contains(DECIDER);
        assertThat(request.decidedAt()).contains(DECIDED_AT);
        assertThat(request.rejectReason()).isEmpty();
        assertThat(request.isConfirmedAsDesired()).isTrue();
    }

    @Test
    @DisplayName("희망과 다른 유형으로 승인하면 희망 유형은 그대로 두고 확정 유형만 다르게 남긴다")
    void approvesWithDifferentType() {
        ParticipationRequest request = pending();

        request.approve(ParticipationType.STAFF, DECIDER, DECIDED_AT);

        assertThat(request.desiredType()).isEqualTo(ParticipationType.TECHNICIAN);
        assertThat(request.confirmedType()).contains(ParticipationType.STAFF);
        assertThat(request.isConfirmedAsDesired()).isFalse();
    }

    @Test
    @DisplayName("사유와 함께 거절하면 사유·처리자·처리 시각을 남기고 확정 유형은 없다")
    void rejectsWithReason() {
        ParticipationRequest request = pending();

        request.reject(RejectReason.from("  인원 마감  "), DECIDER, DECIDED_AT);

        assertThat(request.status()).isEqualTo(ParticipationRequestStatus.REJECTED);
        assertThat(request.rejectReason()).contains("인원 마감");
        assertThat(request.confirmedType()).isEmpty();
        assertThat(request.decidedBy()).contains(DECIDER);
        assertThat(request.decidedAt()).contains(DECIDED_AT);
    }

    @Test
    @DisplayName("사유 없이 거절할 수 있다")
    void rejectsWithoutReason() {
        ParticipationRequest request = pending();

        request.reject(Optional.empty(), DECIDER, DECIDED_AT);

        assertThat(request.status()).isEqualTo(ParticipationRequestStatus.REJECTED);
        assertThat(request.rejectReason()).isEmpty();
    }

    @Test
    @DisplayName("승인 대기가 아닌 요청은 승인·거절할 수 없고 상태가 바뀌지 않는다")
    void refusesDecisionOfNonPendingRequest() {
        ParticipationRequest approved =
                restore(ParticipationType.STAFF, ParticipationRequestStatus.APPROVED, null, DECIDED_AT);
        ParticipationRequest canceled = restore(null, ParticipationRequestStatus.CANCELED, null, null);

        assertThatThrownBy(() -> approved.approve(ParticipationType.TECHNICIAN, DECIDER, DECIDED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> canceled.reject(Optional.empty(), DECIDER, DECIDED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThat(approved.confirmedType()).contains(ParticipationType.STAFF);
        assertThat(canceled.status()).isEqualTo(ParticipationRequestStatus.CANCELED);
    }

    @Test
    @DisplayName("요청 시각보다 앞선 시각으로 처리할 수 없다")
    void refusesDecisionBeforeRequest() {
        ParticipationRequest request = pending();

        assertThatThrownBy(() -> request.approve(ParticipationType.STAFF, DECIDER, REQUESTED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(request.isPending()).isTrue();
    }

    @Test
    @DisplayName("요청한 계정인지 확인한다")
    void checksRequester() {
        ParticipationRequest request =
                ParticipationRequest.request(ORGANIZATION_ID, ACCOUNT_ID, ParticipationType.STAFF, REQUESTED_AT);

        assertThat(request.isRequestedBy(ACCOUNT_ID)).isTrue();
        assertThat(request.isRequestedBy(new AccountId(8L))).isFalse();
    }

    private static ParticipationRequest restore(
            ParticipationType confirmedType,
            ParticipationRequestStatus status,
            String rejectReason,
            Instant decidedAt) {
        return ParticipationRequest.reconstitute(
                ID,
                ORGANIZATION_ID,
                ACCOUNT_ID,
                ParticipationType.TECHNICIAN,
                confirmedType,
                status,
                rejectReason,
                REQUESTED_AT,
                decidedAt,
                decidedAt == null ? null : DECIDER);
    }

    private static ParticipationRequest pending() {
        return ParticipationRequest.request(ORGANIZATION_ID, ACCOUNT_ID, ParticipationType.TECHNICIAN, REQUESTED_AT);
    }
}
