package com.orbit.organization.application.service;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.out.DuplicateCategoryNameException;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 직원·기사 유형 유즈케이스가 함께 쓰는 확인. 입력 규칙은 도메인 값객체가 소유하고 여기서는 위반을 ORGANIZATION-007로 바꾼다. 다른 발주사의 유형과 삭제된 유형은
 * 존재를 드러내지 않도록 없는 유형과 같은 ORGANIZATION-008로 거부한다.
 */
final class MemberCategories {

    private MemberCategories() {}

    static MemberCategoryId id(Long value) {
        try {
            return new MemberCategoryId(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, exception);
        }
    }

    static CategoryName name(String value) {
        try {
            return new CategoryName(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(OrganizationErrorCode.INVALID_CATEGORY_INPUT, exception);
        }
    }

    static CategoryColor color(String value) {
        try {
            return new CategoryColor(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(OrganizationErrorCode.INVALID_CATEGORY_INPUT, exception);
        }
    }

    /** 그 발주사의 유형을 삭제 여부와 관계없이 찾는다. */
    static MemberCategory find(
            MemberCategoryRepository repository,
            OrganizationId organizationId,
            MemberCategoryKind kind,
            MemberCategoryId id) {
        return repository
                .findById(kind, id)
                .filter(category -> category.organizationId().equals(organizationId))
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.CATEGORY_NOT_FOUND));
    }

    /** 같은 발주사·같은 종류의 삭제되지 않은 다른 유형이 이 이름을 쓰면 거부한다. {@code self}는 수정 중인 유형이며 생성이면 {@code null}이다. */
    static void requireUniqueName(
            MemberCategoryRepository repository,
            OrganizationId organizationId,
            MemberCategoryKind kind,
            CategoryName name,
            MemberCategoryId self) {
        repository
                .findUndeletedByName(organizationId, kind, name)
                .filter(existing -> self == null || !existing.id().orElseThrow().equals(self))
                .ifPresent(existing -> {
                    throw new BusinessException(OrganizationErrorCode.DUPLICATE_CATEGORY_NAME);
                });
    }

    /** 이름 유일 인덱스는 잠금을 거치지 않은 경합의 최종 방어선이다. 그 거부도 미리 확인한 중복과 같은 ORGANIZATION-009로 알린다. */
    static MemberCategory save(MemberCategoryRepository repository, MemberCategory category) {
        try {
            return repository.save(category);
        } catch (DuplicateCategoryNameException exception) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_CATEGORY_NAME, exception);
        }
    }
}
