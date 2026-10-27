package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.CreateWorkTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateWorkTypeCommand;
import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.WorkType;

/** 작업 유형 생성. 이름 중복 확인과 저장 사이의 경합을 막으려고 발주사 행을 잠근 뒤 확인한다. 삭제된 유형과 같은 이름은 새 유형으로 만든다. */
@Service
public class CreateWorkTypeService implements CreateWorkTypeUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final WorkTypeRepository workTypeRepository;

    public CreateWorkTypeService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            WorkTypeRepository workTypeRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.workTypeRepository = workTypeRepository;
    }

    @Override
    @Transactional
    public WorkTypeInfo create(CreateWorkTypeCommand command) {
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, command.accountId(), command.organizationId());
        CategoryName name = MemberCategories.name(command.name());
        CategoryColor color = MemberCategories.color(command.color());

        OrganizationLocks.lock(organizationRepository, organizationId);
        WorkTypes.requireUniqueName(workTypeRepository, organizationId, name, null);
        WorkType workType = WorkType.create(organizationId, name, color);
        return WorkTypeInfo.from(WorkTypes.save(workTypeRepository, workType));
    }
}
