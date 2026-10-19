package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.application.port.out.AccountTechnicianContract;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.support.TestcontainersConfiguration;

/** 기사 계약을 만드는 유즈케이스(참여 요청 승인)가 아직 없어 기사 계약 행은 SQL로 넣는다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("기사 계약 조회(PostgreSQL)")
class TechnicianContractQueryAdapterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00.123456Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-10-05T00:00:00.654321Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(200L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final AccountId OTHER_ACCOUNT_ID = new AccountId(8L);

    @Autowired
    private EntityManager entityManager;

    private TechnicianContractQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TechnicianContractQueryAdapter(entityManager);
    }

    @Test
    @DisplayName("모든 발주사의 활성·비활성 기사 계약을 활성 여부와 함께 찾는다")
    void findsActiveAndInactiveContractsOfAllOrganizations() {
        long active = insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");
        long deactivated = insertTechnician(OTHER_ORGANIZATION_ID, ACCOUNT_ID, "DEACTIVATED");

        assertThat(adapter.listByAccount(ACCOUNT_ID))
                .containsExactlyInAnyOrder(
                        new AccountTechnicianContract(new TechnicianId(active), ORGANIZATION_ID, true),
                        new AccountTechnicianContract(new TechnicianId(deactivated), OTHER_ORGANIZATION_ID, false));
    }

    @Test
    @DisplayName("다른 계정의 기사 계약은 찾지 않는다")
    void ignoresContractsOfOtherAccount() {
        insertTechnician(ORGANIZATION_ID, OTHER_ACCOUNT_ID, "ACTIVE");

        assertThat(adapter.listByAccount(ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("이미 읽어 둔 기사 계약 엔티티가 있어도 그 뒤 DB에 쓰인 상태를 읽는다")
    void readsDatabaseStateInsteadOfLoadedEntity() {
        long id = insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");
        entityManager.find(TechnicianJpaEntity.class, id);
        entityManager
                .createNativeQuery("update technician set status = 'DEACTIVATED' where id = ?")
                .setParameter(1, id)
                .executeUpdate();

        assertThat(adapter.listByAccount(ACCOUNT_ID))
                .containsExactly(new AccountTechnicianContract(new TechnicianId(id), ORGANIZATION_ID, false));
    }

    @Test
    @DisplayName("같은 발주사의 활성 기사 계약이면 활성이다")
    void confirmsActiveContractOfSameOrganization() {
        long id = insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");

        assertThat(adapter.existsActive(ORGANIZATION_ID, new TechnicianId(id))).isTrue();
    }

    @Test
    @DisplayName("다른 발주사의 기사 계약, 비활성 계약, 없는 계약은 활성이 아니다")
    void deniesContractOfOtherOrganizationInactiveOrMissing() {
        long otherOrganization = insertTechnician(OTHER_ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");
        long deactivated = insertTechnician(ORGANIZATION_ID, OTHER_ACCOUNT_ID, "DEACTIVATED");

        assertThat(adapter.existsActive(ORGANIZATION_ID, new TechnicianId(otherOrganization)))
                .isFalse();
        assertThat(adapter.existsActive(ORGANIZATION_ID, new TechnicianId(deactivated)))
                .isFalse();
        assertThat(adapter.existsActive(ORGANIZATION_ID, new TechnicianId(deactivated + 1000)))
                .isFalse();
    }

    @Test
    @DisplayName("이미 읽어 둔 기사 계약 엔티티가 있어도 활성 확인은 그 뒤 DB에 쓰인 상태를 본다")
    void activeCheckReadsDatabaseStateInsteadOfLoadedEntity() {
        long id = insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");
        entityManager.find(TechnicianJpaEntity.class, id);
        entityManager
                .createNativeQuery("update technician set status = 'DEACTIVATED' where id = ?")
                .setParameter(1, id)
                .executeUpdate();

        assertThat(adapter.existsActive(ORGANIZATION_ID, new TechnicianId(id))).isFalse();
    }

    private long insertTechnician(OrganizationId organizationId, AccountId accountId, String status) {
        entityManager
                .createNativeQuery("""
                        insert into technician
                            (company_id, member_id, status, status_changed_at, joined_at, updated_at, created_at)
                        values (?, ?, ?, ?, ?, ?, current_timestamp)
                        """)
                .setParameter(1, organizationId.value())
                .setParameter(2, accountId.value())
                .setParameter(3, status)
                .setParameter(4, CHANGED_AT)
                .setParameter(5, CREATED_AT)
                .setParameter(6, CHANGED_AT)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("select max(id) from technician")
                        .getSingleResult())
                .longValue();
    }
}
