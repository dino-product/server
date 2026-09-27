package com.orbit.organization.adapter.out.persistence;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.TechnicianTypeUsagePort;
import com.orbit.organization.domain.TechnicianTypeId;

/** HM-263 기사 계약 테이블과 FK가 생기면 실제 현재 지정 조회로 교체한다. */
@Repository
class TechnicianTypeUsageAdapter implements TechnicianTypeUsagePort {
    @Override
    public boolean isAssigned(TechnicianTypeId id) {
        return false;
    }
}
