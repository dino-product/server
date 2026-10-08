package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.DesignateOwnerCommand;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;

@DisplayName("총관리자 지정")
class DesignateOwnerServiceTest {

    private static final OrganizationId ORGANIZATION = new OrganizationId(1L);
    private static final OrganizationId OTHER_ORGANIZATION = new OrganizationId(2L);
    private static final AccountId REQUESTER = new AccountId(7L);

    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository();

    private DesignateOwnerService service;

    @BeforeEach
    void setUp() {
        service = new DesignateOwnerService(organizationRepository, memberships);
    }

    @Test
    @DisplayName("총관리자가 같은 발주사 직원을 지정하면 총관리자가 추가되고 기존 총관리자는 유지된다")
    void designatesStaffAsAdditionalOwner() {
        Membership requester = memberships.given(ORGANIZATION, REQUESTER, true);
        Membership staff = memberships.given(ORGANIZATION, new AccountId(8L), false);

        service.designate(command(staff));

        assertThat(memberships.stored(id(staff)).isOwner()).isTrue();
        assertThat(memberships.stored(id(requester)).isOwner()).isTrue();
        verify(organizationRepository).lock(ORGANIZATION);
    }

    @Test
    @DisplayName("이미 총관리자인 소속을 다시 지정하면 저장하지 않고 성공한다")
    void designatingExistingOwnerSucceedsWithoutSaving() {
        memberships.given(ORGANIZATION, REQUESTER, true);
        Membership otherOwner = memberships.given(ORGANIZATION, new AccountId(8L), true);

        service.designate(command(otherOwner));

        assertThat(memberships.stored(id(otherOwner)).isOwner()).isTrue();
        assertThat(memberships.saveCount()).isZero();
    }

    @Test
    @DisplayName("요청자가 그 발주사의 활성 소속이 아니면 403으로 거부한다")
    void rejectsRequesterOutsideOrganization() {
        memberships.given(OTHER_ORGANIZATION, REQUESTER, true);
        Membership staff = memberships.given(ORGANIZATION, new AccountId(8L), false);

        assertRejected(command(staff), OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
        verify(organizationRepository).lock(ORGANIZATION);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원이 요청하면 403으로 거부한다")
    void rejectsStaffRequester() {
        memberships.given(ORGANIZATION, REQUESTER, false);
        Membership staff = memberships.given(ORGANIZATION, new AccountId(8L), false);

        assertRejected(command(staff), OrganizationErrorCode.OWNER_ONLY);
    }

    @Test
    @DisplayName("대상 소속이 없으면 404로 거부한다")
    void rejectsMissingTarget() {
        memberships.given(ORGANIZATION, REQUESTER, true);

        assertRejected(new DesignateOwnerCommand(7L, 1L, 99L), OrganizationErrorCode.MEMBERSHIP_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 발주사의 소속을 지정하면 없는 소속처럼 404로 거부한다")
    void hidesMembershipOfOtherOrganization() {
        memberships.given(ORGANIZATION, REQUESTER, true);
        Membership outsider = memberships.given(OTHER_ORGANIZATION, new AccountId(8L), false);

        assertRejected(command(outsider), OrganizationErrorCode.MEMBERSHIP_NOT_FOUND);
        assertThat(memberships.stored(id(outsider)).isOwner()).isFalse();
    }

    private void assertRejected(DesignateOwnerCommand command, BaseCode expected) {
        assertThatThrownBy(() -> service.designate(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expected));
        assertThat(memberships.saveCount()).isZero();
    }

    private static DesignateOwnerCommand command(Membership target) {
        return new DesignateOwnerCommand(
                REQUESTER.value(), ORGANIZATION.value(), id(target).value());
    }

    private static MembershipId id(Membership membership) {
        return membership.id().orElseThrow();
    }
}
