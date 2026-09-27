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
import com.orbit.organization.application.port.out.PersonnelTypeNameConflictException;
import com.orbit.organization.application.port.out.StaffTypeRepository;
import com.orbit.organization.application.port.out.TechnicianTypeRepository;
import com.orbit.organization.application.port.out.TechnicianTypeUsagePort;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.StaffType;
import com.orbit.organization.domain.TechnicianType;
import com.orbit.organization.domain.TypeColor;
import com.orbit.support.IntegrationTestSupport;

class TechnicianTypePersistenceAdapterTest extends IntegrationTestSupport {
    private static final Instant JOINED_AT = Instant.parse("2026-09-28T00:00:00Z");

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private OrganizationIdentityPort identities;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private TechnicianTypeRepository technicianTypes;

    @Autowired
    private StaffTypeRepository staffTypes;

    @Autowired
    private TechnicianTypeUsagePort usage;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allocatesDistinctIdsAndRestoresTypesInCreationOrderWithStoredState() {
        var organizationId = saveOrganization();
        var first = type(organizationId, "전기", 2);
        var second = type(organizationId, "냉방", 7);
        second.deactivate();

        transaction().executeWithoutResult(status -> {
            technicianTypes.save(second);
            technicianTypes.save(first);
        });

        assertThat(first.id()).isNotEqualTo(second.id());
        transaction().executeWithoutResult(status -> {
            var restored = technicianTypes.findAllByOrganization(organizationId);
            assertThat(restored).extracting(TechnicianType::id).containsExactly(first.id(), second.id());
            assertThat(restored.get(0).name().value()).isEqualTo("전기");
            assertThat(restored.get(0).color().value()).isEqualTo(2);
            assertThat(restored.get(0).active()).isTrue();
            assertThat(restored.get(1).name().value()).isEqualTo("냉방");
            assertThat(restored.get(1).color().value()).isEqualTo(7);
            assertThat(restored.get(1).active()).isFalse();
        });
    }

    @Test
    void updatesNameColorAndActiveStateWithoutChangingIdentity() {
        var organizationId = saveOrganization();
        var type = type(organizationId, "전기", 2);
        transaction().executeWithoutResult(status -> technicianTypes.save(type));

        transaction().executeWithoutResult(status -> {
            var loaded = technicianTypes.findByIdForUpdate(type.id()).orElseThrow();
            loaded.rename(new PersonnelTypeName("냉방"));
            loaded.changeColor(new TypeColor(8));
            loaded.deactivate();
            technicianTypes.save(loaded);
        });

        transaction().executeWithoutResult(status -> {
            var restored = technicianTypes.findByIdForUpdate(type.id()).orElseThrow();
            assertThat(restored.organizationId()).isEqualTo(organizationId);
            assertThat(restored.name().value()).isEqualTo("냉방");
            assertThat(restored.color().value()).isEqualTo(8);
            assertThat(restored.active()).isFalse();
        });
    }

    @Test
    void nameLookupIncludesInactiveTypesAndExcludesTheEditedType() {
        var organizationId = saveOrganization();
        var otherOrganizationId = saveOrganization();
        var type = type(organizationId, "전기", 2);
        type.deactivate();
        var name = new PersonnelTypeName("전기");
        transaction().executeWithoutResult(status -> technicianTypes.save(type));

        assertThat(technicianTypes.existsByOrganizationAndName(organizationId, name, null))
                .isTrue();
        assertThat(technicianTypes.existsByOrganizationAndName(organizationId, name, type.id()))
                .isFalse();
        assertThat(technicianTypes.existsByOrganizationAndName(otherOrganizationId, name, null))
                .isFalse();
    }

    @Test
    void duplicateNamesConflictOnlyWithinTheSameOrganization() {
        var organizationId = saveOrganization();
        var otherOrganizationId = saveOrganization();
        var first = type(organizationId, "전기", 2);
        var duplicate = type(organizationId, "전기", 3);
        var otherOrganization = type(otherOrganizationId, "전기", 4);
        first.deactivate();
        transaction().executeWithoutResult(status -> technicianTypes.save(first));
        transaction().executeWithoutResult(status -> technicianTypes.save(otherOrganization));

        var failure =
                catchThrowable(() -> transaction().executeWithoutResult(status -> technicianTypes.save(duplicate)));

        assertThat(failure).isInstanceOf(PersonnelTypeNameConflictException.class);
        assertSqlState(failure, "23505");
        assertThat(rootCause(failure).getMessage()).contains("uq_technician_type_organization_name");
    }

    @Test
    void renamingToAnotherTypesNameMapsTheDatabaseConflict() {
        var organizationId = saveOrganization();
        var first = type(organizationId, "전기", 2);
        var second = type(organizationId, "냉방", 3);
        transaction().executeWithoutResult(status -> {
            technicianTypes.save(first);
            technicianTypes.save(second);
        });

        var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> {
            var loaded = technicianTypes.findByIdForUpdate(second.id()).orElseThrow();
            loaded.rename(first.name());
            technicianTypes.save(loaded);
        }));

        assertThat(failure).isInstanceOf(PersonnelTypeNameConflictException.class);
        assertSqlState(failure, "23505");
        assertThat(rootCause(failure).getMessage()).contains("uq_technician_type_organization_name");
        assertThat(technicianTypes.findAllByOrganization(organizationId))
                .extracting(type -> type.name().value())
                .containsExactly("전기", "냉방");
    }

    @Test
    void concurrentInsertAfterBothNameChecksMapsTheDatabaseConflict() throws Exception {
        var organizationId = saveOrganization();
        var name = new PersonnelTypeName("전기");
        var first = type(organizationId, name.value(), 2);
        var second = type(organizationId, name.value(), 3);
        var firstFlushed = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondChecked = new CountDownLatch(1);
        var secondSawNoName = new AtomicBoolean();
        var secondBackendPid = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstTransaction = executor.submit(() -> transaction().executeWithoutResult(status -> {
                assertThat(technicianTypes.existsByOrganizationAndName(organizationId, name, null))
                        .isFalse();
                technicianTypes.save(first);
                firstFlushed.countDown();
                awaitRelease(releaseFirst);
            }));
            try {
                assertThat(firstFlushed.await(10, TimeUnit.SECONDS)).isTrue();
                var secondTransaction =
                        executor.submit(() -> catchThrowable(() -> transaction().executeWithoutResult(status -> {
                            secondSawNoName.set(
                                    !technicianTypes.existsByOrganizationAndName(organizationId, name, null));
                            secondBackendPid.set(jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class));
                            secondChecked.countDown();
                            technicianTypes.save(second);
                        })));
                assertThat(secondChecked.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(secondSawNoName).isTrue();
                assertThat(awaitsTransactionLock(secondBackendPid.get())).isTrue();
                releaseFirst.countDown();
                firstTransaction.get(10, TimeUnit.SECONDS);

                var failure = secondTransaction.get(10, TimeUnit.SECONDS);
                assertThat(failure).isInstanceOf(PersonnelTypeNameConflictException.class);
                assertSqlState(failure, "23505");
                assertThat(rootCause(failure).getMessage()).contains("uq_technician_type_organization_name");
            } finally {
                releaseFirst.countDown();
                firstTransaction.get(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void reportsUnusedUntilTechnicianContractsAreImplementedAndAllowsNameReuseAfterDeletion() {
        var organizationId = saveOrganization();
        var type = type(organizationId, "전기", 2);
        transaction().executeWithoutResult(status -> technicianTypes.save(type));

        assertThat(usage.isAssigned(type.id())).isFalse();
        transaction().executeWithoutResult(status -> technicianTypes.delete(type.id()));
        assertThat(technicianTypes.findAllByOrganization(organizationId)).isEmpty();
        transaction().executeWithoutResult(status -> technicianTypes.save(type(organizationId, "전기", 3)));
        assertThat(technicianTypes.findAllByOrganization(organizationId))
                .extracting(TechnicianType::name)
                .containsExactly(new PersonnelTypeName("전기"));
    }

    @Test
    void staffAndTechnicianTypesHaveIndependentNameSpaces() {
        var organizationId = saveOrganization();
        var staffType =
                StaffType.create(staffTypes.nextId(), organizationId, new PersonnelTypeName("전기"), new TypeColor(2));
        var technicianType = type(organizationId, "전기", 3);

        transaction().executeWithoutResult(status -> {
            staffTypes.save(staffType);
            technicianTypes.save(technicianType);
        });

        assertThat(staffTypes.findAllByOrganization(organizationId))
                .extracting(StaffType::name)
                .containsExactly(new PersonnelTypeName("전기"));
        assertThat(technicianTypes.findAllByOrganization(organizationId))
                .extracting(TechnicianType::name)
                .containsExactly(new PersonnelTypeName("전기"));
    }

    @Test
    void rejectsTypeForMissingOrganizationWhenTransactionCommits() {
        var missingOrganizationId = identities.nextOrganizationId();
        var type = type(missingOrganizationId, "전기", 2);
        var flushed = new AtomicBoolean();

        var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> {
            technicianTypes.save(type);
            flushed.set(true);
        }));

        assertThat(flushed).isTrue();
        assertSqlState(failure, "23503");
        assertThat(rootCause(failure).getMessage()).contains("fk_technician_type_organization");
    }

    @Test
    void forUpdateReadHoldsTheTypeRowUntilTransactionEnds() throws Exception {
        var organizationId = saveOrganization();
        var type = type(organizationId, "전기", 2);
        transaction().executeWithoutResult(status -> technicianTypes.save(type));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var holder = executor.submit(() -> transaction().executeWithoutResult(status -> {
                assertThat(technicianTypes.findByIdForUpdate(type.id())).isPresent();
                locked.countDown();
                awaitRelease(release);
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var failure = catchThrowable(() -> transaction().executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL lock_timeout = '500ms'");
                    technicianTypes.findByIdForUpdate(type.id());
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

    private TechnicianType type(OrganizationId organizationId, String name, int color) {
        return TechnicianType.create(
                technicianTypes.nextId(), organizationId, new PersonnelTypeName(name), new TypeColor(color));
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
