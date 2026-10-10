package com.orbit.organization.application.service;

import static com.orbit.organization.application.service.MemberCategoryFixtures.ACCOUNT_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.ORGANIZATION_ID;
import static com.orbit.organization.application.service.MemberCategoryFixtures.category;
import static com.orbit.organization.application.service.MemberCategoryFixtures.requester;
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
import com.orbit.organization.application.port.in.query.dto.ListMemberCategoriesQuery;
import com.orbit.organization.application.port.in.query.dto.MemberCategoryInfo;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("직원·기사 유형 목록 조회")
class ListMemberCategoriesServiceTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private MemberCategoryRepository categoryRepository;

    private ListMemberCategoriesService service;

    @BeforeEach
    void setUp() {
        service = new ListMemberCategoriesService(membershipRepository, categoryRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("총관리자와 직원은 삭제되지 않은 유형을 저장소 순서대로 받는다")
    void activeStaffMembershipListsCategories(boolean owner) {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.of(requester(owner)));
        when(categoryRepository.listUndeleted(ORGANIZATION_ID, MemberCategoryKind.TECHNICIAN))
                .thenReturn(List.of(
                        category(3L, MemberCategoryKind.TECHNICIAN, "전기 기사"),
                        category(1L, MemberCategoryKind.TECHNICIAN, "배관 기사")));

        List<MemberCategoryInfo> result =
                service.list(new ListMemberCategoriesQuery(7L, 10L, MemberCategoryKind.TECHNICIAN));

        assertThat(result)
                .extracting(MemberCategoryInfo::categoryId, MemberCategoryInfo::name)
                .containsExactly(tuple(3L, "전기 기사"), tuple(1L, "배관 기사"));
    }

    @Test
    @DisplayName("그 발주사의 활성 직원 소속이 없으면 거부한다")
    void rejectsRequesterWithoutActiveMembership() {
        when(membershipRepository.findActive(ORGANIZATION_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(new ListMemberCategoriesQuery(7L, 10L, MemberCategoryKind.STAFF)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.NOT_ORGANIZATION_MEMBER));
        verifyNoInteractions(categoryRepository);
    }
}
