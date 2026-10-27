package com.orbit.organization.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.UpdateWorkTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.UpdateWorkTypeCommand;
import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.WorkType;
import com.orbit.organization.domain.WorkTypeId;

/**
 * 작업 유형의 이름·색상 수정. 삭제된 유형은 없는 유형과 같이 거부한다. 발주사 행을 잠가 이름 중복 확인과 삭제와의 엇갈림을 막는다. 작업은 유형을 ID로 참조하므로
 * 바뀐 이름·색상은 작업중·완료·취소를 포함한 모든 작업 표시에 그대로 반영된다.
 */
@Service
public class UpdateWorkTypeService implements UpdateWorkTypeUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final WorkTypeRepository workTypeRepository;

    public UpdateWorkTypeService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            WorkTypeRepository workTypeRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.workTypeRepository = workTypeRepository;
    }

    @Override
    @Transactional
    public WorkTypeInfo update(UpdateWorkTypeCommand command) {
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        WorkTypeId workTypeId = WorkTypes.id(command.workTypeId());
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, command.accountId(), command.organizationId());
        CategoryName name = MemberCategories.name(command.name());
        CategoryColor color = MemberCategories.color(command.color());

        OrganizationLocks.lock(organizationRepository, organizationId);
        WorkType workType = WorkTypes.findUndeleted(workTypeRepository, organizationId, workTypeId);
        WorkTypes.requireUniqueName(workTypeRepository, organizationId, name, workTypeId);
        workType.change(name, color);
        return WorkTypeInfo.from(WorkTypes.save(workTypeRepository, workType));
    }
}
