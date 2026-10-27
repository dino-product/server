package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ACCOUNT_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.ORGANIZATION_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.OTHER_ORGANIZATION_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.deletedWorkType;
import static com.orbit.organization.application.service.MemberCategoryFixtures.requester;
import static com.orbit.organization.application.service.MemberCategoryFixtures.workType;
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
import com.orbit.organization.application.port.in.command.dto.UpdateWorkTypeCommand;
import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;
import com.orbit.organization.application.port.out.DuplicateCategoryNameException;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.WorkType;
import com.orbit.organization.domain.WorkTypeId;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("작업 유형 수정")
class UpdateWorkTypeServiceTest {

    private static final WorkTypeId WORK_TYPE_ID = new WorkTypeId(5L);

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private WorkTypeRepository workTypeRepository;

    private UpdateWorkTypeService service;

    @BeforeEach
    void setUp() {
        service = new UpdateWorkTypeService(organizationRepository, membershipRepository, workTypeRepository);
    }

    @Test
    @DisplayName("총관리자가 이름·색상을 바꾸면 발주사를 잠근 뒤 저장하고 바뀐 작업 유형을 돌려준다")
    void ownerChangesNameAndColor() {
        givenRequesterIs(true);
        givenWorkType(workType(5L, "설치"));
        when(workTypeRepository.findUndeletedByName(ORGANIZATION_ID, new CategoryName("수리")))
                .thenReturn(Optional.empty());
        when(workTypeRepository.save(any(WorkType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkTypeInfo result = service.update(command(5L, " 수리 ", "#ef4444"));

        assertThat(result).isEqualTo(new WorkTypeInfo(5L, "수리", "#EF4444"));
        InOrder order = inOrder(organizationRepository, workTypeRepository);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(workTypeRepository).findById(WORK_TYPE_ID);
        order.verify(workTypeRepository).save(any());
    }

    @Test
    @DisplayName("자기 이름을 그대로 두고 색상만 바꿀 수 있다")
    void keepsOwnName() {
        givenRequesterIs(true);
        WorkType current = workType(5L, "설치");
        givenWorkType(current);
        when(workTypeRepository.findUndeletedByName(ORGANIZATION_ID, new CategoryName("설치")))
                .thenReturn(Optional.of(current));
        when(workTypeRepository.save(any(WorkType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkTypeInfo result = service.update(command(5L, "설치", "#EF4444"));

        assertThat(result.color()).isEqualTo("#EF4444");
    }

    @Test
    @DisplayName("삭제되지 않은 다른 작업 유형과 이름이 같으면 거부한다")
    void rejectsDuplicateNameOfOtherWorkType() {
        givenRequesterIs(true);
        givenWorkType(workType(5L, "설치"));
        when(workTypeRepository.findUndeletedByName(ORGANIZATION_ID, new CategoryName("수리")))
                .thenReturn(Optional.of(workType(6L, "수리")));

        assertRejected(command(5L, "수리", "#3B82F6"), OrganizationErrorCode.DUPLICATE_CATEGORY_NAME);
        verify(workTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("저장소가 이름 유일 인덱스로 거부해도 같은 중복 오류로 알린다")
    void reportsDuplicateRejectedByRepository() {
        givenRequesterIs(true);
        givenWorkType(workType(5L, "설치"));
        when(workTypeRepository.findUndeletedByName(any(), any())).thenReturn(Optional.empty());
        when(workTypeRepository.save(any(WorkType.class)))
                .thenThrow(new DuplicateCategoryNameException(new RuntimeException("unique violation")));

        assertRejected(command(5L, "수리", "#3B82F6"), OrganizationErrorCode.DUPLICATE_CATEGORY_NAME);
    }

    @Test
    @DisplayName("삭제된 작업 유형은 없는 유형과 같이 거부한다")
    void rejectsDeletedWorkType() {
        givenRequesterIs(true);
        givenWorkType(deletedWorkType(5L, "설치"));

        assertRejected(command(5L, "수리", "#3B82F6"), OrganizationErrorCode.CATEGORY_NOT_FOUND);
        verify(workTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 발주사의 작업 유형은 없는 유형과 같이 거부한다")
    void rejectsWorkTypeOfOtherOrganization() {
        givenRequesterIs(true);
        givenWorkType(workType(5L, OTHER_ORGANIZATION_ID, "설치"));

        assertRejected(command(5L, "수리", "#3B82F6"), OrganizationErrorCode.CATEGORY_NOT_FOUND);
        verify(workTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 작업 유형은 거부한다")
    void rejectsMissingWorkType() {
        givenRequesterIs(true);
        when(workTypeRepository.findById(WORK_TYPE_ID)).thenReturn(Optional.empty());

        assertRejected(command(5L, "수리", "#3B82F6"), OrganizationErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("입력이 틀리면 작업 유형을 찾기 전에 거부한다")
    void rejectsInvalidInputBeforeLookup() {
        givenRequesterIs(true);

        assertRejected(command(5L, "가", "#3B82F6"), OrganizationErrorCode.INVALID_CATEGORY_INPUT);
        assertRejected(command(5L, "수리", "#000000"), OrganizationErrorCode.INVALID_CATEGORY_INPUT);
        verifyNoInteractions(workTypeRepository);
    }

    @Test
    @DisplayName("작업 유형 식별자 형식이 틀리면 소속을 찾지 않고 거부한다")
    void rejectsMalformedWorkTypeId() {
        assertRejected(command(0L, "수리", "#3B82F6"), CommonErrorCode.BAD_REQUEST);
        verifyNoInteractions(membershipRepository, workTypeRepository);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 거부한다")
    void rejectsStaff() {
        givenRequesterIs(false);

        assertRejected(command(5L, "수리", "#3B82F6"), OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(organizationRepository, workTypeRepository);
    }

    private void givenRequesterIs(boolean owner) {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(requester(owner)));
    }

    private void givenWorkType(WorkType workType) {
        when(workTypeRepository.findById(WORK_TYPE_ID)).thenReturn(Optional.of(workType));
    }

    private void assertRejected(UpdateWorkTypeCommand command, BaseCode errorCode) {
        assertThatThrownBy(() -> service.update(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static UpdateWorkTypeCommand command(long workTypeId, String name, String color) {
        return new UpdateWorkTypeCommand(7L, 10L, workTypeId, name, color);
    }
}
