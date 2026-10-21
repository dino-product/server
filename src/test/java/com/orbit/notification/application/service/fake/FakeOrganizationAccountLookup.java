package com.orbit.notification.application.service.fake;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.orbit.organization.OrganizationAccountLookup;

/** organization 계정 조회 계약의 fake. 테스트가 넣은 활성 기사 계약·직원 소속의 계정만 찾고, 넣지 않은 대상은 빈 값이다. */
public class FakeOrganizationAccountLookup implements OrganizationAccountLookup {

    private final Map<Key, Long> technicians = new HashMap<>();
    private final Map<Key, Long> staff = new HashMap<>();

    public FakeOrganizationAccountLookup technician(Long organizationId, Long technicianId, Long accountId) {
        technicians.put(new Key(organizationId, technicianId), accountId);
        return this;
    }

    public FakeOrganizationAccountLookup staff(Long organizationId, Long membershipId, Long accountId) {
        staff.put(new Key(organizationId, membershipId), accountId);
        return this;
    }

    @Override
    public Optional<Long> findActiveStaffAccountId(Long organizationId, Long membershipId) {
        return Optional.ofNullable(staff.get(key(organizationId, membershipId)));
    }

    @Override
    public Optional<Long> findActiveTechnicianAccountId(Long organizationId, Long technicianId) {
        return Optional.ofNullable(technicians.get(key(organizationId, technicianId)));
    }

    // 계약의 호출 전제: 인자는 null이 아닌 양수다.
    private static Key key(Long organizationId, Long memberId) {
        if (organizationId == null || organizationId <= 0 || memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("identifiers must be positive");
        }
        return new Key(organizationId, memberId);
    }

    private record Key(Long organizationId, Long memberId) {}
}
