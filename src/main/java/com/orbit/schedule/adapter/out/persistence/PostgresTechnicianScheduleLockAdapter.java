package com.orbit.schedule.adapter.out.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;

/**
 * PostgreSQL 트랜잭션 단위 advisory lock으로 조직·기사의 일정 변경을 한 줄로 세운다. 테이블 없이 조직·기사로 만든 64비트 키(SHA-256 앞 8바이트)를 잠그고,
 * 트랜잭션이 커밋·롤백되거나 연결이 끊기면 PostgreSQL이 푼다. 키가 우연히 겹치면 다른 기사끼리 기다리게 되고, 대기 한도를 넘기면 409가 날 수 있다.
 *
 * <p>대기 한도({@code lock_timeout})는 잠금에만 적용한다. 한 번의 왕복으로 보내는 익명 블록 안에서 현재 값을 보관했다가 잠근 뒤 그대로 되돌려, 세션이나 같은
 * 트랜잭션이 앞서 정한 값을 바꾸지 않는다. 블록에는 계산한 정수 키와 밀리초만 들어간다. 익명 블록은 파라미터를 받지 못해 조직·기사마다 문장이 달라지므로
 * pg_stat_statements를 쓰면 기사 수만큼 항목이 생긴다. 문제가 되면 마이그레이션 도구 도입 후 {@code SET lock_timeout} 절을 둔 SQL 함수로 바꿔 파라미터화한다.
 *
 * <p>서비스 트랜잭션과 같은 연결이어야 잠금이 커밋까지 유지되므로, 이 DataSource에 묶인 트랜잭션 연결이 없으면(다른 트랜잭션 매니저·DataSource 등) 거부한다.
 */
@Component
class PostgresTechnicianScheduleLockAdapter implements LockTechnicianSchedulePort {

    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final String KEY_NAMESPACE = "schedule-technician:";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final TechnicianScheduleLockProperties properties;

    PostgresTechnicianScheduleLockAdapter(JdbcTemplate jdbcTemplate, TechnicianScheduleLockProperties properties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.dataSource = Objects.requireNonNull(jdbcTemplate.getDataSource(), "jdbcTemplate must have a DataSource");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public void lock(OrganizationId organizationId, MembershipId technicianId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(technicianId, "technicianId must not be null");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("technician schedule lock requires an active transaction");
        }
        // 풀의 auto-commit 설정과 무관하게, 이 DataSource에 트랜잭션 연결이 묶여 있어야 잠금이 커밋까지 유지된다.
        if (!(TransactionSynchronizationManager.getResource(dataSource) instanceof ConnectionHolder)) {
            throw new IllegalStateException("technician schedule lock requires the transaction's connection");
        }
        String sql = "DO $$ DECLARE previous text := current_setting('lock_timeout'); BEGIN "
                + "PERFORM set_config('lock_timeout', '"
                + properties.waitLimit().toMillis() + "ms', true); "
                + "PERFORM pg_advisory_xact_lock('" + keyOf(organizationId, technicianId) + "'::bigint); "
                + "PERFORM set_config('lock_timeout', previous, true); END $$";
        try {
            jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
                return null;
            });
        } catch (DataAccessException e) {
            if (isLockNotAvailable(e)) {
                throw new BusinessException(ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY, e);
            }
            throw e;
        }
    }

    static long keyOf(OrganizationId organizationId, MembershipId technicianId) {
        String name = KEY_NAMESPACE + organizationId.value() + ":" + technicianId.value();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    // 예외 변환기 설정에 따라 CannotAcquireLockException이 아닐 수 있어 PostgreSQL SQLState로 판정한다.
    private static boolean isLockNotAvailable(DataAccessException e) {
        return e.getMostSpecificCause() instanceof SQLException sqlException
                && LOCK_NOT_AVAILABLE.equals(sqlException.getSQLState());
    }
}
