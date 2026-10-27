package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ACCOUNT_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.ORGANIZATION_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.requester;
import static com.orbit.organization.application.service.MemberCategoryFixtures.workType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.query.dto.ListWorkTypesQuery;
import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("작업 유형 목록 조회")
class ListWorkTypesServiceTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private WorkTypeRepository workTypeRepository;

    private ListWorkTypesService service;

    @BeforeEach
    void setUp() {
        service = new ListWorkTypesService(membershipRepository, workTypeRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("총관리자와 직원은 삭제되지 않은 작업 유형을 저장소 순서대로 받는다")
    void activeStaffMembershipListsWorkTypes(boolean owner) {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(requester(owner)));
        when(workTypeRepository.listUndeleted(ORGANIZATION_ID))
                .thenReturn(List.of(workType(3L, "설치"), workType(1L, "수리")));

        List<WorkTypeInfo> result = service.list(new ListWorkTypesQuery(7L, 10L));

        assertThat(result)
                .extracting(WorkTypeInfo::workTypeId, WorkTypeInfo::name)
                .containsExactly(tuple(3L, "설치"), tuple(1L, "수리"));
    }

    @Test
    @DisplayName("그 발주사의 활성 직원 소속이 없으면(기사 포함) 거부한다")
    void rejectsRequesterWithoutActiveMembership() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(new ListWorkTypesQuery(7L, 10L)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.NOT_ORGANIZATION_MEMBER));
        verifyNoInteractions(workTypeRepository);
    }
}
