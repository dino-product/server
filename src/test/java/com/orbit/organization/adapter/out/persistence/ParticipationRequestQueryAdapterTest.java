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
import com.orbit.organization.domain.ParticipationRequestId;
import com.orbit.organization.domain.ParticipationRequestStatus;
import com.orbit.organization.domain.ParticipationType;
import com.orbit.support.PersistenceAdapterTestSupport;

/** 처리 결과가 여러 가지인 요청을 한 번에 만들기 위해 발주사와 요청 행은 SQL로 넣는다. */
@DisplayName("참여 요청 목록 조회")
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

    @Test
    @DisplayName("발주사의 승인 대기 요청만 요청 일시가 오래된 순, 같으면 식별자순으로 읽는다")
    void readsPendingRequestsOfOrganizationOldestFirst() {
        OrganizationId organization = insertOrganization("CCCCCC", "대기 발주사");
        OrganizationId other = insertOrganization("DDDDDD", "다른 발주사");
        long latest = insertRequest(organization, ACCOUNT_ID, "STAFF", null, "PENDING", null, THIRD, null);
        long sameTimeFirst =
                insertRequest(organization, OTHER_ACCOUNT_ID, "TECHNICIAN", null, "PENDING", null, FIRST, null);
        long sameTimeSecond =
                insertRequest(organization, new AccountId(9L), "STAFF", null, "PENDING", null, FIRST, null);
        insertRequest(organization, new AccountId(10L), "STAFF", "STAFF", "APPROVED", null, FIRST, SECOND);
        insertRequest(organization, new AccountId(11L), "STAFF", null, "REJECTED", null, FIRST, SECOND);
        insertRequest(organization, new AccountId(12L), "STAFF", null, "CANCELED", null, FIRST, null);
        insertRequest(other, ACCOUNT_ID, "STAFF", null, "PENDING", null, FIRST, null);

        assertThat(adapter.listPending(organization))
                .extracting(
                        request -> request.id().orElseThrow().value(),
                        request -> request.accountId(),
                        request -> request.requestedAt())
                .containsExactly(
                        tuple(sameTimeFirst, OTHER_ACCOUNT_ID, FIRST),
                        tuple(sameTimeSecond, new AccountId(9L), FIRST),
                        tuple(latest, ACCOUNT_ID, THIRD));
    }

    @Test
    @DisplayName("승인 대기 요청이 없으면 빈 목록이다")
    void returnsEmptyPendingList() {
        assertThat(adapter.listPending(insertOrganization("EEEEEE", "빈 발주사"))).isEmpty();
    }

    @Test
    @DisplayName("거절된 요청의 거절 사유를 읽는다")
    void readsRejectReasonOfRejectedRequest() {
        OrganizationId organization = insertOrganization("FFFFFF", "거절 발주사");
        long rejected = insertRequest(organization, ACCOUNT_ID, "STAFF", null, "REJECTED", "인원 마감", FIRST, SECOND);

        assertThat(adapter.findRejectReason(new ParticipationRequestId(rejected)))
                .contains("인원 마감");
    }

    @Test
    @DisplayName("사유 없이 거절됐거나 거절되지 않은 요청, 없는 요청의 거절 사유는 빈 값이다")
    void findsNoRejectReasonOtherwise() {
        OrganizationId organization = insertOrganization("GGGGGG", "다른 결과 발주사");
        long withoutReason = insertRequest(organization, ACCOUNT_ID, "STAFF", null, "REJECTED", null, FIRST, SECOND);
        long approved =
                insertRequest(organization, OTHER_ACCOUNT_ID, "STAFF", "STAFF", "APPROVED", "남은 값", FIRST, SECOND);
        long pending = insertRequest(organization, new AccountId(9L), "STAFF", null, "PENDING", null, FIRST, null);

        assertThat(adapter.findRejectReason(new ParticipationRequestId(withoutReason)))
                .isEmpty();
        assertThat(adapter.findRejectReason(new ParticipationRequestId(approved)))
                .isEmpty();
        assertThat(adapter.findRejectReason(new ParticipationRequestId(pending)))
                .isEmpty();
        assertThat(adapter.findRejectReason(new ParticipationRequestId(pending + 1_000)))
                .isEmpty();
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

    private long insertRequest(
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
                            (request_channel, company_id, member_id, desired_type, confirmed_type, status,
                             reject_reason, requested_at, decided_at, decided_by_membership_id, created_at, updated_at)
                        values ('COMPANY_CODE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, organizationId.value())
                .setParameter(2, accountId.value())
                .setParameter(3, desiredType)
                .setParameter(4, confirmedType)
                .setParameter(5, status)
                .setParameter(6, rejectReason)
                .setParameter(7, requestedAt)
                .setParameter(8, decidedAt)
                .setParameter(9, decidedAt == null ? null : 3L)
                .setParameter(10, requestedAt)
                .setParameter(11, decidedAt == null ? requestedAt : decidedAt)
                .executeUpdate();
        return lastId("participation_requests");
    }

    private long lastId(String table) {
        return ((Number) entityManager
                        .createNativeQuery("select max(id) from " + table)
                        .getSingleResult())
                .longValue();
    }
}
