package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ParticipationPolicyTest {
    private static final OrganizationId ORGANIZATION = new OrganizationId(1L);
    private static final AuthAccountId ACCOUNT = new AuthAccountId(1L);
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void existingMembershipBlocksEitherRelationshipAndNewRequestEvenWhenInactive(boolean active) {
        var membership = membership(ORGANIZATION, ACCOUNT);
        if (!active) {
            membership.deactivate();
        }
        assertThatThrownBy(() -> ParticipationPolicy.requireCanCreateRelationship(
                        ORGANIZATION, ACCOUNT, List.of(membership), List.of()))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> ParticipationPolicy.requireCanRequest(
                        ORGANIZATION, ACCOUNT, List.of(membership), List.of(), List.of()))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void existingContractBlocksEitherRelationshipAndNewRequestEvenWhenInactive(boolean active) {
        var technician = technician(ORGANIZATION, ACCOUNT);
        if (!active) {
            technician.deactivate();
        }
        assertThatThrownBy(() -> ParticipationPolicy.requireCanCreateRelationship(
                        ORGANIZATION, ACCOUNT, List.of(), List.of(technician)))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> ParticipationPolicy.requireCanRequest(
                        ORGANIZATION, ACCOUNT, List.of(), List.of(technician), List.of()))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    @Test
    void otherAccountsAndOtherOrganizationsDoNotBlockParticipation() {
        var otherOrganization = new OrganizationId(2L);
        var otherAccount = new AuthAccountId(2L);
        var memberships = List.of(membership(otherOrganization, ACCOUNT), membership(ORGANIZATION, otherAccount));
        var technicians = List.of(technician(otherOrganization, ACCOUNT), technician(ORGANIZATION, otherAccount));
        var requests = List.of(request(otherOrganization, ACCOUNT), request(ORGANIZATION, otherAccount));
        assertThatCode(() -> ParticipationPolicy.requireCanCreateRelationship(
                        ORGANIZATION, ACCOUNT, memberships, technicians))
                .doesNotThrowAnyException();
        assertThatCode(() -> ParticipationPolicy.requireCanRequest(
                        ORGANIZATION, ACCOUNT, memberships, technicians, requests))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(ParticipantRequestStatus.class)
    void onlyPendingRequestBlocksAnotherRequest(ParticipantRequestStatus status) {
        var request = request(ORGANIZATION, ACCOUNT);
        switch (status) {
            case APPROVED -> request.approve(ParticipantType.STAFF, NOW);
            case REJECTED -> request.reject(null, NOW);
            case CANCELLED -> request.cancel(NOW);
            default -> {}
        }
        if (status == ParticipantRequestStatus.PENDING) {
            assertThatThrownBy(() -> ParticipationPolicy.requireCanRequest(
                            ORGANIZATION, ACCOUNT, List.of(), List.of(), List.of(request)))
                    .isInstanceOf(OrganizationRuleViolation.class);
        } else {
            assertThatCode(() -> ParticipationPolicy.requireCanRequest(
                            ORGANIZATION, ACCOUNT, List.of(), List.of(), List.of(request)))
                    .doesNotThrowAnyException();
        }
        // 승인할 요청 자체가 대기 중이라는 이유로 실제 관계 생성을 차단하지 않는다.
        assertThatCode(() ->
                        ParticipationPolicy.requireCanCreateRelationship(ORGANIZATION, ACCOUNT, List.of(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingEvidenceRatherThanTreatingItAsNoRelationship() {
        assertThatThrownBy(() -> ParticipationPolicy.requireCanCreateRelationship(null, ACCOUNT, List.of(), List.of()))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() ->
                        ParticipationPolicy.requireCanCreateRelationship(ORGANIZATION, null, List.of(), List.of()))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(
                        () -> ParticipationPolicy.requireCanCreateRelationship(ORGANIZATION, ACCOUNT, null, List.of()))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(
                        () -> ParticipationPolicy.requireCanCreateRelationship(ORGANIZATION, ACCOUNT, List.of(), null))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(
                        () -> ParticipationPolicy.requireCanRequest(ORGANIZATION, ACCOUNT, List.of(), List.of(), null))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> ParticipationPolicy.requireCanCreateRelationship(
                        ORGANIZATION, ACCOUNT, Arrays.asList((Membership) null), List.of()))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    private Membership membership(OrganizationId organization, AuthAccountId account) {
        return Membership.create(new MembershipId(1L), organization, account, null, NOW);
    }

    private Technician technician(OrganizationId organization, AuthAccountId account) {
        return Technician.create(new TechnicianId(1L), organization, account, null, NOW);
    }

    private ParticipantRequest request(OrganizationId organization, AuthAccountId account) {
        return ParticipantRequest.create(
                new ParticipantRequestId(1L), organization, account, ParticipantType.STAFF, RequestChannel.CODE, NOW);
    }
}
