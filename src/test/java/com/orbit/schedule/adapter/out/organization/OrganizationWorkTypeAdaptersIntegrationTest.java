package com.orbit.schedule.adapter.out.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.CreateWorkTypeUseCase;
import com.orbit.organization.application.port.in.command.DeleteWorkTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreateWorkTypeCommand;
import com.orbit.organization.application.port.in.command.dto.DeleteWorkTypeCommand;
import com.orbit.organization.domain.Industry;
import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.application.port.out.WorkTypePort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;
import com.orbit.support.IntegrationTestSupport;

/**
 * 작업 유형 확인·표시 정보 어댑터를 실제 organization 구현과 PostgreSQL로 확인한다. 어댑터의 번역은 단위 테스트가 보고, 여기서는 organization이 정한 판정(삭제된
 * 유형·다른 발주사의 유형·없는 유형)과 트랜잭션 전제가 schedule 포트로 그대로 나오는지 본다.
 */
@DisplayName("작업 유형 어댑터와 organization 연동(PostgreSQL)")
class OrganizationWorkTypeAdaptersIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong NEXT_OWNER = new AtomicLong(93_000L);

    @Autowired
    private CreateOrganizationUseCase createOrganizationUseCase;

    @Autowired
    private CreateWorkTypeUseCase createWorkTypeUseCase;

    @Autowired
    private DeleteWorkTypeUseCase deleteWorkTypeUseCase;

    @Autowired
    private WorkTypePort workTypePort;

    @Autowired
    private LoadWorkTypeLabelsPort workTypeLabelsPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("표시 정보는 삭제된 유형을 삭제 표시와 함께 담고, 다른 발주사의 유형과 없는 유형은 뺀다")
    void labelsIncludeDeletedAndExcludeOthers() {
        Fixture fixture = fixture();

        assertThat(workTypeLabelsPort.findLabels(
                        fixture.organization,
                        Set.of(fixture.kept, fixture.deleted, fixture.ofOther, new WorkTypeId(999_999L))))
                .containsOnly(
                        entry(fixture.kept, new TypeLabel(fixture.kept.value(), "설치", "#3B82F6", false)),
                        entry(fixture.deleted, new TypeLabel(fixture.deleted.value(), "철거", "#3B82F6", true)));
    }

    @Test
    @DisplayName("선택 가능 확인은 삭제된 유형·다른 발주사의 유형·없는 유형을 거부하고 지금 유형만 허용한다")
    void selectableOnlyForCurrentTypesOfTheOrganization() {
        Fixture fixture = fixture();

        assertThat(inTransaction(fixture.organization, fixture.kept)).isTrue();
        assertThat(inTransaction(fixture.organization, fixture.deleted)).isFalse();
        assertThat(inTransaction(fixture.organization, fixture.ofOther)).isFalse();
        assertThat(inTransaction(fixture.organization, new WorkTypeId(999_999L)))
                .isFalse();
    }

    @Test
    @DisplayName("선택 가능 확인은 유형 행을 잠그므로 호출 트랜잭션 없이는 거부한다")
    void selectableCheckRequiresCallerTransaction() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> workTypePort.isSelectable(fixture.organization, fixture.kept))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private boolean inTransaction(OrganizationId organization, WorkTypeId workType) {
        return Boolean.TRUE.equals(new TransactionTemplate(transactionManager)
                .execute(status -> workTypePort.isSelectable(organization, workType)));
    }

    private Fixture fixture() {
        long owner = NEXT_OWNER.incrementAndGet();
        long organization = organization(owner);
        long kept = workType(owner, organization, "설치");
        long deleted = workType(owner, organization, "철거");
        deleteWorkTypeUseCase.delete(new DeleteWorkTypeCommand(owner, organization, deleted));
        long otherOwner = NEXT_OWNER.incrementAndGet();
        long ofOther = workType(otherOwner, organization(otherOwner), "설치");
        return new Fixture(
                new OrganizationId(organization),
                new WorkTypeId(kept),
                new WorkTypeId(deleted),
                new WorkTypeId(ofOther));
    }

    private record Fixture(OrganizationId organization, WorkTypeId kept, WorkTypeId deleted, WorkTypeId ofOther) {}

    private long organization(long owner) {
        return createOrganizationUseCase
                .create(new CreateOrganizationCommand(owner, "작업 유형 어댑터 발주사", Industry.HVAC))
                .organizationId();
    }

    private long workType(long owner, long organizationId, String name) {
        return createWorkTypeUseCase
                .create(new CreateWorkTypeCommand(owner, organizationId, name, "#3B82F6"))
                .workTypeId();
    }
}
