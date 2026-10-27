package com.orbit.organization.application.service;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.MemberCategoryLabel;
import com.orbit.organization.MemberDirectory;
import com.orbit.organization.StaffDirectoryEntry;
import com.orbit.organization.TechnicianDirectoryEntry;
import com.orbit.organization.application.port.out.DirectoryCategory;
import com.orbit.organization.application.port.out.DirectoryStaff;
import com.orbit.organization.application.port.out.DirectoryTechnician;
import com.orbit.organization.application.port.out.MemberDirectoryQueryPort;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;

@Service
public class MemberDirectoryService implements MemberDirectory {

    private final MemberDirectoryQueryPort queryPort;

    public MemberDirectoryService(MemberDirectoryQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffDirectoryEntry> findStaff(Long organizationId, Set<Long> membershipIds) {
        OrganizationId organization = new OrganizationId(organizationId);
        Set<MembershipId> ids = ids(membershipIds, MembershipId::new);
        if (ids.isEmpty()) {
            return List.of();
        }
        return queryPort.findStaff(organization, ids).stream()
                .map(MemberDirectoryService::toEntry)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianDirectoryEntry> findTechnicians(Long organizationId, Set<Long> technicianIds) {
        OrganizationId organization = new OrganizationId(organizationId);
        Set<TechnicianId> ids = ids(technicianIds, TechnicianId::new);
        if (ids.isEmpty()) {
            return List.of();
        }
        return queryPort.findTechnicians(organization, ids).stream()
                .map(MemberDirectoryService::toEntry)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianDirectoryEntry> listActiveTechnicians(Long organizationId) {
        return queryPort.listActiveTechnicians(new OrganizationId(organizationId)).stream()
                .map(MemberDirectoryService::toEntry)
                .toList();
    }

    private static <T> Set<T> ids(Set<Long> values, Function<Long, T> toId) {
        if (values == null) {
            throw new IllegalArgumentException("ids must not be null");
        }
        return values.stream().map(toId).collect(Collectors.toSet());
    }

    private static StaffDirectoryEntry toEntry(DirectoryStaff staff) {
        return new StaffDirectoryEntry(
                staff.membershipId().value(), staff.accountId().value(), staff.owner(), label(staff.staffCategory()));
    }

    private static TechnicianDirectoryEntry toEntry(DirectoryTechnician technician) {
        return new TechnicianDirectoryEntry(
                technician.technicianId().value(),
                technician.accountId().value(),
                label(technician.technicianCategory()));
    }

    private static MemberCategoryLabel label(DirectoryCategory category) {
        return category == null
                ? null
                : new MemberCategoryLabel(category.id().value(), category.name(), category.color());
    }
}
