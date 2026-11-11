package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
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
import com.orbit.organization.WorkTypeLookup;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

@ExtendWith(MockitoExtension.class)
@DisplayName("organization 작업 유형 표시 정보를 작업 유형 표시 정보로 번역")
class OrganizationWorkTypeLabelsAdapterTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    @Mock
    private WorkTypeLookup workTypeLookup;

    private OrganizationWorkTypeLabelsAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrganizationWorkTypeLabelsAdapter(workTypeLookup);
    }

    @Test
    @DisplayName("삭제된 유형도 이름·색·삭제 표시를 유형 식별자별로 옮긴다")
    void translatesLabelsIncludingDeleted() {
        when(workTypeLookup.findLabels(100L, Set.of(2L, 3L)))
                .thenReturn(List.of(
                        new WorkTypeLabel(2L, "설치", "#3B82F6", false), new WorkTypeLabel(3L, "철거", "#EF4444", true)));

        assertThat(adapter.findLabels(ORGANIZATION_ID, Set.of(new WorkTypeId(2L), new WorkTypeId(3L))))
                .containsOnly(
                        entry(new WorkTypeId(2L), new TypeLabel(2L, "설치", "#3B82F6", false)),
                        entry(new WorkTypeId(3L), new TypeLabel(3L, "철거", "#EF4444", true)));
    }

    @Test
    @DisplayName("organization이 돌려주지 않은 유형은 결과에서 빠진다")
    void omitsUnknownTypes() {
        when(workTypeLookup.findLabels(100L, Set.of(2L, 9L)))
                .thenReturn(List.of(new WorkTypeLabel(2L, "설치", "#3B82F6", false)));

        assertThat(adapter.findLabels(ORGANIZATION_ID, Set.of(new WorkTypeId(2L), new WorkTypeId(9L))))
                .containsOnlyKeys(new WorkTypeId(2L));
    }

    @Test
    @DisplayName("찾을 유형이 없으면 organization을 부르지 않고 빈 결과다")
    void skipsLookupForEmptyIds() {
        assertThat(adapter.findLabels(ORGANIZATION_ID, Set.of())).isEmpty();

        verifyNoInteractions(workTypeLookup);
    }
}
