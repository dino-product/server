package com.orbit.organization.application.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;

/** 기사 계약 저장소의 메모리 fake. 저장 순서대로 1부터 식별자를 붙이고, 식별자가 있는 계약은 같은 행을 덮어쓴다. */
class InMemoryTechnicianRepository implements TechnicianRepository {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00Z");

    private final Map<TechnicianId, Technician> technicians = new LinkedHashMap<>();
    private long nextId = 1;
    private int saveCount;

    /** 저장소에 이미 있는 기사 계약을 둔다. 저장 횟수에 넣지 않는다. */
    Technician given(OrganizationId organizationId, AccountId accountId, TechnicianStatus status) {
        Technician technician = Technician.reconstitute(
                new TechnicianId(nextId++), organizationId, accountId, status, JOINED_AT, JOINED_AT);
        technicians.put(technician.id().orElseThrow(), technician);
        return technician;
    }

    Technician stored(TechnicianId id) {
        return technicians.get(id);
    }

    int saveCount() {
        return saveCount;
    }

    @Override
    public Technician save(Technician technician) {
        saveCount++;
        Technician saved = Technician.reconstitute(
                new TechnicianId(nextId++),
                technician.organizationId(),
                technician.accountId(),
                technician.status(),
                technician.joinedAt(),
                technician.statusChangedAt());
        technicians.put(saved.id().orElseThrow(), saved);
        return saved;
    }

    @Override
    public Optional<Technician> find(OrganizationId organizationId, AccountId accountId) {
        return technicians.values().stream()
                .filter(technician -> technician.organizationId().equals(organizationId))
                .filter(technician -> technician.accountId().equals(accountId))
                .findFirst();
    }

    @Override
    public Technician reactivate(Technician technician, boolean unassignCategory) {
        return put(technician);
    }

    @Override
    public Technician withdraw(Technician technician) {
        return put(technician);
    }

    @Override
    public List<OrganizationId> listOrganizationsOf(AccountId accountId) {
        return technicians.values().stream()
                .filter(technician -> technician.accountId().equals(accountId))
                .map(Technician::organizationId)
                .distinct()
                .sorted(Comparator.comparing(OrganizationId::value))
                .toList();
    }

    /** 기사 유형은 계약 도메인에 없어 이 fake로는 다루지 않는다. */
    @Override
    public int unassignTechnicianCategory(
            OrganizationId organizationId, MemberCategoryId categoryId, Instant unassignedAt) {
        throw new UnsupportedOperationException("technician categories are not modeled by this fake");
    }

    private Technician put(Technician technician) {
        TechnicianId id = technician.id().orElseThrow();
        if (!technicians.containsKey(id)) {
            throw new IllegalArgumentException("technician must be stored");
        }
        saveCount++;
        technicians.put(id, technician);
        return technician;
    }
}
