package com.orbit.schedule.adapter.out.board;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.lettuce.core.resource.ClientResources;

/**
 * 캐시를 켰을 때만 캐시 Redis 저장소를 만든다. 주소가 없으면 {@link BoardCacheProperties#sharedRedisFallback}일 때만 기본 Redis 연결 정보를 쓰고,
 * 아니면(배포 프로필) 기동하지 않는다. 캐시 데이터가 로그인·실시간용 Redis를 채우지 않게 하기 위해서다.
 */
@Configuration(proxyBeanMethods = false)
class BoardCacheConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.schedule.board-cache", name = "enabled", matchIfMissing = true)
    RedisBoardCacheStore boardCacheStore(
            BoardCacheProperties properties,
            ObjectProvider<DataRedisConnectionDetails> sharedRedis,
            ObjectProvider<ClientResources> clientResources) {
        String host = properties.redisHost();
        int port = properties.redisPort();
        if (host.isEmpty()) {
            DataRedisConnectionDetails details = sharedRedis.getIfAvailable();
            if (!properties.sharedRedisFallback() || details == null || details.getStandalone() == null) {
                throw new IllegalStateException("app.schedule.board-cache.redis-host (BOARD_CACHE_REDIS_HOST) "
                        + "is required when the board cache is enabled");
            }
            host = details.getStandalone().getHost();
            port = details.getStandalone().getPort();
        }
        return new RedisBoardCacheStore(
                host,
                port,
                properties.timeout(),
                properties.ttl(),
                properties.maxPeriods(),
                clientResources.getIfAvailable());
    }
}
