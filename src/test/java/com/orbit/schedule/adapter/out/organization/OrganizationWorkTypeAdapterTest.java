package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.WorkTypeLookup;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

@ExtendWith(MockitoExtension.class)
@DisplayName("organization 작업 유형 선택 가능 확인을 작업 유형 확인으로 번역")
class OrganizationWorkTypeAdapterTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final WorkTypeId WORK_TYPE_ID = new WorkTypeId(2L);

    @Mock
    private WorkTypeLookup workTypeLookup;

    private OrganizationWorkTypeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrganizationWorkTypeAdapter(workTypeLookup);
    }

    @Test
    @DisplayName("organization이 선택할 수 있는 유형으로 보면 선택할 수 있다")
    void isSelectableWhenOrganizationConfirms() {
        when(workTypeLookup.isSelectable(100L, 2L)).thenReturn(true);

        assertThat(adapter.isSelectable(ORGANIZATION_ID, WORK_TYPE_ID)).isTrue();
    }

    @Test
    @DisplayName("organization이 선택할 수 없다고 하면 선택할 수 없다")
    void isNotSelectableWhenOrganizationDenies() {
        when(workTypeLookup.isSelectable(100L, 2L)).thenReturn(false);

        assertThat(adapter.isSelectable(ORGANIZATION_ID, WORK_TYPE_ID)).isFalse();
    }
}
