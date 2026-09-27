package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.organization.application.port.out.CompanyCodeConflictException;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationIdentityPort;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.StaffTypeRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.StaffType;
import com.orbit.organization.domain.TypeColor;
import com.orbit.support.IntegrationTestSupport;

class OrganizationPersistenceAdapterTest extends IntegrationTestSupport {
    private static final Instant JOINED_AT = Instant.parse("2026-09-28T00:00:00Z");

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private OrganizationIdentityPort identities;

    @Autowired
    private StaffTypeRepository staffTypes;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesOrganizationAndOwnerMembershipInOneCommittedTransaction() {
        var organizationId = identities.nextOrganizationId();
        var membershipId = identities.nextMembershipId();
        var accountId = new AuthAccountId(101L);
        var code = codeFor(organizationId);
        var membership = Membership.create(membershipId, organizationId, accountId, null, JOINED_AT);
        var organization =
                Organization.create(organizationId, new OrganizationName("저장된 회사"), Industry.OTHER, code, membership);
        var inactiveType =
                StaffType.create(staffTypes.nextId(), organizationId, new PersonnelTypeName("상담"), new TypeColor(1));
        var inactiveMember = Membership.reconstitute(
                identities.nextMembershipId(),
                organizationId,
                new AuthAccountId(105L),
                inactiveType.id(),
                JOINED_AT,
                false);

        transaction().executeWithoutResult(status -> {
            organizations.save(organization);
            memberships.save(membership);
            staffTypes.save(inactiveType);
            memberships.save(inactiveMember);
            entityManager.flush();
        });

        transaction().executeWithoutResult(status -> {
            var restoredOrganization = organizations.findById(organizationId).orElseThrow();
            var restoredMembership = memberships
                    .findByOrganizationAndAccount(organizationId, accountId)
                    .orElseThrow();
            assertThat(restoredOrganization.code()).isEqualTo(code);
            assertThat(organizations.existsByCode(code)).isTrue();
            assertThat(restoredOrganization.industry()).isEqualTo(Industry.OTHER);
            assertThat(restoredOrganization.ownerMembershipId()).isEqualTo(membershipId);
            assertThat(restoredMembership.id()).isEqualTo(membershipId);
            assertThat(restoredMembership.typeId()).isNull();
            assertThat(restoredMembership.active()).isTrue();
            assertThat(restoredMembership.joinedAt()).isEqualTo(JOINED_AT);
            var restoredInactive = memberships
                    .findByOrganizationAndAccount(organizationId, inactiveMember.authAccountId())
                    .orElseThrow();
            assertThat(restoredInactive.typeId()).isEqualTo(inactiveMember.typeId());
            assertThat(restoredInactive.active()).isFalse();
        });
    }

    @Test
    void updatesOrganizationDetailsAfterInsertion() {
        var pair = newPair(new AuthAccountId(106L));
        transaction().executeWithoutResult(status -> savePair(pair));

        transaction().executeWithoutResult(status -> {
            var organization = organizations.findById(pair.organization.id()).orElseThrow();
            organization.updateDetails(new OrganizationName("수정된 회사"), Industry.OTHER);
            organizations.update(organization);
        });

        transaction().executeWithoutResult(status -> {
            var restored = organizations.findById(pair.organization.id()).orElseThrow();
            assertThat(restored.name().value()).isEqualTo("수정된 회사");
            assertThat(restored.industry()).isEqualTo(Industry.OTHER);
        });
    }

    @Test
    void forUpdateReadHoldsTheOrganizationRowUntilTransactionEnds() throws Exception {
        var pair = newPair(new AuthAccountId(111L));
        transaction().executeWithoutResult(status -> savePair(pair));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var holder = executor.submit(() -> transaction().executeWithoutResult(status -> {
                assertThat(organizations.findByIdForUpdate(pair.organization.id()))
                        .isPresent();
                locked.countDown();
                awaitRelease(release);
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL lock_timeout = '500ms'");
                    organizations.findByIdForUpdate(pair.organization.id());
                }));
                assertSqlState(failure, "55P03");
            } finally {
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void restoresStoredLegacyNameAndCodeWithoutApplyingCurrentInputRules() {
        var organizationId = identities.nextOrganizationId();
        var membershipId = identities.nextMembershipId();
        var accountId = new AuthAccountId(110L);

        transaction().executeWithoutResult(status -> {
            entityManager
                    .createNativeQuery(
                            "insert into organization (id, name, code, owner_membership_id) values (?1, ?2, ?3, ?4)")
                    .setParameter(1, organizationId.value())
                    .setParameter(2, " A ")
                    .setParameter(3, "legacy01")
                    .setParameter(4, membershipId.value())
                    .executeUpdate();
            entityManager
                    .createNativeQuery(
                            "insert into membership (id, organization_id, auth_account_id, joined_at, active) "
                                    + "values (?1, ?2, ?3, ?4, ?5)")
                    .setParameter(1, membershipId.value())
                    .setParameter(2, organizationId.value())
                    .setParameter(3, accountId.value())
                    .setParameter(4, JOINED_AT)
                    .setParameter(5, true)
                    .executeUpdate();
        });

        transaction().executeWithoutResult(status -> {
            var restored = organizations.findById(organizationId).orElseThrow();
            assertThat(restored.name().value()).isEqualTo(" A ");
            assertThat(restored.code().value()).isEqualTo("legacy01");
            assertThat(restored.ownerMembershipId()).isEqualTo(membershipId);
        });
    }

    @Test
    void rejectsMissingOwnerOnlyWhenTransactionCommits() {
        var organizationId = identities.nextOrganizationId();
        var organization = Organization.reconstitute(
                organizationId,
                new OrganizationName("소속 없는 회사"),
                null,
                codeFor(organizationId),
                identities.nextMembershipId());
        var flushed = new AtomicBoolean();

        var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> {
            organizations.save(organization);
            entityManager.flush();
            flushed.set(true);
        }));

        assertThat(flushed).isTrue();
        assertSqlState(failure, "23503");
    }

    @Test
    void rejectsMembershipForMissingOrganizationOnlyWhenTransactionCommits() {
        var missingOrganizationId = identities.nextOrganizationId();
        var membership = Membership.reconstitute(
                identities.nextMembershipId(), missingOrganizationId, new AuthAccountId(107L), null, JOINED_AT, true);
        var flushed = new AtomicBoolean();

        var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> {
            memberships.save(membership);
            entityManager.flush();
            flushed.set(true);
        }));

        assertThat(flushed).isTrue();
        assertSqlState(failure, "23503");
    }

    @Test
    void rejectsDuplicateCompanyCodes() {
        var first = newPair(new AuthAccountId(102L));
        var second = newPair(new AuthAccountId(103L), first.organization.code());

        var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> {
            savePair(first);
            savePair(second);
            organizations.flush();
        }));

        assertThat(failure).isInstanceOf(CompanyCodeConflictException.class);
        assertSqlState(failure, "23505");
        assertThat(rootCause(failure).getMessage()).contains("uq_organization_code");
    }

    @Test
    void rejectsDuplicateMembershipForTheSameOrganizationAndAccount() {
        var pair = newPair(new AuthAccountId(104L));
        var duplicate = Membership.create(
                identities.nextMembershipId(),
                pair.organization.id(),
                pair.membership.authAccountId(),
                null,
                JOINED_AT);

        var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> {
            savePair(pair);
            memberships.save(duplicate);
            organizations.flush();
        }));

        assertThat(failure).isNotInstanceOf(CompanyCodeConflictException.class);
        assertSqlState(failure, "23505");
    }

    @Test
    void allowsDifferentAccountsInOneOrganizationAndOneAccountInDifferentOrganizations() {
        var accountId = new AuthAccountId(108L);
        var first = newPair(accountId);
        var second = newPair(accountId);
        var anotherAccount = Membership.create(
                identities.nextMembershipId(), first.organization.id(), new AuthAccountId(109L), null, JOINED_AT);

        transaction().executeWithoutResult(status -> {
            savePair(first);
            savePair(second);
            memberships.save(anotherAccount);
        });

        transaction().executeWithoutResult(status -> {
            assertThat(memberships.findByOrganizationAndAccount(first.organization.id(), accountId))
                    .isPresent();
            assertThat(memberships.findByOrganizationAndAccount(second.organization.id(), accountId))
                    .isPresent();
            assertThat(memberships.findByOrganizationAndAccount(
                            first.organization.id(), anotherAccount.authAccountId()))
                    .isPresent();
        });
    }

    @Test
    void allocatesDifferentOrganizationAndMembershipIds() {
        assertThat(identities.nextOrganizationId()).isNotEqualTo(identities.nextOrganizationId());
        assertThat(identities.nextMembershipId()).isNotEqualTo(identities.nextMembershipId());
    }

    private Pair newPair(AuthAccountId accountId) {
        var organizationId = identities.nextOrganizationId();
        return newPair(accountId, codeFor(organizationId), organizationId);
    }

    private Pair newPair(AuthAccountId accountId, CompanyCode code) {
        return newPair(accountId, code, identities.nextOrganizationId());
    }

    private Pair newPair(AuthAccountId accountId, CompanyCode code, OrganizationId organizationId) {
        var membership = Membership.create(identities.nextMembershipId(), organizationId, accountId, null, JOINED_AT);
        var organization = Organization.create(organizationId, new OrganizationName("새 회사"), null, code, membership);
        return new Pair(organization, membership);
    }

    private void savePair(Pair pair) {
        organizations.save(pair.organization);
        memberships.save(pair.membership);
    }

    private CompanyCode codeFor(OrganizationId id) {
        return new CompanyCode("%08d".formatted(id.value()));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private void assertSqlState(Throwable failure, String expectedState) {
        assertThat(failure).isNotNull();
        assertThat(rootCause(failure)).isInstanceOf(SQLException.class);
        assertThat(((SQLException) rootCause(failure)).getSQLState()).isEqualTo(expectedState);
    }

    private Throwable rootCause(Throwable failure) {
        var cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("row lock holder was not released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("row lock holder was interrupted", interrupted);
        }
    }

    private record Pair(Organization organization, Membership membership) {}
}
