package com.orbit.organization.adapter.out.persistence;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.persistence.EntityManager;

import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.DirectoryCategory;
import com.orbit.organization.application.port.out.DirectoryStaff;
import com.orbit.organization.application.port.out.DirectoryTechnician;
import com.orbit.organization.application.port.out.MemberDirectoryQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;

/** 구성원과 유형을 한 문장으로 읽는다. 유형은 같은 발주사의 삭제되지 않은 것만 붙여, 삭제된 유형이 남은 행은 유형 없음으로 보인다. */
@Repository
class MemberDirectoryQueryAdapter implements MemberDirectoryQueryPort {

    private static final String STAFF = """
            select m.id, m.member_id, m.is_owner, c.id as category_id, c.name, c.color
            from company_memberships m
            left join company_membership_category c
                on c.id = m.company_membership_category_id and c.company_id = m.company_id and c.is_deleted = false
            where m.company_id = :organizationId and m.id in (:ids)
            """;

    private static final String TECHNICIANS = """
            select t.id, t.member_id, c.id as category_id, c.name, c.color
            from technician t
            left join company_technician_category c
                on c.id = t.company_technician_category_id and c.company_id = t.company_id and c.is_deleted = false
            where t.company_id = :organizationId
            """;

    private final EntityManager entityManager;

    MemberDirectoryQueryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DirectoryStaff> findStaff(OrganizationId organizationId, Set<MembershipId> membershipIds) {
        List<Object[]> rows = entityManager
                .createNativeQuery(STAFF)
                .unwrap(NativeQuery.class)
                .addScalar("id", StandardBasicTypes.LONG)
                .addScalar("member_id", StandardBasicTypes.LONG)
                .addScalar("is_owner", StandardBasicTypes.BOOLEAN)
                .addScalar("category_id", StandardBasicTypes.LONG)
                .addScalar("name", StandardBasicTypes.STRING)
                .addScalar("color", StandardBasicTypes.STRING)
                .setParameter("organizationId", organizationId.value())
                .setParameterList(
                        "ids", membershipIds.stream().map(MembershipId::value).toList())
                .getResultList();
        return rows.stream()
                .map(row -> new DirectoryStaff(
                        new MembershipId((Long) row[0]),
                        new AccountId((Long) row[1]),
                        (Boolean) row[2],
                        category(row, 3)))
                .toList();
    }

    @Override
    public List<DirectoryTechnician> findTechnicians(OrganizationId organizationId, Set<TechnicianId> technicianIds) {
        return technicians(
                TECHNICIANS + " and t.id in (:ids)",
                organizationId,
                query -> query.setParameterList(
                        "ids", technicianIds.stream().map(TechnicianId::value).toList()));
    }

    @Override
    public List<DirectoryTechnician> listActiveTechnicians(OrganizationId organizationId) {
        return technicians(
                TECHNICIANS + " and t.status = :active",
                organizationId,
                query -> query.setParameter("active", TechnicianStatus.ACTIVE.name()));
    }

    @SuppressWarnings("unchecked")
    private List<DirectoryTechnician> technicians(
            String sql, OrganizationId organizationId, Consumer<NativeQuery<?>> parameters) {
        NativeQuery<?> query = entityManager
                .createNativeQuery(sql)
                .unwrap(NativeQuery.class)
                .addScalar("id", StandardBasicTypes.LONG)
                .addScalar("member_id", StandardBasicTypes.LONG)
                .addScalar("category_id", StandardBasicTypes.LONG)
                .addScalar("name", StandardBasicTypes.STRING)
                .addScalar("color", StandardBasicTypes.STRING)
                .setParameter("organizationId", organizationId.value());
        parameters.accept(query);
        List<Object[]> rows = (List<Object[]>) query.getResultList();
        return rows.stream()
                .map(row -> new DirectoryTechnician(
                        new TechnicianId((Long) row[0]), new AccountId((Long) row[1]), category(row, 2)))
                .toList();
    }

    private static DirectoryCategory category(Object[] row, int from) {
        return row[from] == null
                ? null
                : new DirectoryCategory(
                        new MemberCategoryId((Long) row[from]), (String) row[from + 1], (String) row[from + 2]);
    }
}
