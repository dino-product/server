package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ACCOUNT_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.ORGANIZATION_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.OTHER_ORGANIZATION_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.deletedWorkType;
import static com.orbit.organization.application.service.MemberCategoryFixtures.requester;
import static com.orbit.organization.application.service.MemberCategoryFixtures.workType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.WorkTypeDeletionImpact;
import com.orbit.organization.WorkTypeRelease;
import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.query.dto.GetWorkTypeDeletionImpactQuery;
import com.orbit.organization.application.port.in.query.dto.WorkTypeDeletionImpactInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.WorkType;
import com.orbit.organization.domain.WorkTypeId;
import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("작업 유형 삭제 영향 건수")
class GetWorkTypeDeletionImpactServiceTest {

    private static final WorkTypeId WORK_TYPE_ID = new WorkTypeId(5L);

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private WorkTypeRepository workTypeRepository;

    @Mock
    private WorkTypeRelease workTypeRelease;

    private GetWorkTypeDeletionImpactService service;

    @BeforeEach
    void setUp() {
        service = new GetWorkTypeDeletionImpactService(membershipRepository, workTypeRepository, workTypeRelease);
    }

    @Test
    @DisplayName("총관리자는 유형 없음이 될 작업 수와 유형이 유지될 작업 수를 받는다")
    void ownerGetsImpact() {
        givenRequesterIs(true);
        givenWorkType(workType(5L, "설치"));
        when(workTypeRelease.countImpact(10L, 5L)).thenReturn(new WorkTypeDeletionImpact(2L, 3L));

        assertThat(service.get(new GetWorkTypeDeletionImpactQuery(7L, 10L, 5L)))
                .isEqualTo(new WorkTypeDeletionImpactInfo(2L, 3L));
    }

    @Test
    @DisplayName("삭제된 작업 유형은 없는 유형과 같이 거부한다")
    void rejectsDeletedWorkType() {
        givenRequesterIs(true);
        givenWorkType(deletedWorkType(5L, "설치"));

        assertRejected(OrganizationErrorCode.CATEGORY_NOT_FOUND);
        verifyNoInteractions(workTypeRelease);
    }

    @Test
    @DisplayName("다른 발주사의 작업 유형은 없는 유형과 같이 거부한다")
    void rejectsWorkTypeOfOtherOrganization() {
        givenRequesterIs(true);
        givenWorkType(workType(5L, OTHER_ORGANIZATION_ID, "설치"));

        assertRejected(OrganizationErrorCode.CATEGORY_NOT_FOUND);
        verifyNoInteractions(workTypeRelease);
    }

    @Test
    @DisplayName("총관리자가 아닌 직원은 거부한다")
    void rejectsStaff() {
        givenRequesterIs(false);

        assertRejected(OrganizationErrorCode.OWNER_ONLY);
        verifyNoInteractions(workTypeRepository, workTypeRelease);
    }

    private void givenRequesterIs(boolean owner) {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(requester(owner)));
    }

    private void givenWorkType(WorkType workType) {
        when(workTypeRepository.findById(WORK_TYPE_ID)).thenReturn(Optional.of(workType));
    }

    private void assertRejected(BaseCode errorCode) {
        assertThatThrownBy(() -> service.get(new GetWorkTypeDeletionImpactQuery(7L, 10L, 5L)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }
}
