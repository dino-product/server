package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.ActivateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.command.dto.CreateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.command.dto.DeactivateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.command.dto.DeleteTechnicianTypeCommand;
import com.orbit.organization.application.port.in.command.dto.UpdateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.query.dto.GetTechnicianTypesQuery;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.PersonnelTypeInUseException;
import com.orbit.organization.application.port.out.PersonnelTypeNameConflictException;
import com.orbit.organization.application.port.out.TechnicianTypeRepository;
import com.orbit.organization.application.port.out.TechnicianTypeUsagePort;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.organization.domain.OrganizationRuleViolation;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.TechnicianType;
import com.orbit.organization.domain.TechnicianTypeId;
import com.orbit.organization.domain.TypeColor;
import com.orbit.shared.error.BusinessException;

class TechnicianTypeServicesTest {
    private static final OrganizationId ORGANIZATION = new OrganizationId(11L);
    private static final AuthAccountId ACCOUNT = new AuthAccountId(9L);
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    private OrganizationRepository organizations;
    private MembershipRepository memberships;
    private FakeTechnicianTypes technicianTypes;
    private FakeUsage usage;
    private GetTechnicianTypesService get;
    private CreateTechnicianTypeService create;
    private UpdateTechnicianTypeService update;
    private ActivateTechnicianTypeService activate;
    private DeactivateTechnicianTypeService deactivate;
    private DeleteTechnicianTypeService delete;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        memberships = mock(MembershipRepository.class);
        technicianTypes = new FakeTechnicianTypes();
        usage = new FakeUsage();
        get = new GetTechnicianTypesService(organizations, memberships, technicianTypes);
        create = new CreateTechnicianTypeService(organizations, memberships, technicianTypes);
        update = new UpdateTechnicianTypeService(organizations, memberships, technicianTypes);
        activate = new ActivateTechnicianTypeService(organizations, memberships, technicianTypes);
        deactivate = new DeactivateTechnicianTypeService(organizations, memberships, technicianTypes);
        delete = new DeleteTechnicianTypeService(organizations, memberships, technicianTypes, usage);
        owner();
    }

    @Test
    void listsActiveAndInactiveTypesInCreationOrder() {
        var first = technicianTypes.put(ORGANIZATION, "전기", 2);
        var second = technicianTypes.put(ORGANIZATION, "냉방", 7);
        second.deactivate();
        technicianTypes.put(new OrganizationId(22L), "다른회사", 4);

        var types = get.get(new GetTechnicianTypesQuery(ACCOUNT.value(), ORGANIZATION.value()));

        assertThat(types).hasSize(2);
        assertThat(types.get(0).id()).isEqualTo(first.id().value());
        assertThat(types.get(0).name()).isEqualTo("전기");
        assertThat(types.get(0).color()).isEqualTo(2);
        assertThat(types.get(0).active()).isTrue();
        assertThat(types.get(1).id()).isEqualTo(second.id().value());
        assertThat(types.get(1).active()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 10})
    void createsNormalizedNamesAtValidBoundaries(int length) {
        var name = "가".repeat(length);

        var created = create.create(
                new CreateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), "  " + name + "  ", 8));

        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.name()).isEqualTo(name);
        assertThat(created.color()).isEqualTo(8);
        assertThat(created.active()).isTrue();
        assertThat(technicianTypes.saved).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 11})
    void rejectsInvalidCreateNameAfterOwnerCheck(int length) {
        assertError(
                OrganizationErrorCode.INVALID_ORGANIZATION_INPUT,
                () -> create.create(
                        new CreateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), "가".repeat(length), 2)));
        assertThat(technicianTypes.saved).isZero();
        assertThat(technicianTypes.allocated).isZero();
    }

    @Test
    void rejectsInvalidColorBeforeAllocatingOrMutatingAType() {
        var type = technicianTypes.put(ORGANIZATION, "전기", 2);
        assertError(
                OrganizationErrorCode.INVALID_ORGANIZATION_INPUT,
                () -> create.create(new CreateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), "냉방", 0)));
        assertError(
                OrganizationErrorCode.INVALID_ORGANIZATION_INPUT,
                () -> update.update(new UpdateTechnicianTypeCommand(
                        ACCOUNT.value(), ORGANIZATION.value(), type.id().value(), "냉방", 9)));
        assertThat(technicianTypes.allocated).isZero();
        assertThat(technicianTypes.saved).isZero();
        assertThat(type.name().value()).isEqualTo("전기");
        assertThat(type.color().value()).isEqualTo(2);
    }

    @Test
    void inactiveNameStillConflictsButOtherOrganizationNameDoesNot() {
        var inactive = technicianTypes.put(ORGANIZATION, "전기", 2);
        inactive.deactivate();
        technicianTypes.put(new OrganizationId(22L), "냉방", 3);

        assertError(
                OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME,
                () -> create.create(new CreateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), " 전기 ", 4)));
        assertThat(technicianTypes.allocated).isZero();
        assertThat(create.create(new CreateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), "냉방", 4))
                        .name())
                .isEqualTo("냉방");
    }

    @Test
    void updatesNameAndColorAndAllowsSavingItsOwnName() {
        var type = technicianTypes.put(ORGANIZATION, "전기", 2);

        var changed = update.update(new UpdateTechnicianTypeCommand(
                ACCOUNT.value(), ORGANIZATION.value(), type.id().value(), "  전기  ", 8));

        assertThat(changed.id()).isEqualTo(type.id().value());
        assertThat(changed.name()).isEqualTo("전기");
        assertThat(changed.color()).isEqualTo(8);
        assertThat(technicianTypes.saved).isEqualTo(1);
        assertThat(technicianTypes.lastExcluded).isEqualTo(type.id());
    }

    @Test
    void rejectsDuplicateUpdateWithoutMutatingCurrentValues() {
        var type = technicianTypes.put(ORGANIZATION, "전기", 2);
        technicianTypes.put(ORGANIZATION, "냉방", 3).deactivate();

        assertError(
                OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME,
                () -> update.update(new UpdateTechnicianTypeCommand(
                        ACCOUNT.value(), ORGANIZATION.value(), type.id().value(), "냉방", 8)));
        assertThat(type.name().value()).isEqualTo("전기");
        assertThat(type.color().value()).isEqualTo(2);
        assertThat(technicianTypes.saved).isZero();
    }

    @Test
    void rejectsForeignAndMissingTypesBeforeInvalidInputOrNameLookup() {
        var foreign = technicianTypes.put(new OrganizationId(22L), "전기", 2);

        assertError(
                OrganizationErrorCode.PERSONNEL_TYPE_NOT_FOUND,
                () -> update.update(new UpdateTechnicianTypeCommand(
                        ACCOUNT.value(), ORGANIZATION.value(), foreign.id().value(), "가", 0)));
        assertError(
                OrganizationErrorCode.PERSONNEL_TYPE_NOT_FOUND,
                () -> update.update(
                        new UpdateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), 999L, "가", 0)));
        assertThat(technicianTypes.nameLookups).isZero();
        assertThat(technicianTypes.saved).isZero();
    }

    @Test
    void ownerInvalidInputAndNullAccountFollowTheExistingErrorOrder() {
        assertThatThrownBy(() -> get.get(new GetTechnicianTypesQuery(null, ORGANIZATION.value())))
                .isInstanceOf(NullPointerException.class);
        assertError(
                OrganizationErrorCode.INVALID_ORGANIZATION_INPUT,
                () -> get.get(new GetTechnicianTypesQuery(ACCOUNT.value(), 0L)));
        assertError(
                OrganizationErrorCode.INVALID_ORGANIZATION_INPUT,
                () -> create.create(new CreateTechnicianTypeCommand(ACCOUNT.value(), 0L, "가", 0)));
        assertError(
                OrganizationErrorCode.INVALID_ORGANIZATION_INPUT,
                () -> update.update(
                        new UpdateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), 0L, "전기", 2)));
        assertThat(technicianTypes.saved).isZero();
    }

    @Test
    void nonOwnerGetsForbiddenBeforeTypeLookupAndInputValidation() {
        when(memberships.findByOrganizationAndAccount(ORGANIZATION, ACCOUNT)).thenReturn(Optional.of(member(2L, true)));
        assertForbiddenForAllActions();
        when(memberships.findByOrganizationAndAccount(ORGANIZATION, ACCOUNT))
                .thenReturn(Optional.of(member(1L, false)));
        assertForbiddenForAllActions();
        when(memberships.findByOrganizationAndAccount(ORGANIZATION, ACCOUNT)).thenReturn(Optional.empty());
        assertForbiddenForAllActions();
        assertThat(technicianTypes.reads).isZero();
        assertThat(technicianTypes.nameLookups).isZero();
        assertThat(technicianTypes.saved).isZero();
    }

    @Test
    void missingOrganizationIsForbiddenAndDoesNotExposeExistence() {
        when(organizations.findById(ORGANIZATION)).thenReturn(Optional.empty());
        assertForbiddenForAllActions();
        assertThat(technicianTypes.reads).isZero();
    }

    @Test
    void mapsOnlyTheNamedDatabaseConflictAndPropagatesOtherPortFailures() {
        technicianTypes.saveFailure = new PersonnelTypeNameConflictException(new IllegalStateException("duplicate"));
        assertError(
                OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME,
                () -> create.create(new CreateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), "전기", 2)));
        technicianTypes.saveFailure = new OrganizationRuleViolation("port failure");
        assertThatThrownBy(() ->
                        create.create(new CreateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), "전기", 2)))
                .isInstanceOf(OrganizationRuleViolation.class);

        var type = technicianTypes.put(ORGANIZATION, "냉방", 2);
        technicianTypes.saveFailure = new PersonnelTypeNameConflictException(new IllegalStateException("duplicate"));
        assertError(
                OrganizationErrorCode.DUPLICATE_PERSONNEL_TYPE_NAME,
                () -> update.update(new UpdateTechnicianTypeCommand(
                        ACCOUNT.value(), ORGANIZATION.value(), type.id().value(), "설비", 2)));
    }

    @Test
    void deactivatesAndReactivatesIdempotentlyWithoutChangingTypeValues() {
        var type = technicianTypes.put(ORGANIZATION, "전기", 2);

        var inactive = deactivate.deactivate(new DeactivateTechnicianTypeCommand(
                ACCOUNT.value(), ORGANIZATION.value(), type.id().value()));
        var stillInactive = deactivate.deactivate(new DeactivateTechnicianTypeCommand(
                ACCOUNT.value(), ORGANIZATION.value(), type.id().value()));
        var active = activate.activate(new ActivateTechnicianTypeCommand(
                ACCOUNT.value(), ORGANIZATION.value(), type.id().value()));
        var stillActive = activate.activate(new ActivateTechnicianTypeCommand(
                ACCOUNT.value(), ORGANIZATION.value(), type.id().value()));

        assertThat(inactive.active()).isFalse();
        assertThat(stillInactive.active()).isFalse();
        assertThat(active.active()).isTrue();
        assertThat(stillActive.active()).isTrue();
        assertThat(stillActive.id()).isEqualTo(type.id().value());
        assertThat(stillActive.name()).isEqualTo("전기");
        assertThat(stillActive.color()).isEqualTo(2);
    }

    @Test
    void deletesUnusedActiveTypeAndAllowsReusingItsName() {
        var type = technicianTypes.put(ORGANIZATION, "전기", 2);

        delete.delete(new DeleteTechnicianTypeCommand(
                ACCOUNT.value(), ORGANIZATION.value(), type.id().value()));

        assertThat(technicianTypes.deleted).isEqualTo(1);
        assertThat(technicianTypes.types).doesNotContainKey(type.id());
        assertThat(create.create(new CreateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), "전기", 3))
                        .name())
                .isEqualTo("전기");
    }

    @Test
    void assignedInactiveTypeCannotBeDeletedAndFkConflictAlsoMapsToInUse() {
        var type = technicianTypes.put(ORGANIZATION, "전기", 2);
        type.deactivate();
        usage.assigned.add(type.id());

        assertError(
                OrganizationErrorCode.PERSONNEL_TYPE_IN_USE,
                () -> delete.delete(new DeleteTechnicianTypeCommand(
                        ACCOUNT.value(), ORGANIZATION.value(), type.id().value())));
        assertThat(technicianTypes.deleted).isZero();
        assertThat(technicianTypes.types).containsKey(type.id());

        usage.assigned.clear();
        technicianTypes.deleteFailure = new PersonnelTypeInUseException(new IllegalStateException("foreign key"));
        assertError(
                OrganizationErrorCode.PERSONNEL_TYPE_IN_USE,
                () -> delete.delete(new DeleteTechnicianTypeCommand(
                        ACCOUNT.value(), ORGANIZATION.value(), type.id().value())));
        assertThat(technicianTypes.types).containsKey(type.id());
    }

    @Test
    void missingAndForeignTypesAreHiddenBeforeUsageChecks() {
        var foreign = technicianTypes.put(new OrganizationId(22L), "전기", 2);

        assertError(
                OrganizationErrorCode.PERSONNEL_TYPE_NOT_FOUND,
                () -> activate.activate(new ActivateTechnicianTypeCommand(
                        ACCOUNT.value(), ORGANIZATION.value(), foreign.id().value())));
        assertError(
                OrganizationErrorCode.PERSONNEL_TYPE_NOT_FOUND,
                () -> deactivate.deactivate(
                        new DeactivateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), 999L)));
        assertError(
                OrganizationErrorCode.PERSONNEL_TYPE_NOT_FOUND,
                () -> delete.delete(new DeleteTechnicianTypeCommand(
                        ACCOUNT.value(), ORGANIZATION.value(), foreign.id().value())));
        assertThat(usage.checked).isZero();
        assertThat(technicianTypes.saved).isZero();
        assertThat(technicianTypes.deleted).isZero();
    }

    @Test
    void stateAndDeleteActionsRequireOwnerBeforeTypeLookup() {
        when(memberships.findByOrganizationAndAccount(ORGANIZATION, ACCOUNT)).thenReturn(Optional.of(member(2L, true)));
        assertStateActionsForbidden();
        when(memberships.findByOrganizationAndAccount(ORGANIZATION, ACCOUNT))
                .thenReturn(Optional.of(member(1L, false)));
        assertStateActionsForbidden();
        when(memberships.findByOrganizationAndAccount(ORGANIZATION, ACCOUNT)).thenReturn(Optional.empty());
        assertStateActionsForbidden();
        assertThat(technicianTypes.reads).isZero();
        assertThat(usage.checked).isZero();
    }

    @Test
    void stateAndDeleteActionsValidateOrganizationBeforePortAccess() {
        assertThatThrownBy(() -> activate.activate(new ActivateTechnicianTypeCommand(null, ORGANIZATION.value(), 1L)))
                .isInstanceOf(NullPointerException.class);
        assertError(
                OrganizationErrorCode.INVALID_ORGANIZATION_INPUT,
                () -> deactivate.deactivate(new DeactivateTechnicianTypeCommand(ACCOUNT.value(), 0L, 1L)));
        assertError(
                OrganizationErrorCode.INVALID_ORGANIZATION_INPUT,
                () -> delete.delete(new DeleteTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), 0L)));
        assertThat(technicianTypes.reads).isZero();
        assertThat(usage.checked).isZero();
    }

    @Test
    void deletePropagatesUnrelatedPortFailure() {
        var type = technicianTypes.put(ORGANIZATION, "전기", 2);
        technicianTypes.deleteFailure = new OrganizationRuleViolation("port failure");

        assertThatThrownBy(() -> delete.delete(new DeleteTechnicianTypeCommand(
                        ACCOUNT.value(), ORGANIZATION.value(), type.id().value())))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    private void assertForbiddenForAllActions() {
        assertError(
                OrganizationErrorCode.NOT_ORGANIZATION_OWNER,
                () -> get.get(new GetTechnicianTypesQuery(ACCOUNT.value(), ORGANIZATION.value())));
        assertError(
                OrganizationErrorCode.NOT_ORGANIZATION_OWNER,
                () -> create.create(new CreateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), "가", 0)));
        assertError(
                OrganizationErrorCode.NOT_ORGANIZATION_OWNER,
                () -> update.update(
                        new UpdateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), 999L, "가", 0)));
    }

    private void assertStateActionsForbidden() {
        assertError(
                OrganizationErrorCode.NOT_ORGANIZATION_OWNER,
                () -> activate.activate(new ActivateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), 0L)));
        assertError(
                OrganizationErrorCode.NOT_ORGANIZATION_OWNER,
                () -> deactivate.deactivate(
                        new DeactivateTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), 0L)));
        assertError(
                OrganizationErrorCode.NOT_ORGANIZATION_OWNER,
                () -> delete.delete(new DeleteTechnicianTypeCommand(ACCOUNT.value(), ORGANIZATION.value(), 0L)));
    }

    private void assertError(OrganizationErrorCode expected, Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(expected));
    }

    private void owner() {
        var owner = member(1L, true);
        var organization = Organization.reconstitute(
                ORGANIZATION, new OrganizationName("회사명"), null, new CompanyCode("C0DE1234"), owner.id());
        when(organizations.findById(ORGANIZATION)).thenReturn(Optional.of(organization));
        when(memberships.findByOrganizationAndAccount(ORGANIZATION, ACCOUNT)).thenReturn(Optional.of(owner));
    }

    private Membership member(long id, boolean active) {
        return Membership.reconstitute(new MembershipId(id), ORGANIZATION, ACCOUNT, null, NOW, active);
    }

    private static final class FakeTechnicianTypes implements TechnicianTypeRepository {
        private final Map<TechnicianTypeId, TechnicianType> types = new HashMap<>();
        private long nextId = 1;
        private int allocated;
        private int reads;
        private int nameLookups;
        private int saved;
        private int deleted;
        private TechnicianTypeId lastExcluded;
        private RuntimeException saveFailure;
        private RuntimeException deleteFailure;

        TechnicianType put(OrganizationId organizationId, String name, int color) {
            var type = TechnicianType.create(
                    new TechnicianTypeId(nextId++), organizationId, new PersonnelTypeName(name), new TypeColor(color));
            types.put(type.id(), type);
            return type;
        }

        @Override
        public TechnicianTypeId nextId() {
            allocated++;
            return new TechnicianTypeId(nextId++);
        }

        @Override
        public void save(TechnicianType type) {
            saved++;
            if (saveFailure != null) {
                throw saveFailure;
            }
            types.put(type.id(), type);
        }

        @Override
        public Optional<TechnicianType> findByIdForUpdate(TechnicianTypeId id) {
            reads++;
            return Optional.ofNullable(types.get(id));
        }

        @Override
        public List<TechnicianType> findAllByOrganization(OrganizationId organizationId) {
            reads++;
            var result = new ArrayList<TechnicianType>();
            for (var type : types.values()) {
                if (type.organizationId().equals(organizationId)) {
                    result.add(type);
                }
            }
            result.sort(Comparator.comparing(TechnicianType::id, Comparator.comparing(TechnicianTypeId::value)));
            return result;
        }

        @Override
        public boolean existsByOrganizationAndName(
                OrganizationId organizationId, PersonnelTypeName name, TechnicianTypeId excludeId) {
            nameLookups++;
            lastExcluded = excludeId;
            return types.values().stream()
                    .anyMatch(type -> type.organizationId().equals(organizationId)
                            && type.name().equals(name)
                            && !type.id().equals(excludeId));
        }

        @Override
        public void delete(TechnicianTypeId id) {
            if (deleteFailure != null) {
                throw deleteFailure;
            }
            deleted++;
            types.remove(id);
        }
    }

    private static final class FakeUsage implements TechnicianTypeUsagePort {
        private final Set<TechnicianTypeId> assigned = new HashSet<>();
        private int checked;

        @Override
        public boolean isAssigned(TechnicianTypeId id) {
            checked++;
            return assigned.contains(id);
        }
    }
}
