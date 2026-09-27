package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.out.CompanyCodeConflictException;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationIdentityPort;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationRuleViolation;
import com.orbit.shared.error.BusinessException;

class CreateOrganizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final CompanyCode FIRST_CODE = new CompanyCode("C0DE1234");
    private static final CompanyCode SECOND_CODE = new CompanyCode("NEWC0DE1");

    private OrganizationRepository organizations;
    private MembershipRepository memberships;
    private OrganizationIdentityPort identities;
    private CompanyCodeGenerator codes;
    private CreateOrganizationService service;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        memberships = mock(MembershipRepository.class);
        identities = mock(OrganizationIdentityPort.class);
        codes = mock(CompanyCodeGenerator.class);
        service = new CreateOrganizationService(
                organizations, memberships, identities, codes, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 30})
    void createsOrganizationWithBoundaryLengthNameAndOptionalIndustry(int length) {
        stubIdsAndCode();
        var result = service.create(new CreateOrganizationCommand(9L, "가".repeat(length), null));

        assertThat(result.organizationId()).isEqualTo(11L);
        assertThat(result.membershipId()).isEqualTo(12L);
        assertThat(result.code()).isEqualTo(FIRST_CODE.value());
        var organization = ArgumentCaptor.forClass(Organization.class);
        var membership = ArgumentCaptor.forClass(Membership.class);
        verify(organizations).save(organization.capture());
        verify(memberships).save(membership.capture());
        verify(organizations).flush();
        assertThat(organization.getValue().name().value()).hasSize(length);
        assertThat(organization.getValue().industry()).isNull();
        assertThat(organization.getValue().ownerMembershipId())
                .isEqualTo(membership.getValue().id());
        assertThat(organization.getValue().isManagedBy(membership.getValue())).isTrue();
        assertThat(membership.getValue().authAccountId().value()).isEqualTo(9L);
        assertThat(membership.getValue().joinedAt()).isEqualTo(NOW);
    }

    @Test
    void preservesSelectedIndustry() {
        stubIdsAndCode();
        service.create(new CreateOrganizationCommand(9L, "회사명", Industry.OTHER));
        var organization = ArgumentCaptor.forClass(Organization.class);
        verify(organizations).save(organization.capture());
        assertThat(organization.getValue().industry()).isEqualTo(Industry.OTHER);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 31})
    void rejectsInvalidNameBeforeAllocatingIds(int length) {
        assertThatThrownBy(() -> service.create(new CreateOrganizationCommand(9L, "가".repeat(length), null)))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verifyNoInteractions(identities, codes, organizations, memberships);
    }

    @Test
    void rejectsInvalidAccountIdButLeavesMissingAccountIdAsProgrammingError() {
        assertThatThrownBy(() -> service.create(new CreateOrganizationCommand(0L, "회사명", null)))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        assertThatThrownBy(() -> service.create(new CreateOrganizationCommand(null, "회사명", null)))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(identities, codes, organizations, memberships);
    }

    @Test
    void regeneratesCodeWhenCandidateAlreadyExists() {
        stubIds();
        when(codes.generate()).thenReturn(FIRST_CODE, SECOND_CODE);
        when(organizations.existsByCode(FIRST_CODE)).thenReturn(true);

        var result = service.create(new CreateOrganizationCommand(9L, "회사명", null));

        assertThat(result.code()).isEqualTo(SECOND_CODE.value());
        verify(codes, times(2)).generate();
    }

    @Test
    void reportsCodeExhaustionAfterFiveCandidatesWithoutSaving() {
        stubIds();
        when(codes.generate()).thenReturn(FIRST_CODE);
        when(organizations.existsByCode(FIRST_CODE)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateOrganizationCommand(9L, "회사명", null)))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.COMPANY_CODE_EXHAUSTED);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        verify(codes, times(5)).generate();
        verify(organizations, times(5)).existsByCode(FIRST_CODE);
        verify(organizations, times(0)).save(any(Organization.class));
        verifyNoInteractions(memberships);
    }

    @Test
    void acceptsAvailableCodeOnFifthAttempt() {
        stubIds();
        when(codes.generate()).thenReturn(FIRST_CODE, FIRST_CODE, FIRST_CODE, FIRST_CODE, SECOND_CODE);
        when(organizations.existsByCode(FIRST_CODE)).thenReturn(true);

        var result = service.create(new CreateOrganizationCommand(9L, "회사명", null));

        assertThat(result.code()).isEqualTo(SECOND_CODE.value());
        verify(codes, times(5)).generate();
        verify(organizations).existsByCode(SECOND_CODE);
    }

    @Test
    void mapsConcurrentCodeConflictToRetryableError() {
        stubIdsAndCode();
        doThrow(new CompanyCodeConflictException(new IllegalStateException("duplicate")))
                .when(organizations)
                .flush();

        assertThatThrownBy(() -> service.create(new CreateOrganizationCommand(9L, "회사명", null)))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.COMPANY_CODE_CONFLICT);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void doesNotMapPortRuleViolationToInputError() {
        stubIds();
        when(codes.generate()).thenThrow(new OrganizationRuleViolation("port failure"));

        assertThatThrownBy(() -> service.create(new CreateOrganizationCommand(9L, "회사명", null)))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    @Test
    void doesNotMapLateMembershipPortRuleViolationToInputError() {
        stubIdsAndCode();
        doThrow(new OrganizationRuleViolation("port failure")).when(memberships).save(any(Membership.class));

        assertThatThrownBy(() -> service.create(new CreateOrganizationCommand(9L, "회사명", null)))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    private void stubIdsAndCode() {
        stubIds();
        when(codes.generate()).thenReturn(FIRST_CODE);
    }

    private void stubIds() {
        when(identities.nextOrganizationId()).thenReturn(new OrganizationId(11L));
        when(identities.nextMembershipId()).thenReturn(new MembershipId(12L));
    }
}
