package com.orbit.organization.application.port.out;

import java.util.List;
import java.util.Set;

import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;

/** 구성원 표시 정보 조회. 유형은 같은 발주사의 삭제되지 않은 유형만 붙인다. */
public interface MemberDirectoryQueryPort {

    /** 그 발주사의 지정한 직원 소속을 상태와 관계없이 읽는다. */
    List<DirectoryStaff> findStaff(OrganizationId organizationId, Set<MembershipId> membershipIds);

    /** 그 발주사의 지정한 기사 계약을 상태와 관계없이 읽는다. */
    List<DirectoryTechnician> findTechnicians(OrganizationId organizationId, Set<TechnicianId> technicianIds);

    /** 그 발주사의 활성 기사 계약을 읽는다. */
    List<DirectoryTechnician> listActiveTechnicians(OrganizationId organizationId);
}
