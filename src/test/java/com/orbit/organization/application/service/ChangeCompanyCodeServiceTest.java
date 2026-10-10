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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.ChangeCompanyCodeCommand;
import com.orbit.organization.application.port.in.query.dto.CompanyCodeInfo;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.organization.domain.RetiredCompanyCode;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("회사 코드 변경")
class ChangeCompanyCodeServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final CompanyCode CURRENT_CODE = new CompanyCode("7K2M9X");
    private static final CompanyCode ISSUED_CODE = new CompanyCode("M3N4P5");
    private static final CompanyCode FREE_CODE = new CompanyCode("Q4ZT8B");

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private CompanyCodeGenerator codeGenerator;

    private ChangeCompanyCodeService service;

    @BeforeEach
    void setUp() {
        service = new ChangeCompanyCodeService(
                organizationRepository, membershipRepository, codeGenerator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("활성 총관리자가 바꾸면 잠근 발주사에 새 코드를 발급하고 이전 코드를 폐기 코드로 남긴다")
    void issuesNewCodeAndRetiresPreviousCode() {
        ownerRequests();
        when(organizationRepository.findByIdForUpdate(ORGANIZATION_ID)).thenReturn(Optional.of(organization()));
        when(codeGenerator.generate()).thenReturn(FREE_CODE);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyCodeInfo result = service.change(new ChangeCompanyCodeCommand(7L, 10L));

        assertThat(result).isEqualTo(new CompanyCodeInfo("Q4ZT8B"));
        verify(organizationRepository).saveRetiredCode(new RetiredCompanyCode(ORGANIZATION_ID, CURRENT_CODE, NOW));
        ArgumentCaptor<Organization> saved = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(saved.capture());
        assertThat(saved.getValue().code()).isEqualTo(FREE_CODE);
        assertThat(saved.getValue().updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("후보가 현재 코드나 폐기 코드로 발급된 적 있으면 새 후보를 만든다")
    void regeneratesCodeWhenCandidateWasIssued() {
        ownerRequests();
        when(organizationRepository.findByIdForUpdate(ORGANIZATION_ID)).thenReturn(Optional.of(organization()));
        when(codeGenerator.generate()).thenReturn(CURRENT_CODE, ISSUED_CODE, FREE_CODE);
        when(organizationRepository.existsIssuedCode(CURRENT_CODE)).thenReturn(true);
        when(organizationRepository.existsIssuedCode(ISSUED_CODE)).thenReturn(true);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyCodeInfo result = service.change(new ChangeCompanyCodeCommand(7L, 10L));

        assertThat(result.companyCode()).isEqualTo("Q4ZT8B");
    }

    @Test
    @DisplayName("정해진 횟수 안에 발급된 적 없는 코드를 얻지 못하면 저장하지 않고 실패한다")
    void failsWithoutSavingWhenNoFreeCodeWithinAttempts() {
        ownerRequests();
        when(organizationRepository.findByIdForUpdate(ORGANIZATION_ID)).thenReturn(Optional.of(organization()));
        when(codeGenerator.generate()).thenReturn(ISSUED_CODE);
        when(organizationRepository.existsIssuedCode(ISSUED_CODE)).thenReturn(true);

        assertThatThrownBy(() -> service.change(new ChangeCompanyCodeCommand(7L, 10L)))
                .isInstanceOf(IllegalStateException.class);
        verify(organizationRepository, never()).save(any());
        verify(organizationRepository, never()).saveRetiredCode(any());
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 회사 코드를 바꿀 수 없다")
    void rejectsStaff() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(membership(false)));

        assertRejected(new ChangeCompanyCodeCommand(7L, 10L), OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(organizationRepository, codeGenerator);
    }

    @Test
    @DisplayName("그 발주사의 활성 소속이 없으면 발주사 존재 여부를 드러내지 않고 거부한다")
    void rejectsRequesterWithoutActiveMembership() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertRejected(new ChangeCompanyCodeCommand(7L, 10L), OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
        verifyNoInteractions(organizationRepository, codeGenerator);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("발주사 식별자 형식이 틀리면 소속을 찾지 않고 거부한다")
    void rejectsMalformedOrganizationId(Long organizationId) {
        assertRejected(new ChangeCompanyCodeCommand(7L, organizationId), CommonErrorCode.BAD_REQUEST);
        verifyNoInteractions(membershipRepository, organizationRepository, codeGenerator);
    }

    private void ownerRequests() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(membership(true)));
    }

    private void assertRejected(ChangeCompanyCodeCommand command, BaseCode errorCode) {
        assertThatThrownBy(() -> service.change(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static Membership membership(boolean owner) {
        return Membership.reconstitute(
                new MembershipId(3L),
                ORGANIZATION_ID,
                ACCOUNT_ID,
                owner,
                MembershipStatus.ACTIVE,
                CREATED_AT,
                CREATED_AT);
    }

    private static Organization organization() {
        return Organization.reconstitute(
                ORGANIZATION_ID,
                new OrganizationName("오르빗 설비"),
                Industry.PLUMBING,
                CURRENT_CODE,
                CREATED_AT,
                CREATED_AT);
    }
}
