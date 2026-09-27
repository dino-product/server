package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationIdentityPort;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.PersonnelTypeInUseException;
import com.orbit.organization.application.port.out.PersonnelTypeNameConflictException;
import com.orbit.organization.application.port.out.StaffTypeRepository;
import com.orbit.organization.application.port.out.StaffTypeUsagePort;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.StaffType;
import com.orbit.organization.domain.TypeColor;
import com.orbit.support.IntegrationTestSupport;

class StaffTypePersistenceAdapterTest extends IntegrationTestSupport {
    private static final Instant JOINED_AT = Instant.parse("2026-09-28T00:00:00Z");

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private OrganizationIdentityPort identities;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private StaffTypeRepository staffTypes;

    @Autowired
    private StaffTypeUsagePort usage;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allocatesDistinctIdsAndRestoresTypesInCreationOrderWithStoredState() {
        var organizationId = saveOrganization();
        var first = type(organizationId, "상담", 2);
        var second = type(organizationId, "팀장", 7);
        second.deactivate();

        transaction().executeWithoutResult(status -> {
            staffTypes.save(second);
            staffTypes.save(first);
        });

        assertThat(first.id()).isNotEqualTo(second.id());
        transaction().executeWithoutResult(status -> {
            var restored = staffTypes.findAllByOrganization(organizationId);
            assertThat(restored).extracting(StaffType::id).containsExactly(first.id(), second.id());
            assertThat(restored.get(0).name().value()).isEqualTo("상담");
            assertThat(restored.get(0).color().value()).isEqualTo(2);
            assertThat(restored.get(0).active()).isTrue();
            assertThat(restored.get(1).name().value()).isEqualTo("팀장");
            assertThat(restored.get(1).color().value()).isEqualTo(7);
            assertThat(restored.get(1).active()).isFalse();
        });
    }

    @Test
    void updatesNameColorAndActiveStateWithoutChangingIdentity() {
        var organizationId = saveOrganization();
        var type = type(organizationId, "상담", 2);
        transaction().executeWithoutResult(status -> staffTypes.save(type));

        transaction().executeWithoutResult(status -> {
            var loaded = staffTypes.findByIdForUpdate(type.id()).orElseThrow();
            loaded.rename(new PersonnelTypeName("팀장"));
            loaded.changeColor(new TypeColor(8));
            loaded.deactivate();
            staffTypes.save(loaded);
        });

        transaction().executeWithoutResult(status -> {
            var restored = staffTypes.findByIdForUpdate(type.id()).orElseThrow();
            assertThat(restored.organizationId()).isEqualTo(organizationId);
            assertThat(restored.name().value()).isEqualTo("팀장");
            assertThat(restored.color().value()).isEqualTo(8);
            assertThat(restored.active()).isFalse();
        });
    }

    @Test
    void nameLookupIncludesInactiveTypesAndExcludesTheEditedType() {
        var organizationId = saveOrganization();
        var otherOrganizationId = saveOrganization();
        var type = type(organizationId, "상담", 2);
        type.deactivate();
        var name = new PersonnelTypeName("상담");
        transaction().executeWithoutResult(status -> staffTypes.save(type));

        assertThat(staffTypes.existsByOrganizationAndName(organizationId, name, null))
                .isTrue();
        assertThat(staffTypes.existsByOrganizationAndName(organizationId, name, type.id()))
                .isFalse();
        assertThat(staffTypes.existsByOrganizationAndName(otherOrganizationId, name, null))
                .isFalse();
    }

    @Test
    void duplicateNamesConflictOnlyWithinTheSameOrganization() {
        var organizationId = saveOrganization();
        var otherOrganizationId = saveOrganization();
        var first = type(organizationId, "상담", 2);
        var duplicate = type(organizationId, "상담", 3);
        var otherOrganization = type(otherOrganizationId, "상담", 4);
        first.deactivate();
        transaction().executeWithoutResult(status -> staffTypes.save(first));
        transaction().executeWithoutResult(status -> staffTypes.save(otherOrganization));

        var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> staffTypes.save(duplicate)));

        assertThat(failure).isInstanceOf(PersonnelTypeNameConflictException.class);
        assertSqlState(failure, "23505");
        assertThat(rootCause(failure).getMessage()).contains("uq_staff_type_organization_name");
    }

    @Test
    void renamingToAnotherTypesNameMapsTheDatabaseConflict() {
        var organizationId = saveOrganization();
        var first = type(organizationId, "상담", 2);
        var second = type(organizationId, "팀장", 3);
        transaction().executeWithoutResult(status -> {
            staffTypes.save(first);
            staffTypes.save(second);
        });

        var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> {
            var loaded = staffTypes.findByIdForUpdate(second.id()).orElseThrow();
            loaded.rename(first.name());
            staffTypes.save(loaded);
        }));

        assertThat(failure).isInstanceOf(PersonnelTypeNameConflictException.class);
        assertSqlState(failure, "23505");
        assertThat(rootCause(failure).getMessage()).contains("uq_staff_type_organization_name");
        assertThat(staffTypes.findAllByOrganization(organizationId))
                .extracting(type -> type.name().value())
                .containsExactly("상담", "팀장");
    }

    @Test
    void concurrentInsertAfterBothNameChecksMapsTheDatabaseConflict() throws Exception {
        var organizationId = saveOrganization();
        var name = new PersonnelTypeName("상담");
        var first = type(organizationId, name.value(), 2);
        var second = type(organizationId, name.value(), 3);
        var firstFlushed = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondChecked = new CountDownLatch(1);
        var secondSawNoName = new AtomicBoolean();
        var secondBackendPid = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstTransaction = executor.submit(() -> transaction().executeWithoutResult(status -> {
                assertThat(staffTypes.existsByOrganizationAndName(organizationId, name, null))
                        .isFalse();
                staffTypes.save(first);
                firstFlushed.countDown();
                awaitRelease(releaseFirst);
            }));
            try {
                assertThat(firstFlushed.await(10, TimeUnit.SECONDS)).isTrue();
                var secondTransaction =
                        executor.submit(() -> catchThrowable(() -> transaction().executeWithoutResult(status -> {
                            secondSawNoName.set(!staffTypes.existsByOrganizationAndName(organizationId, name, null));
                            secondBackendPid.set(jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class));
                            secondChecked.countDown();
                            staffTypes.save(second);
                        })));
                assertThat(secondChecked.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(secondSawNoName).isTrue();
                assertThat(awaitsTransactionLock(secondBackendPid.get())).isTrue();
                releaseFirst.countDown();
                firstTransaction.get(10, TimeUnit.SECONDS);

                var failure = secondTransaction.get(10, TimeUnit.SECONDS);
                assertThat(failure).isInstanceOf(PersonnelTypeNameConflictException.class);
                assertSqlState(failure, "23505");
                assertThat(rootCause(failure).getMessage()).contains("uq_staff_type_organization_name");
            } finally {
                releaseFirst.countDown();
                firstTransaction.get(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void detectsUsageByActiveAndInactiveMembershipsAndRestrictsDeletion() {
        var organizationId = saveOrganization();
        var activeType = type(organizationId, "상담", 2);
        var inactiveType = type(organizationId, "팀장", 3);
        var unused = type(organizationId, "관리", 4);
        transaction().executeWithoutResult(status -> {
            staffTypes.save(activeType);
            staffTypes.save(inactiveType);
            staffTypes.save(unused);
            memberships.save(Membership.reconstitute(
                    identities.nextMembershipId(),
                    organizationId,
                    new AuthAccountId(801L),
                    activeType.id(),
                    JOINED_AT,
                    true));
            memberships.save(Membership.reconstitute(
                    identities.nextMembershipId(),
                    organizationId,
                    new AuthAccountId(802L),
                    inactiveType.id(),
                    JOINED_AT,
                    false));
        });

        assertThat(usage.isAssigned(activeType.id())).isTrue();
        assertThat(usage.isAssigned(inactiveType.id())).isTrue();
        assertThat(usage.isAssigned(unused.id())).isFalse();

        var failure = catchThrowable(
                () -> transaction().executeWithoutResult(status -> staffTypes.delete(inactiveType.id())));
        assertThat(failure).isInstanceOf(PersonnelTypeInUseException.class);
        assertSqlState(failure, "23503");
        assertThat(rootCause(failure).getMessage()).contains("fk_membership_staff_type");

        transaction().executeWithoutResult(status -> staffTypes.delete(unused.id()));
        assertThat(staffTypes.findAllByOrganization(organizationId))
                .extracting(StaffType::id)
                .doesNotContain(unused.id());
        transaction().executeWithoutResult(status -> staffTypes.save(type(organizationId, "관리", 5)));
    }

    @Test
    void forUpdateReadHoldsTheTypeRowUntilTransactionEnds() throws Exception {
        var organizationId = saveOrganization();
        var type = type(organizationId, "상담", 2);
        transaction().executeWithoutResult(status -> staffTypes.save(type));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var holder = executor.submit(() -> transaction().executeWithoutResult(status -> {
                assertThat(staffTypes.findByIdForUpdate(type.id())).isPresent();
                locked.countDown();
                awaitRelease(release);
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL lock_timeout = '500ms'");
                    staffTypes.findByIdForUpdate(type.id());
                }));
                assertSqlState(failure, "55P03");
            } finally {
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
            }
        }
    }

    private OrganizationId saveOrganization() {
        var organizationId = identities.nextOrganizationId();
        var membership = Membership.create(
                identities.nextMembershipId(),
                organizationId,
                new AuthAccountId(organizationId.value() + 1000),
                null,
                JOINED_AT);
        var organization = Organization.create(
                organizationId,
                new OrganizationName("유형 회사"),
                null,
                new CompanyCode("%08d".formatted(organizationId.value())),
                membership);
        transaction().executeWithoutResult(status -> {
            organizations.save(organization);
            memberships.save(membership);
        });
        return organizationId;
    }

    private StaffType type(OrganizationId organizationId, String name, int color) {
        return StaffType.create(staffTypes.nextId(), organizationId, new PersonnelTypeName(name), new TypeColor(color));
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

    private boolean awaitsTransactionLock(int backendPid) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            var waitEvent = jdbcTemplate.queryForObject(
                    "select wait_event from pg_stat_activity where pid = ?", String.class, backendPid);
            if ("transactionid".equals(waitEvent)) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }
}
