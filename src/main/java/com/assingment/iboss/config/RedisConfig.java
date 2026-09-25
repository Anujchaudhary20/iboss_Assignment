package com.assingment.iboss.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise Redis Distributed Caching Configuration.
 * Configures RedisCacheManager with customized TTLs per cache domain,
 * JSON value serialization, and a resilient CacheErrorHandler that provides
 * zero-downtime graceful fallback to origin services if Redis is unavailable.
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    public static final String CACHE_TICKERS = "market:tickers";
    public static final String CACHE_ORDERBOOK = "market:orderbook";
    public static final String CACHE_PAIRS = "market:pairs";
    public static final String CACHE_USER_PROFILE = "user:profile";

    @Value("${app.cache.redis.default-ttl-seconds:60}")
    private long defaultTtlSeconds;

    @Value("${app.cache.redis.tickers-ttl-seconds:3}")
    private long tickersTtlSeconds;

    @Value("${app.cache.redis.orderbook-ttl-seconds:2}")
    private long orderbookTtlSeconds;

    @Value("${app.cache.redis.pairs-ttl-minutes:10}")
    private long pairsTtlMinutes;

    @Value("${app.cache.redis.user-profile-ttl-minutes:5}")
    private long userProfileTtlMinutes;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(defaultTtlSeconds))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // Hot market tickers: short TTL (e.g. 3s) to prevent origin stampedes while delivering fresh data
        cacheConfigs.put(CACHE_TICKERS, defaultConfiguration.entryTtl(Duration.ofSeconds(tickersTtlSeconds)));

        // Live order book snapshot: very short TTL (e.g. 2s)
        cacheConfigs.put(CACHE_ORDERBOOK, defaultConfiguration.entryTtl(Duration.ofSeconds(orderbookTtlSeconds)));

        // Supported trading pairs: long TTL (e.g. 10m)
        cacheConfigs.put(CACHE_PAIRS, defaultConfiguration.entryTtl(Duration.ofMinutes(pairsTtlMinutes)));

        // User profile / metadata: medium TTL (e.g. 5m)
        cacheConfigs.put(CACHE_USER_PROFILE, defaultConfiguration.entryTtl(Duration.ofMinutes(userProfileTtlMinutes)));

        log.info("Initialized RedisCacheManager with customized TTLs: tickers={}s, orderbook={}s, pairs={}m, user-profile={}m",
                tickersTtlSeconds, orderbookTtlSeconds, pairsTtlMinutes, userProfileTtlMinutes);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    /**
     * Resilient error handler: if Redis is temporarily unreachable or throws an exception,
     * the application logs a warning and continues processing via direct backend execution
     * without failing client requests.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis GET failed for cache='{}', key='{}'. Falling back to live origin. Error: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis PUT failed for cache='{}', key='{}'. Error: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis EVICT failed for cache='{}', key='{}'. Error: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis CLEAR failed for cache='{}'. Error: {}",
                        cache.getName(), exception.getMessage());
            }
        };
    }
}
