package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.query.dto.GetOrganizationQuery;
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
@DisplayName("발주사 정보 조회")
class GetOrganizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private GetOrganizationService service;

    @BeforeEach
    void setUp() {
        service = new GetOrganizationService(organizationRepository, membershipRepository);
    }

    @Test
    @DisplayName("활성 총관리자에게 발주사 ID·발주사명·업종을 돌려준다")
    void returnsOrganizationToOwner() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(membership(true)));
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization()));

        OrganizationInfo result = service.get(new GetOrganizationQuery(7L, 10L));

        assertThat(result).isEqualTo(new OrganizationInfo(10L, "오르빗 설비", Industry.PLUMBING));
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 발주사 정보를 볼 수 없다")
    void rejectsStaff() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(membership(false)));

        assertRejected(new GetOrganizationQuery(7L, 10L), OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(organizationRepository);
    }

    @Test
    @DisplayName("그 발주사의 활성 소속이 없으면 발주사 존재 여부를 드러내지 않고 거부한다")
    void rejectsRequesterWithoutActiveMembership() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertRejected(new GetOrganizationQuery(7L, 10L), OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
        verifyNoInteractions(organizationRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("발주사 식별자 형식이 틀리면 소속을 찾지 않고 거부한다")
    void rejectsMalformedOrganizationId(Long organizationId) {
        assertRejected(new GetOrganizationQuery(7L, organizationId), CommonErrorCode.BAD_REQUEST);
        verifyNoInteractions(membershipRepository, organizationRepository);
    }

    @Test
    @DisplayName("요청자 계정이 없으면 인증 계층의 프로그래밍 오류다")
    void failsWithoutRequesterAccount() {
        assertThatThrownBy(() -> service.get(new GetOrganizationQuery(null, 10L)))
                .isInstanceOf(NullPointerException.class);
    }

    private void assertRejected(GetOrganizationQuery query, BaseCode errorCode) {
        assertThatThrownBy(() -> service.get(query))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static Membership membership(boolean owner) {
        return Membership.reconstitute(
                new MembershipId(3L), ORGANIZATION_ID, ACCOUNT_ID, owner, MembershipStatus.ACTIVE, NOW, NOW);
    }

    private static Organization organization() {
        return Organization.reconstitute(
                ORGANIZATION_ID,
                new OrganizationName("오르빗 설비"),
                Industry.PLUMBING,
                new CompanyCode("7K2M9X"),
                NOW,
                NOW);
    }
}
