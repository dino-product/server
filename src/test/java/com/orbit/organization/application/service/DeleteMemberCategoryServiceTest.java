package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ACCOUNT_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.CREATED_AT;
import static com.orbit.organization.application.service.MemberCategoryFixtures.NOW;
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

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.DeleteMemberCategoryCommand;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("직원·기사 유형 삭제")
class DeleteMemberCategoryServiceTest {

    private static final MemberCategoryId CATEGORY_ID = new MemberCategoryId(5L);

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private MemberCategoryRepository categoryRepository;

    @Mock
    private TechnicianRepository technicianRepository;

    private DeleteMemberCategoryService service;

    @BeforeEach
    void setUp() {
        service = new DeleteMemberCategoryService(
                organizationRepository,
                membershipRepository,
                categoryRepository,
                technicianRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("직원 유형을 삭제하면 소프트 삭제하고 그 유형이 지정된 직원 소속을 유형 없음으로 바꾼다")
    void softDeletesStaffCategoryAndUnassignsMemberships() {
        givenRequesterIs(true);
        givenCategory(MemberCategoryKind.STAFF, category(5L, MemberCategoryKind.STAFF, "정규직"));

        service.delete(command(MemberCategoryKind.STAFF));

        ArgumentCaptor<MemberCategory> saved = ArgumentCaptor.forClass(MemberCategory.class);
        InOrder order = inOrder(organizationRepository, categoryRepository, membershipRepository);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(categoryRepository).save(saved.capture());
        order.verify(membershipRepository).unassignStaffCategory(ORGANIZATION_ID, CATEGORY_ID, NOW);
        verify(technicianRepository, never()).unassignTechnicianCategory(any(), any(), any());
        assertThat(saved.getValue().isDeleted()).isTrue();
        assertThat(saved.getValue().deletedAt()).contains(NOW);
    }

    @Test
    @DisplayName("기사 유형을 삭제하면 그 유형이 지정된 기사 계약을 유형 없음으로 바꾸고 직원 소속은 건드리지 않는다")
    void softDeletesTechnicianCategoryAndUnassignsTechnicians() {
        givenRequesterIs(true);
        givenCategory(MemberCategoryKind.TECHNICIAN, category(5L, MemberCategoryKind.TECHNICIAN, "전기 기사"));

        service.delete(command(MemberCategoryKind.TECHNICIAN));

        InOrder order = inOrder(categoryRepository, technicianRepository);
        order.verify(categoryRepository).save(any());
        order.verify(technicianRepository).unassignTechnicianCategory(ORGANIZATION_ID, CATEGORY_ID, NOW);
        verify(membershipRepository, never()).unassignStaffCategory(any(), any(), any());
    }

    @Test
    @DisplayName("이미 삭제된 유형을 다시 삭제하면 아무것도 바꾸지 않고 성공한다")
    void deletingAgainSucceedsWithoutChanges() {
        givenRequesterIs(true);
        MemberCategory deleted = deletedCategory(5L, MemberCategoryKind.STAFF, "정규직");
        givenCategory(MemberCategoryKind.STAFF, deleted);

        service.delete(command(MemberCategoryKind.STAFF));

        assertThat(deleted.deletedAt()).contains(CREATED_AT);
        verify(categoryRepository, never()).save(any());
        verify(membershipRepository, never()).unassignStaffCategory(any(), any(), any());
        verify(technicianRepository, never()).unassignTechnicianCategory(any(), any(), any());
    }

    @Test
    @DisplayName("다른 발주사의 유형은 없는 유형과 같이 거부한다")
    void rejectsCategoryOfOtherOrganization() {
        givenRequesterIs(true);
        givenCategory(MemberCategoryKind.STAFF, category(5L, OTHER_ORGANIZATION_ID, MemberCategoryKind.STAFF, "정규직"));

        assertRejected(command(MemberCategoryKind.STAFF), OrganizationErrorCode.CATEGORY_NOT_FOUND);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 유형은 거부한다")
    void rejectsMissingCategory() {
        givenRequesterIs(true);
        when(categoryRepository.findById(MemberCategoryKind.STAFF, CATEGORY_ID)).thenReturn(Optional.empty());

        assertRejected(command(MemberCategoryKind.STAFF), OrganizationErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 거부한다")
    void rejectsStaff() {
        givenRequesterIs(false);

        assertRejected(command(MemberCategoryKind.STAFF), OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(organizationRepository, categoryRepository);
    }

    private void givenRequesterIs(boolean owner) {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(requester(owner)));
    }

    private void givenCategory(MemberCategoryKind kind, MemberCategory category) {
        when(categoryRepository.findById(kind, CATEGORY_ID)).thenReturn(Optional.of(category));
    }

    private void assertRejected(DeleteMemberCategoryCommand command, BaseCode errorCode) {
        assertThatThrownBy(() -> service.delete(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static DeleteMemberCategoryCommand command(MemberCategoryKind kind) {
        return new DeleteMemberCategoryCommand(7L, 10L, kind, 5L);
    }
}
