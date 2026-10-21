package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.orbit.organization.application.port.out.AccountParticipationRequest;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.support.PersistenceAdapterTestSupport;

/** 승인·거절 유즈케이스가 아직 없어 발주사와 요청 행은 SQL로 넣는다. */
@DisplayName("계정의 참여 요청 목록 조회")
class ParticipationRequestQueryAdapterTest extends PersistenceAdapterTestSupport {

    private static final Instant FIRST = Instant.parse("2026-10-01T00:00:00.123456Z");
    private static final Instant SECOND = Instant.parse("2026-10-02T00:00:00.123456Z");
    private static final Instant THIRD = Instant.parse("2026-10-03T00:00:00.123456Z");
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final AccountId OTHER_ACCOUNT_ID = new AccountId(8L);

    @Autowired
    private EntityManager entityManager;

    private ParticipationRequestQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ParticipationRequestQueryAdapter(entityManager);
    }

    @Test
    @DisplayName("보낸 요청이 없으면 빈 목록이다")
    void returnsEmptyListWithoutRequests() {
        assertThat(adapter.listByAccount(ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("모든 상태의 요청을 발주사명과 함께 요청 일시순, 같으면 식별자순으로 읽고 다른 계정의 요청은 읽지 않는다")
    void readsAllRequestsOfAccountInRequestOrder() {
        OrganizationId first = insertOrganization("AAAAAA", "첫 발주사");
        OrganizationId second = insertOrganization("BBBBBB", "둘째 발주사");
        insertRequest(second, ACCOUNT_ID, "STAFF", null, "PENDING", null, THIRD, null);
        insertRequest(first, ACCOUNT_ID, "TECHNICIAN", "STAFF", "APPROVED", null, FIRST, SECOND);
        insertRequest(second, ACCOUNT_ID, "STAFF", null, "REJECTED", "인원 마감", SECOND, THIRD);
        insertRequest(first, ACCOUNT_ID, "STAFF", null, "CANCELED", null, THIRD, null);
        insertRequest(first, OTHER_ACCOUNT_ID, "STAFF", null, "PENDING", null, FIRST, null);

        assertThat(adapter.listByAccount(ACCOUNT_ID))
                .extracting(
                        row -> row.request().organizationId(),
                        AccountParticipationRequest::organizationName,
                        row -> row.request().status(),
                        row -> row.request().requestedAt())
                .containsExactly(
                        tuple(first, "첫 발주사", ParticipationRequestStatus.APPROVED, FIRST),
                        tuple(second, "둘째 발주사", ParticipationRequestStatus.REJECTED, SECOND),
                        tuple(second, "둘째 발주사", ParticipationRequestStatus.PENDING, THIRD),
                        tuple(first, "첫 발주사", ParticipationRequestStatus.CANCELED, THIRD));
        assertThat(adapter.listByAccount(ACCOUNT_ID).getFirst().request().confirmedType())
                .contains(ParticipationType.STAFF);
        assertThat(adapter.listByAccount(ACCOUNT_ID).get(1).request().rejectReason())
                .contains("인원 마감");
    }

    private OrganizationId insertOrganization(String code, String name) {
        entityManager
                .createNativeQuery("""
                        insert into companies (code, name, industry_code, created_at, updated_at)
                        values (?, ?, 'HVAC', ?, ?)
                        """)
                .setParameter(1, code)
                .setParameter(2, name)
                .setParameter(3, FIRST)
                .setParameter(4, FIRST)
                .executeUpdate();
        return new OrganizationId(lastId("companies"));
    }

    private void insertRequest(
            OrganizationId organizationId,
            AccountId accountId,
            String desiredType,
            String confirmedType,
            String status,
            String rejectReason,
            Instant requestedAt,
            Instant decidedAt) {
        entityManager
                .createNativeQuery("""
                        insert into participation_requests
                            (company_id, member_id, desired_type, confirmed_type, status, reject_reason,
                             requested_at, decided_at, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, organizationId.value())
                .setParameter(2, accountId.value())
                .setParameter(3, desiredType)
                .setParameter(4, confirmedType)
                .setParameter(5, status)
                .setParameter(6, rejectReason)
                .setParameter(7, requestedAt)
                .setParameter(8, decidedAt)
                .setParameter(9, requestedAt)
                .setParameter(10, decidedAt == null ? requestedAt : decidedAt)
                .executeUpdate();
    }

    private long lastId(String table) {
        return ((Number) entityManager
                        .createNativeQuery("select max(id) from " + table)
                        .getSingleResult())
                .longValue();
    }
}
