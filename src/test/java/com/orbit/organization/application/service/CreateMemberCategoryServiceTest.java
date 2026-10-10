package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ACCOUNT_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.NOW;
import static com.orbit.organization.application.service.MemberCategoryFixtures.ORGANIZATION_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.category;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.CreateMemberCategoryCommand;
import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("직원·기사 유형 생성")
class CreateMemberCategoryServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private MemberCategoryRepository categoryRepository;

    private CreateMemberCategoryService service;

    @BeforeEach
    void setUp() {
        service = new CreateMemberCategoryService(
                organizationRepository, membershipRepository, categoryRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("총관리자가 만들면 발주사를 잠근 뒤 공백을 지운 이름과 대문자 색상으로 저장한다")
    void ownerCreatesCategory() {
        givenRequesterIs(true);
        when(categoryRepository.findUndeletedByName(
                        ORGANIZATION_ID, MemberCategoryKind.TECHNICIAN, new CategoryName("전기 기사")))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(MemberCategory.class)))
                .thenAnswer(invocation -> saved(invocation.getArgument(0)));

        MemberCategoryInfo result = service.create(
                new CreateMemberCategoryCommand(7L, 10L, MemberCategoryKind.TECHNICIAN, "  전기 기사 ", "#ef4444"));

        assertThat(result).isEqualTo(new MemberCategoryInfo(20L, MemberCategoryKind.TECHNICIAN, "전기 기사", "#EF4444"));
        ArgumentCaptor<MemberCategory> saved = ArgumentCaptor.forClass(MemberCategory.class);
        InOrder order = inOrder(organizationRepository, categoryRepository);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(categoryRepository).findUndeletedByName(any(), any(), any());
        order.verify(categoryRepository).save(saved.capture());
        assertThat(saved.getValue().organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(saved.getValue().createdAt()).isEqualTo(NOW);
        assertThat(saved.getValue().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("같은 발주사·같은 종류에 삭제되지 않은 같은 이름의 유형이 있으면 거부한다")
    void rejectsDuplicateName() {
        givenRequesterIs(true);
        when(categoryRepository.findUndeletedByName(ORGANIZATION_ID, MemberCategoryKind.STAFF, new CategoryName("정규직")))
                .thenReturn(Optional.of(category(5L, MemberCategoryKind.STAFF, "정규직")));

        assertRejected(
                new CreateMemberCategoryCommand(7L, 10L, MemberCategoryKind.STAFF, " 정규직", "#3B82F6"),
                OrganizationErrorCode.DUPLICATE_CATEGORY_NAME);
        verify(categoryRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "가", "가나다라마바사아자차카", "설치😀"})
    @DisplayName("유형 이름이 규칙에 맞지 않으면 거부한다")
    void rejectsInvalidName(String name) {
        givenRequesterIs(true);

        assertRejected(
                new CreateMemberCategoryCommand(7L, 10L, MemberCategoryKind.STAFF, name, "#3B82F6"),
                OrganizationErrorCode.INVALID_CATEGORY_INPUT);
        verifyNoInteractions(categoryRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"#000000", "blue"})
    @DisplayName("프리셋에 없는 색상은 거부한다")
    void rejectsColorOutsidePalette(String color) {
        givenRequesterIs(true);

        assertRejected(
                new CreateMemberCategoryCommand(7L, 10L, MemberCategoryKind.STAFF, "정규직", color),
                OrganizationErrorCode.INVALID_CATEGORY_INPUT);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 입력이 틀려도 권한 오류로 거부한다")
    void rejectsStaffBeforeCheckingInput() {
        givenRequesterIs(false);

        assertRejected(
                new CreateMemberCategoryCommand(7L, 10L, MemberCategoryKind.STAFF, "가", null),
                OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(organizationRepository, categoryRepository);
    }

    @Test
    @DisplayName("그 발주사의 활성 소속이 없으면(기사 포함) 거부한다")
    void rejectsRequesterWithoutActiveMembership() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertRejected(
                new CreateMemberCategoryCommand(7L, 10L, MemberCategoryKind.STAFF, "정규직", "#3B82F6"),
                OrganizationErrorCode.NOT_ORGANIZATION_MEMBER);
        verifyNoInteractions(organizationRepository, categoryRepository);
    }

    private void givenRequesterIs(boolean owner) {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(requester(owner)));
    }

    private void assertRejected(CreateMemberCategoryCommand command, BaseCode errorCode) {
        assertThatThrownBy(() -> service.create(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }

    private static MemberCategory saved(MemberCategory category) {
        return MemberCategory.reconstitute(
                new MemberCategoryId(20L),
                category.organizationId(),
                category.kind(),
                category.name(),
                category.color(),
                category.createdAt(),
                category.updatedAt(),
                false,
                null);
    }
}
