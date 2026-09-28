package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.ChangeCompanyCodeCommand;
import com.orbit.organization.application.port.out.CompanyCodeConflictException;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.shared.error.BusinessException;

class ChangeCompanyCodeServiceTest {
    private static final OrganizationId ID = new OrganizationId(11L);
    private static final AuthAccountId ACCOUNT = new AuthAccountId(9L);
    private static final CompanyCode OLD_CODE = new CompanyCode("C0DE1234");
    private static final CompanyCode NEW_CODE = new CompanyCode("NEWC0DE1");
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    private OrganizationRepository organizations;
    private MembershipRepository memberships;
    private CompanyCodeGenerator codes;
    private ChangeCompanyCodeService service;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        memberships = mock(MembershipRepository.class);
        codes = mock(CompanyCodeGenerator.class);
        service = new ChangeCompanyCodeService(organizations, memberships, codes);
    }

    @Test
    void ownerChangesCodeAfterLockingAndPreservesOtherOrganizationState() {
        var owner = member(1L, ID, true);
        var organization = organization(owner.id());
        stubOwner(organization, owner);
        when(codes.generate()).thenReturn(NEW_CODE);

        var result = service.change(new ChangeCompanyCodeCommand(ACCOUNT.value(), ID.value()));

        assertThat(result.organizationId()).isEqualTo(ID.value());
        assertThat(result.code()).isEqualTo(NEW_CODE.value());
        assertThat(organization.code()).isEqualTo(NEW_CODE);
        assertThat(organization.name().value()).isEqualTo("회사명");
        assertThat(organization.industry()).isEqualTo(Industry.OTHER);
        assertThat(organization.ownerMembershipId()).isEqualTo(owner.id());
        verify(organizations).update(organization);
        verify(organizations).flush();
        var order = inOrder(organizations, memberships);
        order.verify(organizations).findByIdForUpdate(ID);
        order.verify(memberships).findByOrganizationAndAccount(ID, ACCOUNT);
    }

    @Test
    void rejectsMissingOrganizationAndUnauthorizedAccountsBeforeGeneratingCode() {
        assertForbidden(); // 회사 없음

        var organization = organization(new MembershipId(1L));
        when(organizations.findByIdForUpdate(ID)).thenReturn(Optional.of(organization));
        assertForbidden(); // 비소속

        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(member(2L, ID, true)));
        assertForbidden(); // 일반 직원

        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(member(1L, ID, false)));
        assertForbidden(); // 비활성 직원

        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT))
                .thenReturn(Optional.of(member(1L, new OrganizationId(22L), true)));
        assertForbidden(); // 타사 직원

        assertThat(organization.code()).isEqualTo(OLD_CODE);
        verifyNoInteractions(codes);
        verify(organizations, never()).update(organization);
        verify(organizations, never()).flush();
    }

    @Test
    void retriesWhenGeneratedCodeEqualsCurrentCode() {
        var owner = member(1L, ID, true);
        var organization = organization(owner.id());
        stubOwner(organization, owner);
        when(codes.generate()).thenReturn(OLD_CODE, NEW_CODE);
        when(organizations.existsByCode(OLD_CODE)).thenReturn(true);

        var result = service.change(new ChangeCompanyCodeCommand(ACCOUNT.value(), ID.value()));

        assertThat(result.code()).isEqualTo(NEW_CODE.value());
        verify(codes, times(2)).generate();
        verify(organizations).existsByCode(OLD_CODE);
        verify(organizations).existsByCode(NEW_CODE);
    }

    @Test
    void fiveDuplicateCandidatesExhaustWithoutChangingOrganization() {
        var owner = member(1L, ID, true);
        var organization = organization(owner.id());
        stubOwner(organization, owner);
        when(codes.generate()).thenReturn(OLD_CODE);
        when(organizations.existsByCode(OLD_CODE)).thenReturn(true);

        assertThatThrownBy(() -> service.change(new ChangeCompanyCodeCommand(ACCOUNT.value(), ID.value())))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.COMPANY_CODE_EXHAUSTED));

        assertThat(organization.code()).isEqualTo(OLD_CODE);
        verify(codes, times(5)).generate();
        verify(organizations, never()).update(organization);
        verify(organizations, never()).flush();
    }

    @Test
    void mapsDatabaseCodeConflictToRetryableError() {
        var owner = member(1L, ID, true);
        var organization = organization(owner.id());
        stubOwner(organization, owner);
        when(codes.generate()).thenReturn(NEW_CODE);
        doThrow(new CompanyCodeConflictException(new IllegalStateException("duplicate")))
                .when(organizations)
                .flush();

        assertThatThrownBy(() -> service.change(new ChangeCompanyCodeCommand(ACCOUNT.value(), ID.value())))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.COMPANY_CODE_CONFLICT);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void invalidIdsFailBeforeLockingAndNullAccountIsProgrammingError() {
        assertThatThrownBy(() -> service.change(new ChangeCompanyCodeCommand(0L, ID.value())))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT));
        assertThatThrownBy(() -> service.change(new ChangeCompanyCodeCommand(ACCOUNT.value(), 0L)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT));
        assertThatThrownBy(() -> service.change(new ChangeCompanyCodeCommand(null, ID.value())))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(organizations, memberships, codes);
    }

    private void stubOwner(Organization organization, Membership owner) {
        when(organizations.findByIdForUpdate(ID)).thenReturn(Optional.of(organization));
        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(owner));
    }

    private void assertForbidden() {
        assertThatThrownBy(() -> service.change(new ChangeCompanyCodeCommand(ACCOUNT.value(), ID.value())))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.NOT_ORGANIZATION_OWNER);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    private Organization organization(MembershipId ownerId) {
        return Organization.reconstitute(ID, new OrganizationName("회사명"), Industry.OTHER, OLD_CODE, ownerId);
    }

    private Membership member(long id, OrganizationId organizationId, boolean active) {
        return Membership.reconstitute(new MembershipId(id), organizationId, ACCOUNT, null, NOW, active);
    }
}
