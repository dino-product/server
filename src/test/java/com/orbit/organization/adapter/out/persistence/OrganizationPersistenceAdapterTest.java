package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.support.TestcontainersConfiguration;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("발주사·직원 소속 저장")
class OrganizationPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00.123456Z");

    @Autowired
    private SpringDataOrganizationRepository organizationRepository;

    @Autowired
    private SpringDataMembershipRepository membershipRepository;

    private OrganizationPersistenceAdapter organizations;
    private MembershipPersistenceAdapter memberships;

    @BeforeEach
    void setUp() {
        organizations = new OrganizationPersistenceAdapter(organizationRepository);
        memberships = new MembershipPersistenceAdapter(membershipRepository);
    }

    @Test
    @DisplayName("발주사를 저장하면 식별자와 함께 같은 값으로 복원된다")
    void savesAndRestoresOrganization() {
        Organization saved = organizations.save(organization("7K2M9X"));

        assertThat(saved.id()).isPresent();
        assertThat(saved.name()).isEqualTo(new OrganizationName("오르빗 설비"));
        assertThat(saved.industry()).isEqualTo(Industry.PLUMBING);
        assertThat(saved.code()).isEqualTo(new CompanyCode("7K2M9X"));
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("현재 쓰이는 회사 코드인지 확인한다")
    void checksWhetherCodeIsInUse() {
        organizations.save(organization("Q4ZT8B"));

        assertThat(organizations.existsByCode(new CompanyCode("Q4ZT8B"))).isTrue();
        assertThat(organizations.existsByCode(new CompanyCode("Q4ZT8C"))).isFalse();
    }

    @Test
    @DisplayName("같은 회사 코드를 두 발주사가 가질 수 없다")
    void rejectsDuplicateCodeAtDatabase() {
        organizations.save(organization("M3N4P5"));

        assertThatThrownBy(() -> {
                    organizations.save(organization("M3N4P5"));
                    organizationRepository.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("직원 소속을 저장하면 총관리자 표시·상태·시각을 그대로 복원한다")
    void savesAndRestoresMembership() {
        OrganizationId organizationId =
                organizations.save(organization("R6S7T8")).id().orElseThrow();

        Membership saved = memberships.save(Membership.founder(organizationId, new AccountId(7L), NOW));

        assertThat(saved.id()).isPresent();
        assertThat(saved.organizationId()).isEqualTo(organizationId);
        assertThat(saved.accountId()).isEqualTo(new AccountId(7L));
        assertThat(saved.isOwner()).isTrue();
        assertThat(saved.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(saved.joinedAt()).isEqualTo(NOW);
        assertThat(saved.statusChangedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("같은 계정·같은 발주사의 직원 소속은 하나만 둘 수 있다")
    void rejectsSecondMembershipOfSameAccountInSameOrganization() {
        OrganizationId organizationId =
                organizations.save(organization("V9W0X1")).id().orElseThrow();
        memberships.save(Membership.founder(organizationId, new AccountId(7L), NOW));

        assertThatThrownBy(() -> {
                    memberships.save(Membership.founder(organizationId, new AccountId(7L), NOW));
                    membershipRepository.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Organization organization(String code) {
        return Organization.create(new OrganizationName("오르빗 설비"), Industry.PLUMBING, new CompanyCode(code), NOW);
    }
}
