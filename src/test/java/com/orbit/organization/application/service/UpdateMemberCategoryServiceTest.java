package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ACCOUNT_ID;
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
import com.orbit.organization.application.port.in.command.dto.UpdateMemberCategoryCommand;
import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;
import com.orbit.organization.application.port.out.DuplicateCategoryNameException;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("직원·기사 유형 수정")
class UpdateMemberCategoryServiceTest {

    private static final MemberCategoryId CATEGORY_ID = new MemberCategoryId(5L);

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private MemberCategoryRepository categoryRepository;

    private UpdateMemberCategoryService service;

    @BeforeEach
    void setUp() {
        service = new UpdateMemberCategoryService(organizationRepository, membershipRepository, categoryRepository);
    }

    @Test
    @DisplayName("총관리자가 이름·색상을 바꾸면 발주사를 잠근 뒤 저장하고 바뀐 유형을 돌려준다")
    void ownerChangesNameAndColor() {
        givenRequesterIs(true);
        givenCategory(category(5L, MemberCategoryKind.STAFF, "정규직"));
        when(categoryRepository.findUndeletedByName(ORGANIZATION_ID, MemberCategoryKind.STAFF, new CategoryName("계약직")))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(MemberCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemberCategoryInfo result = service.update(command(5L, " 계약직 ", "#ef4444"));

        assertThat(result).isEqualTo(new MemberCategoryInfo(5L, MemberCategoryKind.STAFF, "계약직", "#EF4444"));
        InOrder order = inOrder(organizationRepository, categoryRepository);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(categoryRepository).findById(MemberCategoryKind.STAFF, CATEGORY_ID);
        order.verify(categoryRepository).save(any());
    }

    @Test
    @DisplayName("자기 이름을 그대로 두고 색상만 바꿀 수 있다")
    void keepsOwnName() {
        givenRequesterIs(true);
        MemberCategory current = category(5L, MemberCategoryKind.STAFF, "정규직");
        givenCategory(current);
        when(categoryRepository.findUndeletedByName(ORGANIZATION_ID, MemberCategoryKind.STAFF, new CategoryName("정규직")))
                .thenReturn(Optional.of(current));
        when(categoryRepository.save(any(MemberCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemberCategoryInfo result = service.update(command(5L, "정규직", "#EF4444"));

        assertThat(result.color()).isEqualTo("#EF4444");
    }

    @Test
    @DisplayName("같은 종류의 삭제되지 않은 다른 유형과 이름이 같으면 거부한다")
    void rejectsDuplicateNameOfOtherCategory() {
        givenRequesterIs(true);
        givenCategory(category(5L, MemberCategoryKind.STAFF, "정규직"));
        when(categoryRepository.findUndeletedByName(ORGANIZATION_ID, MemberCategoryKind.STAFF, new CategoryName("계약직")))
                .thenReturn(Optional.of(category(6L, MemberCategoryKind.STAFF, "계약직")));

        assertRejected(command(5L, "계약직", "#3B82F6"), OrganizationErrorCode.DUPLICATE_CATEGORY_NAME);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("저장소가 이름 유일 인덱스로 거부해도 같은 중복 오류로 알린다")
    void reportsDuplicateRejectedByRepository() {
        givenRequesterIs(true);
        givenCategory(category(5L, MemberCategoryKind.STAFF, "정규직"));
        when(categoryRepository.findUndeletedByName(any(), any(), any())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(MemberCategory.class)))
                .thenThrow(new DuplicateCategoryNameException(new RuntimeException("unique violation")));

        assertRejected(command(5L, "계약직", "#3B82F6"), OrganizationErrorCode.DUPLICATE_CATEGORY_NAME);
    }

    @Test
    @DisplayName("삭제된 유형은 없는 유형과 같이 거부한다")
    void rejectsDeletedCategory() {
        givenRequesterIs(true);
        givenCategory(deletedCategory(5L, MemberCategoryKind.STAFF, "정규직"));

        assertRejected(command(5L, "계약직", "#3B82F6"), OrganizationErrorCode.CATEGORY_NOT_FOUND);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 발주사의 유형은 없는 유형과 같이 거부한다")
    void rejectsCategoryOfOtherOrganization() {
        givenRequesterIs(true);
        givenCategory(category(5L, OTHER_ORGANIZATION_ID, MemberCategoryKind.STAFF, "정규직"));

        assertRejected(command(5L, "계약직", "#3B82F6"), OrganizationErrorCode.CATEGORY_NOT_FOUND);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 유형은 거부한다")
    void rejectsMissingCategory() {
        givenRequesterIs(true);
        when(categoryRepository.findById(MemberCategoryKind.STAFF, CATEGORY_ID)).thenReturn(Optional.empty());

        assertRejected(command(5L, "계약직", "#3B82F6"), OrganizationErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("입력이 틀리면 유형을 찾기 전에 거부한다")
    void rejectsInvalidInputBeforeLookup() {
        givenRequesterIs(true);

        assertRejected(command(5L, "가", "#3B82F6"), OrganizationErrorCode.INVALID_CATEGORY_INPUT);
        assertRejected(command(5L, "계약직", "#000000"), OrganizationErrorCode.INVALID_CATEGORY_INPUT);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("유형 식별자 형식이 틀리면 소속을 찾지 않고 거부한다")
    void rejectsMalformedCategoryId() {
        assertRejected(command(0L, "계약직", "#3B82F6"), CommonErrorCode.BAD_REQUEST);
        verifyNoInteractions(membershipRepository, categoryRepository);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 거부한다")
    void rejectsStaff() {
        givenRequesterIs(false);

        assertRejected(command(5L, "계약직", "#3B82F6"), OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(organizationRepository, categoryRepository);
    }

    private void givenRequesterIs(boolean owner) {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(requester(owner)));
    }

    private void givenCategory(MemberCategory category) {
        when(categoryRepository.findById(MemberCategoryKind.STAFF, CATEGORY_ID)).thenReturn(Optional.of(category));
    }

    private void assertRejected(UpdateMemberCategoryCommand command, BaseCode errorCode) {
        assertThatThrownBy(() -> service.update(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static UpdateMemberCategoryCommand command(long categoryId, String name, String color) {
        return new UpdateMemberCategoryCommand(7L, 10L, MemberCategoryKind.STAFF, categoryId, name, color);
    }
}
