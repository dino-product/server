package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("발주사 생성")
class CreateOrganizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
    private static final CompanyCode TAKEN_CODE = new CompanyCode("7K2M9X");
    private static final CompanyCode FREE_CODE = new CompanyCode("Q4ZT8B");

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private CompanyCodeGenerator codeGenerator;

    private CreateOrganizationService service;

    @BeforeEach
    void setUp() {
        service = new CreateOrganizationService(
                organizationRepository, membershipRepository, codeGenerator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("발주사와 생성자의 총관리자 직원 소속을 만들고 발주사 ID·회사 코드를 돌려준다")
    void createsOrganizationWithFounderAsOwner() {
        when(codeGenerator.generate()).thenReturn(FREE_CODE);
        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        CreatedOrganizationInfo result = service.create(new CreateOrganizationCommand(7L, "오르빗 설비", Industry.HVAC));

        assertThat(result).isEqualTo(new CreatedOrganizationInfo(10L, "Q4ZT8B"));
        ArgumentCaptor<Organization> organization = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(organization.capture());
        assertThat(organization.getValue().name().value()).isEqualTo("오르빗 설비");
        assertThat(organization.getValue().industry()).isEqualTo(Industry.HVAC);
        assertThat(organization.getValue().createdAt()).isEqualTo(NOW);

        ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(membership.capture());
        assertThat(membership.getValue().organizationId()).isEqualTo(new OrganizationId(10L));
        assertThat(membership.getValue().accountId()).isEqualTo(new AccountId(7L));
        assertThat(membership.getValue().isOwner()).isTrue();
        assertThat(membership.getValue().status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.getValue().joinedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("후보 코드가 현재 코드나 폐기 코드로 발급된 적 있으면 새 후보를 만든다")
    void regeneratesCodeWhenCandidateIsTaken() {
        when(codeGenerator.generate()).thenReturn(TAKEN_CODE, FREE_CODE);
        when(organizationRepository.existsIssuedCode(TAKEN_CODE)).thenReturn(true);
        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        CreatedOrganizationInfo result = service.create(new CreateOrganizationCommand(7L, "오르빗 설비", Industry.HVAC));

        assertThat(result.companyCode()).isEqualTo("Q4ZT8B");
    }

    @Test
    @DisplayName("정해진 횟수 안에 겹치지 않는 코드를 얻지 못하면 저장하지 않고 실패한다")
    void failsWithoutSavingWhenNoFreeCodeWithinAttempts() {
        when(codeGenerator.generate()).thenReturn(TAKEN_CODE);
        when(organizationRepository.existsIssuedCode(TAKEN_CODE)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateOrganizationCommand(7L, "오르빗 설비", Industry.HVAC)))
                .isInstanceOf(IllegalStateException.class);
        verify(organizationRepository, never()).save(any());
        verifyNoInteractions(membershipRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"가", "   "})
    @DisplayName("발주사명이 형식에 맞지 않으면 코드를 발급하지 않고 거부한다")
    void rejectsInvalidNameBeforeIssuingCode(String name) {
        assertInvalidInput(new CreateOrganizationCommand(7L, name, Industry.HVAC));
    }

    @Test
    @DisplayName("업종이 없으면 코드를 발급하지 않고 거부한다")
    void rejectsMissingIndustryBeforeIssuingCode() {
        assertInvalidInput(new CreateOrganizationCommand(7L, "오르빗 설비", null));
    }

    private void assertInvalidInput(CreateOrganizationCommand command) {
        assertThatThrownBy(() -> service.create(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT));
        verifyNoInteractions(codeGenerator, organizationRepository, membershipRepository);
    }

    private static Organization persisted(Organization organization) {
        return Organization.reconstitute(
                new OrganizationId(10L),
                organization.name(),
                organization.industry(),
                organization.code(),
                organization.createdAt(),
                organization.updatedAt());
    }
}
