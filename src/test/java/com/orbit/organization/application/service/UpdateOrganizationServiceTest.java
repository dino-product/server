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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.UpdateOrganizationCommand;
import com.orbit.organization.application.port.in.query.dto.OrganizationInfo;
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
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("발주사 정보 수정")
class UpdateOrganizationServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final CompanyCode CODE = new CompanyCode("7K2M9X");

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private UpdateOrganizationService service;

    @BeforeEach
    void setUp() {
        service = new UpdateOrganizationService(
                organizationRepository, membershipRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("총관리자가 발주사명·업종을 바꾸면 저장하고 바뀐 정보를 돌려준다")
    void ownerChangesNameAndIndustry() {
        givenRequesterIs(true);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization()));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationInfo result =
                service.update(new UpdateOrganizationCommand(7L, 10L, "  새 발주사  ", Industry.FACILITY_MANAGEMENT));

        assertThat(result).isEqualTo(new OrganizationInfo(10L, "새 발주사", Industry.FACILITY_MANAGEMENT));
        ArgumentCaptor<Organization> saved = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(saved.capture());
        assertThat(saved.getValue().name().value()).isEqualTo("새 발주사");
        assertThat(saved.getValue().industry()).isEqualTo(Industry.FACILITY_MANAGEMENT);
        assertThat(saved.getValue().updatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().code()).isEqualTo(CODE);
    }

    @Test
    @DisplayName("현재 값과 같은 값으로 저장해도 성공한다")
    void acceptsUnchangedValues() {
        givenRequesterIs(true);
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization()));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationInfo result = service.update(new UpdateOrganizationCommand(7L, 10L, "오르빗 설비", Industry.PLUMBING));

        assertThat(result).isEqualTo(new OrganizationInfo(10L, "오르빗 설비", Industry.PLUMBING));
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 입력이 틀려도 권한 오류로 거부한다")
    void rejectsStaffBeforeCheckingInput() {
        givenRequesterIs(false);

        assertRejected(new UpdateOrganizationCommand(7L, 10L, "가", null), OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(organizationRepository);
    }

    @Test
    @DisplayName("그 발주사의 활성 소속이 없으면 거부한다")
    void rejectsRequesterWithoutActiveMembership() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertRejected(
                new UpdateOrganizationCommand(7L, 10L, "새 발주사", Industry.HVAC),
                OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
        verifyNoInteractions(organizationRepository);
    }

    @Test
    @DisplayName("발주사 식별자 형식이 틀리면 소속을 찾지 않고 거부한다")
    void rejectsMalformedOrganizationId() {
        assertRejected(new UpdateOrganizationCommand(7L, 0L, "새 발주사", Industry.HVAC), CommonErrorCode.BAD_REQUEST);
        verifyNoInteractions(membershipRepository, organizationRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"가", "   ", " 가 ", "오르빗😀"})
    @DisplayName("발주사명이 생성 때와 같은 규칙에 맞지 않으면 저장하지 않고 거부한다")
    void rejectsInvalidName(String name) {
        givenRequesterIs(true);

        assertRejected(
                new UpdateOrganizationCommand(7L, 10L, name, Industry.HVAC),
                OrganizationErrorCode.INVALID_ORGANIZATION_INPUT);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("업종이 없으면 저장하지 않고 거부한다")
    void rejectsMissingIndustry() {
        givenRequesterIs(true);

        assertRejected(
                new UpdateOrganizationCommand(7L, 10L, "새 발주사", null),
                OrganizationErrorCode.INVALID_ORGANIZATION_INPUT);
        verify(organizationRepository, never()).save(any());
    }

    private void givenRequesterIs(boolean owner) {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(Membership.reconstitute(
                        new MembershipId(3L),
                        ORGANIZATION_ID,
                        ACCOUNT_ID,
                        owner,
                        MembershipStatus.ACTIVE,
                        CREATED_AT,
                        CREATED_AT)));
    }

    private void assertRejected(UpdateOrganizationCommand command, BaseCode errorCode) {
        assertThatThrownBy(() -> service.update(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static Organization organization() {
        return Organization.reconstitute(
                ORGANIZATION_ID, new OrganizationName("오르빗 설비"), Industry.PLUMBING, CODE, CREATED_AT, CREATED_AT);
    }
}
