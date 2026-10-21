package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ORGANIZATION_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.deletedWorkType;
import static com.orbit.organization.application.service.MemberCategoryFixtures.workType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.WorkTypeLabel;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.WorkTypeId;

@ExtendWith(MockitoExtension.class)
@DisplayName("작업 유형 공개 조회")
class WorkTypeLookupServiceTest {

    @Mock
    private WorkTypeRepository workTypeRepository;

    private WorkTypeLookupService service;

    @BeforeEach
    void setUp() {
        service = new WorkTypeLookupService(workTypeRepository);
    }

    @Test
    @DisplayName("그 발주사의 삭제되지 않은 작업 유형이면 선택할 수 있다")
    void selectableWhenRepositoryLocksUndeletedWorkType() {
        when(workTypeRepository.lockIfSelectable(ORGANIZATION_ID, new WorkTypeId(5L)))
                .thenReturn(true);

        assertThat(service.isSelectable(10L, 5L)).isTrue();
    }

    @Test
    @DisplayName("다른 발주사·삭제된·없는 작업 유형이면 선택할 수 없다")
    void notSelectableOtherwise() {
        when(workTypeRepository.lockIfSelectable(ORGANIZATION_ID, new WorkTypeId(5L)))
                .thenReturn(false);

        assertThat(service.isSelectable(10L, 5L)).isFalse();
    }

    @Test
    @DisplayName("표시 정보는 삭제된 작업 유형도 삭제 표시와 함께 담는다")
    void labelsIncludeDeletedWorkTypes() {
        when(workTypeRepository.listByIds(ORGANIZATION_ID, Set.of(new WorkTypeId(5L), new WorkTypeId(6L))))
                .thenReturn(List.of(workType(5L, "설치"), deletedWorkType(6L, "수리")));

        assertThat(service.findLabels(10L, Set.of(5L, 6L)))
                .containsExactlyInAnyOrder(
                        new WorkTypeLabel(5L, "설치", "#3B82F6", false), new WorkTypeLabel(6L, "수리", "#3B82F6", true));
    }

    @Test
    @DisplayName("찾을 작업 유형이 없으면 저장소를 부르지 않고 빈 목록이다")
    void emptyLabelsWithoutIds() {
        assertThat(service.findLabels(10L, Set.of())).isEmpty();
        verifyNoInteractions(workTypeRepository);
    }

    @Test
    @DisplayName("식별자가 양수가 아니거나 null이면 호출자 오류다")
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> service.isSelectable(0L, 5L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.isSelectable(10L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findLabels(10L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findLabels(10L, Set.of(-1L))).isInstanceOf(IllegalArgumentException.class);
    }
}
