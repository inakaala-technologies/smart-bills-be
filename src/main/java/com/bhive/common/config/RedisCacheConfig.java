package com.bhive.common.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.bhive.common.cache.RedisCacheErrorHandler;
import com.bhive.common.cache.SafeTransactionAwareCacheManager;

@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

	private final RedisCacheErrorHandler redisCacheErrorHandler;

	public RedisCacheConfig(RedisCacheErrorHandler redisCacheErrorHandler) {
		this.redisCacheErrorHandler = redisCacheErrorHandler;
	}

	@Value("${app.cache.ttl:30m}")
	private Duration cacheTtl;

	@Value("${spring.cache.redis.key-prefix:bhive:}")
	private String cacheKeyPrefix;

	@Bean
	public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

		RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig().entryTtl(cacheTtl)
				.disableCachingNullValues().computePrefixWith(cacheName -> cacheKeyPrefix + cacheName + "::")
				.serializeKeysWith(
						RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair
						.fromSerializer(new GenericJackson2JsonRedisSerializer()));

		RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory).cacheDefaults(configuration)
				.build();

		return new SafeTransactionAwareCacheManager(redisCacheManager);
	}

	@Override
	public CacheErrorHandler errorHandler() {
		return redisCacheErrorHandler;
	}
}
