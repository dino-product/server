package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ACCOUNT_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.NOW;
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

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.orbit.organization.WorkTypeDeleted;
import com.orbit.organization.WorkTypeRelease;
import com.orbit.organization.WorkTypeReleasedWork;
import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.DeleteWorkTypeCommand;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.WorkType;
import com.orbit.organization.domain.WorkTypeId;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("작업 유형 삭제")
class DeleteWorkTypeServiceTest {

    private static final WorkTypeId WORK_TYPE_ID = new WorkTypeId(5L);

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private WorkTypeRepository workTypeRepository;

    @Mock
    private WorkTypeRelease workTypeRelease;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DeleteWorkTypeService service;

    @BeforeEach
    void setUp() {
        service = new DeleteWorkTypeService(
                organizationRepository,
                membershipRepository,
                workTypeRepository,
                workTypeRelease,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("발주사를 잠근 뒤 소프트 삭제를 저장하고, 시작 전 작업의 유형을 비운 뒤 비운 작업을 담아 삭제 이벤트를 발행한다")
    void softDeletesReleasesAndPublishes() {
        givenRequesterIs(true);
        givenWorkType(workType(5L, "설치"));
        when(workTypeRepository.save(any(WorkType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        List<WorkTypeReleasedWork> released =
                List.of(new WorkTypeReleasedWork(31L, null), new WorkTypeReleasedWork(32L, 21L));
        when(workTypeRelease.releaseForDeletion(10L, 5L)).thenReturn(released);

        service.delete(new DeleteWorkTypeCommand(7L, 10L, 5L));

        ArgumentCaptor<WorkType> saved = ArgumentCaptor.forClass(WorkType.class);
        InOrder order = inOrder(organizationRepository, workTypeRepository, workTypeRelease, eventPublisher);
        order.verify(organizationRepository).lock(ORGANIZATION_ID);
        order.verify(workTypeRepository).findById(WORK_TYPE_ID);
        order.verify(workTypeRepository).save(saved.capture());
        order.verify(workTypeRelease).releaseForDeletion(10L, 5L);
        order.verify(eventPublisher).publishEvent(new WorkTypeDeleted(10L, 5L, released, NOW));
        assertThat(saved.getValue().isDeleted()).isTrue();
        assertThat(saved.getValue().deletedAt()).contains(NOW);
    }

    @Test
    @DisplayName("이미 삭제된 작업 유형은 아무것도 바꾸지 않고 성공하며 다시 해제·발행하지 않는다")
    void deletingAgainChangesNothing() {
        givenRequesterIs(true);
        givenWorkType(deletedWorkType(5L, "설치"));

        service.delete(new DeleteWorkTypeCommand(7L, 10L, 5L));

        verify(workTypeRepository, never()).save(any());
        verifyNoInteractions(workTypeRelease, eventPublisher);
    }

    @Test
    @DisplayName("다른 발주사의 작업 유형은 없는 유형과 같이 거부한다")
    void rejectsWorkTypeOfOtherOrganization() {
        givenRequesterIs(true);
        givenWorkType(workType(5L, OTHER_ORGANIZATION_ID, "설치"));

        assertRejected(new DeleteWorkTypeCommand(7L, 10L, 5L), OrganizationErrorCode.CATEGORY_NOT_FOUND);
        verifyNoInteractions(workTypeRelease, eventPublisher);
    }

    @Test
    @DisplayName("없는 작업 유형은 거부한다")
    void rejectsMissingWorkType() {
        givenRequesterIs(true);
        when(workTypeRepository.findById(WORK_TYPE_ID)).thenReturn(Optional.empty());

        assertRejected(new DeleteWorkTypeCommand(7L, 10L, 5L), OrganizationErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 거부한다")
    void rejectsStaff() {
        givenRequesterIs(false);

        assertRejected(new DeleteWorkTypeCommand(7L, 10L, 5L), OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(organizationRepository, workTypeRepository, workTypeRelease, eventPublisher);
    }

    @Test
    @DisplayName("작업 유형 식별자 형식이 틀리면 소속을 찾지 않고 거부한다")
    void rejectsMalformedWorkTypeId() {
        assertRejected(new DeleteWorkTypeCommand(7L, 10L, 0L), CommonErrorCode.BAD_REQUEST);
        verifyNoInteractions(membershipRepository, workTypeRepository);
    }

    private void givenRequesterIs(boolean owner) {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(requester(owner)));
    }

    private void givenWorkType(WorkType workType) {
        when(workTypeRepository.findById(WORK_TYPE_ID)).thenReturn(Optional.of(workType));
    }

    private void assertRejected(DeleteWorkTypeCommand command, BaseCode errorCode) {
        assertThatThrownBy(() -> service.delete(command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }
}
