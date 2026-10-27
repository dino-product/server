package com.orbit.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.util.ClassUtils;

/**
 * {@link SharedContainers}를 쓰는 테스트 클래스를 시작하기 전에 공유 DB의 모든 테이블과 Redis 키를 비운다. 컨테이너를 컨텍스트마다 띄우던 때는 새 컨텍스트가 빈 DB를
 * 받았지만, 이제는 다른 컨텍스트의 테스트가 커밋한 행이 남으므로 고정 식별자·고유 값·개수를 단언하는 테스트가 서로 섞이지 않게 한다.
 *
 * <p>{@code @Nested} 클래스는 바깥 클래스와 한 묶음으로 보고 비우지 않는다. 같은 클래스 안의 테스트끼리는 지금처럼 각 테스트가 데이터를 격리한다. Redis는 Redis
 * Bean이 있는 컨텍스트에서만 비운다.
 *
 * <p>컨텍스트가 공유 컨테이너가 아닌 DB·Redis에 연결돼 있으면 비우지 않고 실패한다. 다른 저장소를 지우지 않으면서, 설정 실수로 정리가 빠진 채 테스트가 섞이는 것도
 * 드러내기 위해서다.
 */
final class SharedContainersCleanupListener extends AbstractTestExecutionListener {

    private static final String PUBLIC_TABLES = """
            select string_agg(format('%I.%I', schemaname, tablename), ', ')
            from pg_tables
            where schemaname = 'public'
              -- Flyway 이력을 지우면 다음 컨텍스트가 마이그레이션을 다시 실행하다 실패한다.
              and tablename <> 'flyway_schema_history'
            """;

    // 앞선 테스트가 끝내지 못한 트랜잭션이 테이블을 잠그고 있으면 무한히 기다리지 않고 실패한다.
    private static final String LOCK_TIMEOUT = "set lock_timeout = '10s'";

    /** {@code @Sql(BEFORE_TEST_CLASS)}로 넣는 데이터보다 먼저 비운다. */
    @Override
    public int getOrder() {
        return SqlScriptsTestExecutionListener.ORDER - 1;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        if (ClassUtils.isInnerClass(testContext.getTestClass())) {
            return;
        }
        ApplicationContext context = testContext.getApplicationContext();
        truncateTables(context.getBean(DataSource.class));
        context.getBeanProvider(RedisConnectionFactory.class).ifAvailable(SharedContainersCleanupListener::flushRedis);
    }

    private static void truncateTables(DataSource dataSource) {
        new JdbcTemplate(dataSource).execute((ConnectionCallback<Void>) connection -> {
            assertSharedPostgres(connection);
            try (Statement statement = connection.createStatement()) {
                String tables = publicTables(statement);
                if (tables == null) {
                    return null;
                }
                statement.execute(LOCK_TIMEOUT);
                try {
                    statement.execute("truncate table " + tables + " restart identity cascade");
                } finally {
                    statement.execute("reset lock_timeout");
                }
            }
            return null;
        });
    }

    private static void assertSharedPostgres(Connection connection) throws SQLException {
        String connected = withoutQuery(connection.getMetaData().getURL());
        String shared = withoutQuery(SharedContainers.POSTGRES.getJdbcUrl());
        if (!connected.equals(shared)) {
            throw new IllegalStateException(
                    "DataSource is not the shared PostgreSQL container: " + connected + " (expected " + shared + ")");
        }
    }

    private static String publicTables(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery(PUBLIC_TABLES)) {
            result.next();
            return result.getString(1);
        }
    }

    // 드라이버 옵션(예: loggerLevel)은 연결 대상이 아니므로 비교에서 뺀다.
    private static String withoutQuery(String jdbcUrl) {
        int query = jdbcUrl.indexOf('?');
        return query < 0 ? jdbcUrl : jdbcUrl.substring(0, query);
    }

    private static void flushRedis(RedisConnectionFactory connectionFactory) {
        assertSharedRedis(connectionFactory);
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    private static void assertSharedRedis(RedisConnectionFactory connectionFactory) {
        String shared = SharedContainers.REDIS.getHost() + ":"
                + SharedContainers.REDIS.getMappedPort(SharedContainers.REDIS_PORT);
        String connected = connectionFactory instanceof LettuceConnectionFactory lettuce
                ? lettuce.getHostName() + ":" + lettuce.getPort()
                : connectionFactory.getClass().getName();
        if (!connected.equals(shared)) {
            throw new IllegalStateException(
                    "Redis is not the shared Redis container: " + connected + " (expected " + shared + ")");
        }
    }
}
