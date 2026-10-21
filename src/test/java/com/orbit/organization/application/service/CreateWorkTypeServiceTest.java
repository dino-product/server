package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ACCOUNT_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.ORGANIZATION_ID;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.CreateWorkTypeCommand;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("작업 유형 생성")
class CreateWorkTypeServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private WorkTypeRepository workTypeRepository;

    private CreateWorkTypeService service;

    @BeforeEach
    void setUp() {
        service = new CreateWorkTypeService(organizationRepository, membershipRepository, workTypeRepository);
    }

    @Test
    @DisplayName("총관리자가 만들면 발주사를 잠근 뒤 공백을 지운 이름과 대문자 색상으로 저장한다")
    void ownerCreatesWorkType() {
        givenRequesterIs(true);
        when(workTypeRepository.findUndeletedByName(ORGANIZATION_ID, new CategoryName("설치")))
                .thenReturn(Optional.empty());
        when(workTypeRepository.save(any(WorkType.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        WorkTypeInfo result = service.create(new CreateWorkTypeCommand(7L, 10L, "  설치 ", "#ef4444"));

        assertThat(result).isEqualTo(new WorkTypeInfo(20L, "설치", "#EF4444"));
        ArgumentCaptor<WorkType> saved = ArgumentCaptor.forClass(WorkType.class);
        InOrder order = inOrder(organizationRepository, workTypeRepository);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(workTypeRepository).findUndeletedByName(any(), any());
        order.verify(workTypeRepository).save(saved.capture());
        assertThat(saved.getValue().organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(saved.getValue().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("같은 발주사에 삭제되지 않은 같은 이름의 작업 유형이 있으면 거부한다")
    void rejectsDuplicateName() {
        givenRequesterIs(true);
        when(workTypeRepository.findUndeletedByName(ORGANIZATION_ID, new CategoryName("설치")))
                .thenReturn(Optional.of(workType(5L, "설치")));

        assertRejected(
                new CreateWorkTypeCommand(7L, 10L, " 설치", "#3B82F6"), OrganizationErrorCode.DUPLICATE_CATEGORY_NAME);
        verify(workTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("저장소가 이름 유일 인덱스로 거부해도 같은 중복 오류로 알린다")
    void reportsDuplicateRejectedByRepository() {
        givenRequesterIs(true);
        when(workTypeRepository.findUndeletedByName(any(), any())).thenReturn(Optional.empty());
        when(workTypeRepository.save(any(WorkType.class)))
                .thenThrow(new DuplicateCategoryNameException(new RuntimeException("unique violation")));

        assertRejected(
                new CreateWorkTypeCommand(7L, 10L, "설치", "#3B82F6"), OrganizationErrorCode.DUPLICATE_CATEGORY_NAME);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "가", "가나다라마바사아자차카", "설치😀"})
    @DisplayName("작업 유형 이름이 규칙에 맞지 않으면 거부한다")
    void rejectsInvalidName(String name) {
        givenRequesterIs(true);

        assertRejected(
                new CreateWorkTypeCommand(7L, 10L, name, "#3B82F6"), OrganizationErrorCode.INVALID_CATEGORY_INPUT);
        verifyNoInteractions(workTypeRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"#000000", "blue"})
    @DisplayName("프리셋에 없는 색상은 거부한다")
    void rejectsColorOutsidePalette(String color) {
        givenRequesterIs(true);

        assertRejected(new CreateWorkTypeCommand(7L, 10L, "설치", color), OrganizationErrorCode.INVALID_CATEGORY_INPUT);
        verifyNoInteractions(workTypeRepository);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 입력이 틀려도 권한 오류로 거부한다")
    void rejectsStaffBeforeCheckingInput() {
        givenRequesterIs(false);

        assertRejected(new CreateWorkTypeCommand(7L, 10L, "가", null), OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(organizationRepository, workTypeRepository);
    }

    @Test
    @DisplayName("그 발주사의 활성 소속이 없으면 거부한다")
    void rejectsRequesterWithoutActiveMembership() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertRejected(
                new CreateWorkTypeCommand(7L, 10L, "설치", "#3B82F6"), OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
        verifyNoInteractions(organizationRepository, workTypeRepository);
    }

    private void givenRequesterIs(boolean owner) {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(requester(owner)));
    }

    private void assertRejected(CreateWorkTypeCommand command, BaseCode errorCode) {
        assertThatThrownBy(() -> service.create(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static WorkType saved(WorkType workType) {
        return WorkType.reconstitute(
                new WorkTypeId(20L), workType.organizationId(), workType.name(), workType.color(), false, null);
    }
}
