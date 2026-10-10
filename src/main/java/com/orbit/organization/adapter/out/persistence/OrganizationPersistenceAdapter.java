package com.orbit.organization.adapter.out.persistence;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.organization.application.port.out.OrganizationBusyException;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;

/**
 * 발주사 저장소. 잠금({@link #lock})은 발주사 행을 {@code FOR UPDATE}로 잠그고 대기 한도({@code lock_timeout})를 잠금에만 적용한다. 한 번의 왕복으로
 * 보내는 익명 블록 안에서 현재 값을 보관했다가 잠근 뒤 그대로 되돌려, 세션이나 같은 트랜잭션이 앞서 정한 값을 바꾸지 않는다. 블록에는 발주사 식별자와 밀리초 정수만
 * 들어간다. 서비스 트랜잭션과 같은 연결이어야 잠금이 커밋까지 유지되므로, 이 DataSource에 묶인 트랜잭션 연결이 없으면 거부한다.
 */
@Repository
class OrganizationPersistenceAdapter implements OrganizationRepository {

    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private final SpringDataOrganizationRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final OrganizationLockProperties lockProperties;

    OrganizationPersistenceAdapter(
            SpringDataOrganizationRepository repository,
            JdbcTemplate jdbcTemplate,
            OrganizationLockProperties lockProperties) {
        this.repository = repository;
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.dataSource = Objects.requireNonNull(jdbcTemplate.getDataSource(), "jdbcTemplate must have a DataSource");
        this.lockProperties = Objects.requireNonNull(lockProperties, "lockProperties must not be null");
    }

    @Override
    public Organization save(Organization organization) {
        if (organization.id().isEmpty()) {
            return repository.save(OrganizationJpaEntity.from(organization)).toDomain();
        }
        OrganizationJpaEntity entity = repository
                .findById(organization.id().get().value())
                .orElseThrow(() -> new IllegalStateException("organization to update must exist"));
        entity.changeInfo(organization);
        return entity.toDomain();
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        return repository.findById(id.value()).map(OrganizationJpaEntity::toDomain);
    }

    @Override
    public boolean existsByCode(CompanyCode code) {
        return repository.existsByCode(code.value());
    }

    @Override
    public void lock(OrganizationId id) {
        Objects.requireNonNull(id, "id must not be null");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("organization lock requires an active transaction");
        }
        // 풀의 auto-commit 설정과 무관하게, 이 DataSource에 트랜잭션 연결이 묶여 있어야 잠금이 커밋까지 유지된다.
        if (!(TransactionSynchronizationManager.getResource(dataSource) instanceof ConnectionHolder)) {
            throw new IllegalStateException("organization lock requires the transaction's connection");
        }
        String sql = "DO $$ DECLARE previous text := current_setting('lock_timeout'); BEGIN "
                + "PERFORM set_config('lock_timeout', '"
                + lockProperties.waitLimit().toMillis() + "ms', true); "
                + "PERFORM 1 FROM companies WHERE id = " + id.value() + " FOR UPDATE; "
                + "PERFORM set_config('lock_timeout', previous, true); END $$";
        try {
            jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
                return null;
            });
        } catch (DataAccessException exception) {
            if (isLockNotAvailable(exception)) {
                throw new OrganizationBusyException(exception);
            }
            throw exception;
        }
    }

    // 예외 변환기 설정에 따라 CannotAcquireLockException이 아닐 수 있어 PostgreSQL SQLState로 판정한다.
    private static boolean isLockNotAvailable(DataAccessException exception) {
        return exception.getMostSpecificCause() instanceof SQLException sqlException
                && LOCK_NOT_AVAILABLE.equals(sqlException.getSQLState());
    }
}
