package com.orbit.organization.application.service;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.out.DuplicateCategoryNameException;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.WorkType;
import com.orbit.organization.domain.WorkTypeId;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 작업 유형 유즈케이스가 함께 쓰는 확인. 이름·색상 변환은 직원·기사 유형과 같은 {@link MemberCategories}를 쓴다. 다른 발주사의 작업 유형은 존재를 드러내지
 * 않도록 없는 유형과 같은 ORGANIZATION-008로 거부한다.
 */
final class WorkTypes {

    private WorkTypes() {}

    static WorkTypeId id(Long value) {
        try {
            return new WorkTypeId(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, exception);
        }
    }

    /** 그 발주사의 작업 유형을 삭제 여부와 관계없이 찾는다. */
    static WorkType find(WorkTypeRepository repository, OrganizationId organizationId, WorkTypeId id) {
        return repository
                .findById(id)
                .filter(workType -> workType.organizationId().equals(organizationId))
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.CATEGORY_NOT_FOUND));
    }

    /** 그 발주사의 삭제되지 않은 작업 유형을 찾는다. 삭제된 유형은 목록에서 빠진 유형이므로 없는 유형과 같이 거부한다. */
    static WorkType findUndeleted(WorkTypeRepository repository, OrganizationId organizationId, WorkTypeId id) {
        WorkType workType = find(repository, organizationId, id);
        if (workType.isDeleted()) {
            throw new BusinessException(OrganizationErrorCode.CATEGORY_NOT_FOUND);
        }
        return workType;
    }

    /** 같은 발주사의 삭제되지 않은 다른 작업 유형이 이 이름을 쓰면 거부한다. {@code self}는 수정 중인 유형이며 생성이면 {@code null}이다. */
    static void requireUniqueName(
            WorkTypeRepository repository, OrganizationId organizationId, CategoryName name, WorkTypeId self) {
        repository
                .findUndeletedByName(organizationId, name)
                .filter(existing -> self == null || !existing.id().orElseThrow().equals(self))
                .ifPresent(existing -> {
                    throw new BusinessException(OrganizationErrorCode.DUPLICATE_CATEGORY_NAME);
                });
    }

    /** 이름 유일 인덱스는 잠금을 거치지 않은 경합의 최종 방어선이다. 그 거부도 미리 확인한 중복과 같은 ORGANIZATION-009로 알린다. */
    static WorkType save(WorkTypeRepository repository, WorkType workType) {
        try {
            return repository.save(workType);
        } catch (DuplicateCategoryNameException exception) {
            throw new BusinessException(OrganizationErrorCode.DUPLICATE_CATEGORY_NAME, exception);
        }
    }
}
