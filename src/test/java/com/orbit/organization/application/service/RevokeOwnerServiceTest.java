package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.RevokeOwnerCommand;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;

@DisplayName("총관리자 해제")
class RevokeOwnerServiceTest {

    private static final OrganizationId ORGANIZATION = new OrganizationId(1L);
    private static final OrganizationId OTHER_ORGANIZATION = new OrganizationId(2L);
    private static final AccountId REQUESTER = new AccountId(7L);

    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();

    private RevokeOwnerService service;

    @BeforeEach
    void setUp() {
        service = new RevokeOwnerService(organizationRepository, memberships);
    }

    @Test
    @DisplayName("총관리자가 다른 총관리자를 해제하면 그 소속만 직원이 된다")
    void revokesOtherOwner() {
        Membership requester = memberships.given(ORGANIZATION, REQUESTER, true);
        Membership otherOwner = memberships.given(ORGANIZATION, new AccountId(8L), true);

        service.revoke(command(otherOwner));

        assertThat(memberships.stored(id(otherOwner)).isOwner()).isFalse();
        assertThat(memberships.stored(id(requester)).isOwner()).isTrue();
        verify(organizationRepository).lock(ORGANIZATION);
    }

    @Test
    @DisplayName("다른 총관리자가 있으면 본인을 해제할 수 있다")
    void revokesSelfWhileAnotherOwnerRemains() {
        Membership requester = memberships.given(ORGANIZATION, REQUESTER, true);
        memberships.given(ORGANIZATION, new AccountId(8L), true);

        service.revoke(command(requester));

        assertThat(memberships.stored(id(requester)).isOwner()).isFalse();
    }

    @Test
    @DisplayName("마지막 총관리자인 본인을 해제하면 409로 거부한다")
    void rejectsRevokingLastOwner() {
        Membership requester = memberships.given(ORGANIZATION, REQUESTER, true);
        memberships.given(ORGANIZATION, new AccountId(8L), false);

        assertRejected(command(requester), OrganizationErrorCode.LAST_OWNER);
        assertThat(memberships.stored(id(requester)).isOwner()).isTrue();
    }

    @Test
    @DisplayName("다른 발주사의 총관리자는 마지막 1명 판단에 세지 않는다")
    void countsOnlyOwnersOfSameOrganization() {
        Membership requester = memberships.given(ORGANIZATION, REQUESTER, true);
        memberships.given(OTHER_ORGANIZATION, new AccountId(8L), true);

        assertRejected(command(requester), OrganizationErrorCode.LAST_OWNER);
    }

    @Test
    @DisplayName("총관리자가 아닌 소속을 해제하면 저장하지 않고 성공한다")
    void revokingStaffSucceedsWithoutSaving() {
        memberships.given(ORGANIZATION, REQUESTER, true);
        Membership staff = memberships.given(ORGANIZATION, new AccountId(8L), false);

        service.revoke(command(staff));

        assertThat(memberships.stored(id(staff)).isOwner()).isFalse();
        assertThat(memberships.saveCount()).isZero();
    }

    @Test
    @DisplayName("요청자가 그 발주사의 활성 소속이 아니면 403으로 거부한다")
    void rejectsRequesterOutsideOrganization() {
        memberships.given(OTHER_ORGANIZATION, REQUESTER, true);
        Membership owner = memberships.given(ORGANIZATION, new AccountId(8L), true);
        memberships.given(ORGANIZATION, new AccountId(9L), true);

        assertRejected(command(owner), OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원이 요청하면 403으로 거부한다")
    void rejectsStaffRequester() {
        memberships.given(ORGANIZATION, REQUESTER, false);
        Membership owner = memberships.given(ORGANIZATION, new AccountId(8L), true);
        memberships.given(ORGANIZATION, new AccountId(9L), true);

        assertRejected(command(owner), OrganizationErrorCode.OWNER_ONLY);
    }

    @Test
    @DisplayName("대상 소속이 없으면 404로 거부한다")
    void rejectsMissingTarget() {
        memberships.given(ORGANIZATION, REQUESTER, true);

        assertRejected(new RevokeOwnerCommand(7L, 1L, 99L), OrganizationErrorCode.MEMBERSHIP_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 발주사의 소속을 해제하면 없는 소속처럼 404로 거부한다")
    void hidesMembershipOfOtherOrganization() {
        memberships.given(ORGANIZATION, REQUESTER, true);
        Membership outsider = memberships.given(OTHER_ORGANIZATION, new AccountId(8L), true);
        memberships.given(OTHER_ORGANIZATION, new AccountId(9L), true);

        assertRejected(command(outsider), OrganizationErrorCode.MEMBERSHIP_NOT_FOUND);
        assertThat(memberships.stored(id(outsider)).isOwner()).isTrue();
    }

    private void assertRejected(RevokeOwnerCommand command, BaseCode expected) {
        assertThatThrownBy(() -> service.revoke(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expected));
        assertThat(memberships.saveCount()).isZero();
    }

    private static RevokeOwnerCommand command(Membership target) {
        return new RevokeOwnerCommand(
                REQUESTER.value(), ORGANIZATION.value(), id(target).value());
    }

    private static MembershipId id(Membership membership) {
        return membership.id().orElseThrow();
    }
}
