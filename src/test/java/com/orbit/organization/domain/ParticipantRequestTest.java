package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ParticipantRequestTest {
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @ParameterizedTest
    @EnumSource(RequestChannel.class)
    void createsPendingRequestWithOriginalAccountAndChannel(RequestChannel channel) {
        var request = ParticipantRequest.create(
                new ParticipantRequestId(1L),
                new OrganizationId(2L),
                new AuthAccountId(3L),
                ParticipantType.STAFF,
                channel,
                NOW);
        assertThat(request.id()).isEqualTo(new ParticipantRequestId(1L));
        assertThat(request.organizationId()).isEqualTo(new OrganizationId(2L));
        assertThat(request.authAccountId()).isEqualTo(new AuthAccountId(3L));
        assertThat(request.requestedType()).isEqualTo(ParticipantType.STAFF);
        assertThat(request.channel()).isEqualTo(channel);
        assertThat(request.status()).isEqualTo(ParticipantRequestStatus.PENDING);
        assertThat(request.requestedAt()).isEqualTo(NOW);
        assertThat(request.confirmedType()).isNull();
        assertThat(request.processedAt()).isNull();
        assertThat(request.rejectionReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ParticipantType.class)
    void approvalRecordsFinalTypeWithoutChangingRequestedType(ParticipantType confirmedType) {
        var request = pending();
        request.approve(confirmedType, NOW.plusSeconds(1));
        assertThat(request.status()).isEqualTo(ParticipantRequestStatus.APPROVED);
        assertThat(request.requestedType()).isEqualTo(ParticipantType.STAFF);
        assertThat(request.confirmedType()).isEqualTo(confirmedType);
        assertThat(request.processedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(request.rejectionReason()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "정원이 찼습니다"})
    void rejectionAllowsOptionalReason(String reason) {
        var request = pending();
        request.reject(reason, NOW);
        assertThat(request.status()).isEqualTo(ParticipantRequestStatus.REJECTED);
        assertThat(request.rejectionReason()).isEqualTo(reason == null || reason.isBlank() ? null : reason);
        assertThat(request.confirmedType()).isNull();
        assertThat(request.processedAt()).isEqualTo(NOW);
    }

    @Test
    void cancellationRetainsOriginalRequest() {
        var request = pending();
        request.cancel(NOW);
        assertThat(request.status()).isEqualTo(ParticipantRequestStatus.CANCELLED);
        assertThat(request.requestedType()).isEqualTo(ParticipantType.STAFF);
        assertThat(request.confirmedType()).isNull();
        assertThat(request.processedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @EnumSource(
            value = ParticipantRequestStatus.class,
            names = {"APPROVED", "REJECTED", "CANCELLED"})
    void terminalRequestCannotBeProcessedAgain(ParticipantRequestStatus status) {
        var request = pending();
        switch (status) {
            case APPROVED -> request.approve(ParticipantType.TECHNICIAN, NOW);
            case REJECTED -> request.reject("사유", NOW);
            case CANCELLED -> request.cancel(NOW);
            default -> throw new AssertionError(status);
        }
        assertThatThrownBy(() -> request.approve(ParticipantType.STAFF, NOW))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> request.reject("변경", NOW)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> request.cancel(NOW)).isInstanceOf(OrganizationRuleViolation.class);
        assertThat(request.status()).isEqualTo(status);
        assertThat(request.processedAt()).isEqualTo(NOW);
        assertThat(request.confirmedType())
                .isEqualTo(status == ParticipantRequestStatus.APPROVED ? ParticipantType.TECHNICIAN : null);
        assertThat(request.rejectionReason()).isEqualTo(status == ParticipantRequestStatus.REJECTED ? "사유" : null);
    }

    @Test
    void invalidProcessingPreservesPendingState() {
        var request = pending();
        assertThatThrownBy(() -> request.approve(null, NOW)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> request.approve(ParticipantType.STAFF, NOW.minusSeconds(1)))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> request.reject("사유", NOW.minusSeconds(1)))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> request.cancel(null)).isInstanceOf(OrganizationRuleViolation.class);
        assertThat(request.status()).isEqualTo(ParticipantRequestStatus.PENDING);
        assertThat(request.processedAt()).isNull();
        assertThat(request.confirmedType()).isNull();
        assertThat(request.rejectionReason()).isNull();
    }

    @Test
    void rejectsMissingCreationFields() {
        var id = new ParticipantRequestId(1L);
        var organization = new OrganizationId(2L);
        var account = new AuthAccountId(3L);
        assertThatThrownBy(() -> ParticipantRequest.create(
                        null, organization, account, ParticipantType.STAFF, RequestChannel.CODE, NOW))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() ->
                        ParticipantRequest.create(id, null, account, ParticipantType.STAFF, RequestChannel.CODE, NOW))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> ParticipantRequest.create(
                        id, organization, null, ParticipantType.STAFF, RequestChannel.CODE, NOW))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> ParticipantRequest.create(id, organization, account, null, RequestChannel.CODE, NOW))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> ParticipantRequest.create(id, organization, account, ParticipantType.STAFF, null, NOW))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> ParticipantRequest.create(
                        id, organization, account, ParticipantType.STAFF, RequestChannel.CODE, null))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidIdentifiers(Long value) {
        assertThatThrownBy(() -> new AuthAccountId(value)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> new OrganizationId(value)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> new ParticipantRequestId(value)).isInstanceOf(OrganizationRuleViolation.class);
    }

    private ParticipantRequest pending() {
        return ParticipantRequest.create(
                new ParticipantRequestId(1L),
                new OrganizationId(2L),
                new AuthAccountId(3L),
                ParticipantType.STAFF,
                RequestChannel.CODE,
                NOW);
    }
}
