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

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.support.PersistenceTestConfiguration;

/** 승인·거절 유즈케이스가 아직 없어 처리된 요청 행은 SQL로 넣는다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("참여 요청 저장")
class ParticipationRequestPersistenceAdapterTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-06T00:00:00.123456Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(11L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final AccountId OTHER_ACCOUNT_ID = new AccountId(8L);

    @Autowired
    private SpringDataParticipationRequestRepository repository;

    @Autowired
    private EntityManager entityManager;

    private ParticipationRequestPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ParticipationRequestPersistenceAdapter(repository);
    }

    @Test
    @DisplayName("새 요청을 저장하면 식별자와 함께 승인 대기 상태로 복원된다")
    void savesAndRestoresPendingRequest() {
        ParticipationRequest saved = adapter.save(
                ParticipationRequest.request(ORGANIZATION_ID, ACCOUNT_ID, ParticipationType.TECHNICIAN, REQUESTED_AT));
        repository.flush();
        entityManager.clear();

        assertThat(adapter.findPending(ORGANIZATION_ID, ACCOUNT_ID)).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(saved.id()).isPresent();
            assertThat(found.desiredType()).isEqualTo(ParticipationType.TECHNICIAN);
            assertThat(found.status()).isEqualTo(ParticipationRequestStatus.PENDING);
            assertThat(found.confirmedType()).isEmpty();
            assertThat(found.decidedAt()).isEmpty();
            assertThat(found.requestedAt()).isEqualTo(REQUESTED_AT);
        });
    }

    @Test
    @DisplayName("승인 대기 요청은 그 계정·발주사의 것만 찾고 처리된 요청은 보지 않는다")
    void findsOnlyPendingRequestOfAccountInOrganization() {
        insertDecided(ORGANIZATION_ID, ACCOUNT_ID, "REJECTED");
        adapter.save(
                ParticipationRequest.request(OTHER_ORGANIZATION_ID, ACCOUNT_ID, ParticipationType.STAFF, REQUESTED_AT));
        adapter.save(
                ParticipationRequest.request(ORGANIZATION_ID, OTHER_ACCOUNT_ID, ParticipationType.STAFF, REQUESTED_AT));

        assertThat(adapter.findPending(ORGANIZATION_ID, ACCOUNT_ID)).isEmpty();
        assertThat(adapter.findPending(OTHER_ORGANIZATION_ID, ACCOUNT_ID)).isPresent();
    }

    @Test
    @DisplayName("잠가 읽은 요청을 취소해 저장하면 상태만 바뀌고 요청 시각은 그대로다")
    void savesCanceledRequest() {
        ParticipationRequest saved = adapter.save(
                ParticipationRequest.request(ORGANIZATION_ID, ACCOUNT_ID, ParticipationType.STAFF, REQUESTED_AT));
        repository.flush();
        entityManager.clear();

        ParticipationRequest locked =
                adapter.findByIdForUpdate(saved.id().orElseThrow()).orElseThrow();
        locked.cancel();
        adapter.save(locked);
        repository.flush();
        entityManager.clear();

        ParticipationRequest found =
                adapter.findByIdForUpdate(saved.id().orElseThrow()).orElseThrow();
        assertThat(found.status()).isEqualTo(ParticipationRequestStatus.CANCELED);
        assertThat(found.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(found.decidedAt()).isEmpty();
        assertThat(adapter.findPending(ORGANIZATION_ID, ACCOUNT_ID)).isEmpty();
    }

    private void insertDecided(OrganizationId organizationId, AccountId accountId, String status) {
        entityManager
                .createNativeQuery("""
                        insert into participation_requests
                            (company_id, member_id, desired_type, status,
                             requested_at, decided_at, created_at, updated_at)
                        values (?, ?, 'STAFF', ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, organizationId.value())
                .setParameter(2, accountId.value())
                .setParameter(3, status)
                .setParameter(4, REQUESTED_AT)
                .setParameter(5, REQUESTED_AT)
                .setParameter(6, REQUESTED_AT)
                .setParameter(7, REQUESTED_AT)
                .executeUpdate();
    }
}
