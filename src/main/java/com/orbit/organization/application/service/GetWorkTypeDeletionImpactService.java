package com.orbit.organization.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.WorkTypeDeletionImpact;
import com.orbit.organization.WorkTypeRelease;
import com.orbit.organization.application.port.in.query.GetWorkTypeDeletionImpactUseCase;
import com.orbit.organization.application.port.in.query.dto.GetWorkTypeDeletionImpactQuery;
import com.orbit.organization.application.port.in.query.dto.WorkTypeDeletionImpactInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.WorkTypeId;

/**
 * 작업 유형 삭제 확인 창의 영향 건수. 삭제와 같은 총관리자 권한으로 보며, 작업 수는 작업을 소유한 schedule이 요구 인터페이스로 센다. 확인 창을 띄운 뒤 작업 상태가
 * 바뀔 수 있어 건수는 그 시점의 안내이고 잠그지 않는다. 이미 삭제된 유형은 목록에서 빠진 유형이므로 없는 유형과 같이 거부한다.
 */
@Service
public class GetWorkTypeDeletionImpactService implements GetWorkTypeDeletionImpactUseCase {

    private final MembershipRepository membershipRepository;
    private final WorkTypeRepository workTypeRepository;
    private final WorkTypeRelease workTypeRelease;

    public GetWorkTypeDeletionImpactService(
            MembershipRepository membershipRepository,
            WorkTypeRepository workTypeRepository,
            WorkTypeRelease workTypeRelease) {
        this.membershipRepository = membershipRepository;
        this.workTypeRepository = workTypeRepository;
        this.workTypeRelease = workTypeRelease;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkTypeDeletionImpactInfo get(GetWorkTypeDeletionImpactQuery query) {
        Objects.requireNonNull(query.accountId(), "accountId must not be null");
        WorkTypeId workTypeId = WorkTypes.id(query.workTypeId());
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, query.accountId(), query.organizationId());

        WorkTypes.findUndeleted(workTypeRepository, organizationId, workTypeId);
        WorkTypeDeletionImpact impact = workTypeRelease.countImpact(organizationId.value(), workTypeId.value());
        return new WorkTypeDeletionImpactInfo(impact.releasedCount(), impact.keptCount());
    }
}
