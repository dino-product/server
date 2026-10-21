package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ACCOUNT_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.CREATED_AT;
import static com.orbit.organization.application.service.MemberCategoryFixtures.ORGANIZATION_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.OTHER_ORGANIZATION_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.category;
import static com.orbit.organization.application.service.MemberCategoryFixtures.deletedCategory;
import static com.orbit.organization.application.service.MemberCategoryFixtures.requester;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.AssignMemberCategoryCommand;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("참여 인원 세부 유형 지정·해제")
class AssignMemberCategoryServiceTest {

    private static final MembershipId MEMBERSHIP_ID = new MembershipId(20L);
    private static final TechnicianId TECHNICIAN_ID = new TechnicianId(30L);
    private static final MemberCategoryId CATEGORY_ID = new MemberCategoryId(5L);
    private static final AccountId MEMBER_ACCOUNT = new AccountId(8L);

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private TechnicianRepository technicianRepository;

    @Mock
    private MemberCategoryRepository categoryRepository;

    private AssignMemberCategoryService service;

    @BeforeEach
    void setUp() {
        service = new AssignMemberCategoryService(
                organizationRepository, membershipRepository, technicianRepository, categoryRepository);
    }

    @Test
    @DisplayName("직원 소속에는 발주사를 잠근 뒤 그 발주사의 직원 유형을 지정한다")
    void assignsStaffCategoryAfterLocking() {
        givenRequesterIs(true);
        givenStaff(ORGANIZATION_ID, MembershipStatus.ACTIVE);
        when(categoryRepository.findById(MemberCategoryKind.STAFF, CATEGORY_ID))
                .thenReturn(Optional.of(category(5L, MemberCategoryKind.STAFF, "정규직")));

        service.assign(command(MemberCategoryKind.STAFF, MEMBERSHIP_ID.value(), CATEGORY_ID.value()));

        InOrder order = inOrder(organizationRepository, membershipRepository);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(membershipRepository).findById(MEMBERSHIP_ID);
        order.verify(membershipRepository).changeStaffCategory(MEMBERSHIP_ID, CATEGORY_ID);
        verifyNoInteractions(technicianRepository);
    }

    @Test
    @DisplayName("기사 계약에는 그 발주사의 기사 유형을 지정하고 직원 소속은 건드리지 않는다")
    void assignsTechnicianCategory() {
        givenRequesterIs(true);
        givenTechnician(ORGANIZATION_ID, TechnicianStatus.ACTIVE);
        when(categoryRepository.findById(MemberCategoryKind.TECHNICIAN, CATEGORY_ID))
                .thenReturn(Optional.of(category(5L, MemberCategoryKind.TECHNICIAN, "전기 기사")));

        service.assign(command(MemberCategoryKind.TECHNICIAN, TECHNICIAN_ID.value(), CATEGORY_ID.value()));

        verify(technicianRepository).changeTechnicianCategory(TECHNICIAN_ID, CATEGORY_ID);
        verify(membershipRepository, never()).changeStaffCategory(any(), any());
    }

    @Test
    @DisplayName("유형을 비우면 유형을 찾지 않고 \"유형 없음\"으로 바꾼다")
    void unassignsWithoutCategory() {
        givenRequesterIs(true);
        givenTechnician(ORGANIZATION_ID, TechnicianStatus.ACTIVE);

        service.assign(command(MemberCategoryKind.TECHNICIAN, TECHNICIAN_ID.value(), null));

        verify(technicianRepository).changeTechnicianCategory(TECHNICIAN_ID, null);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("삭제된 유형과 다른 발주사의 유형은 없는 유형과 같이 거부한다")
    void rejectsDeletedOrForeignCategory() {
        givenRequesterIs(true);
        givenStaff(ORGANIZATION_ID, MembershipStatus.ACTIVE);
        when(categoryRepository.findById(MemberCategoryKind.STAFF, CATEGORY_ID))
                .thenReturn(Optional.of(deletedCategory(5L, MemberCategoryKind.STAFF, "정규직")))
                .thenReturn(Optional.of(category(5L, OTHER_ORGANIZATION_ID, MemberCategoryKind.STAFF, "정규직")))
                .thenReturn(Optional.empty());

        for (int attempt = 0; attempt < 3; attempt++) {
            assertRejected(
                    command(MemberCategoryKind.STAFF, MEMBERSHIP_ID.value(), CATEGORY_ID.value()),
                    OrganizationErrorCode.CATEGORY_NOT_FOUND);
        }
        verify(membershipRepository, never()).changeStaffCategory(any(), any());
    }

    @Test
    @DisplayName("비활성 소속·계약의 세부 유형은 바꿀 수 없다")
    void rejectsInactiveMember() {
        givenRequesterIs(true);
        givenStaff(ORGANIZATION_ID, MembershipStatus.DEACTIVATED);
        givenTechnician(ORGANIZATION_ID, TechnicianStatus.DEACTIVATED);

        assertRejected(
                command(MemberCategoryKind.STAFF, MEMBERSHIP_ID.value(), null), OrganizationErrorCode.INACTIVE_MEMBER);
        assertRejected(
                command(MemberCategoryKind.TECHNICIAN, TECHNICIAN_ID.value(), null),
                OrganizationErrorCode.INACTIVE_MEMBER);
        verify(membershipRepository, never()).changeStaffCategory(any(), any());
        verify(technicianRepository, never()).changeTechnicianCategory(any(), any());
    }

    @Test
    @DisplayName("없거나 다른 발주사의 직원 소속·기사 계약은 찾을 수 없다고 거부한다")
    void rejectsMissingOrForeignMember() {
        givenRequesterIs(true);
        givenStaff(OTHER_ORGANIZATION_ID, MembershipStatus.ACTIVE);
        when(technicianRepository.findById(TECHNICIAN_ID)).thenReturn(Optional.empty());

        assertRejected(
                command(MemberCategoryKind.STAFF, MEMBERSHIP_ID.value(), null),
                OrganizationErrorCode.MEMBERSHIP_NOT_FOUND);
        assertRejected(
                command(MemberCategoryKind.TECHNICIAN, TECHNICIAN_ID.value(), null),
                OrganizationErrorCode.TECHNICIAN_NOT_FOUND);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 잠근 뒤 거부되고, 소속이 없으면 잠그기 전에 거부된다")
    void rejectsNonOwners() {
        givenRequesterIs(false);
        assertRejected(
                command(MemberCategoryKind.STAFF, MEMBERSHIP_ID.value(), null), OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(categoryRepository, technicianRepository);

        when(membershipRepository.existsActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(false);
        assertRejected(
                command(MemberCategoryKind.STAFF, MEMBERSHIP_ID.value(), null),
                OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
        verify(organizationRepository).lock(ORGANIZATION_ID);
    }

    @Test
    @DisplayName("0 이하의 대상·유형 식별자는 권한 확인 전에 입력 오류다")
    void rejectsNonPositiveIds() {
        assertRejected(command(MemberCategoryKind.STAFF, 0L, null), CommonErrorCode.BAD_REQUEST);
        assertRejected(command(MemberCategoryKind.STAFF, MEMBERSHIP_ID.value(), 0L), CommonErrorCode.BAD_REQUEST);
        verifyNoInteractions(organizationRepository, membershipRepository);
    }

    private void givenRequesterIs(boolean owner) {
        when(membershipRepository.existsActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(true);
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(requester(owner)));
    }

    private void givenStaff(OrganizationId organizationId, MembershipStatus status) {
        when(membershipRepository.findById(MEMBERSHIP_ID))
                .thenReturn(Optional.of(Membership.reconstitute(
                        MEMBERSHIP_ID, organizationId, MEMBER_ACCOUNT, false, status, CREATED_AT, CREATED_AT)));
    }

    private void givenTechnician(OrganizationId organizationId, TechnicianStatus status) {
        when(technicianRepository.findById(TECHNICIAN_ID))
                .thenReturn(Optional.of(Technician.reconstitute(
                        TECHNICIAN_ID, organizationId, MEMBER_ACCOUNT, status, CREATED_AT, CREATED_AT)));
    }

    private void assertRejected(AssignMemberCategoryCommand command, BaseCode errorCode) {
        assertThatThrownBy(() -> service.assign(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static AssignMemberCategoryCommand command(MemberCategoryKind kind, Long memberId, Long categoryId) {
        return new AssignMemberCategoryCommand(ACCOUNT_ID.value(), ORGANIZATION_ID.value(), kind, memberId, categoryId);
    }
}
