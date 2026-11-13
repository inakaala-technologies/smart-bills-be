package com.bhive.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

@Component
public class RedisCacheErrorHandler implements CacheErrorHandler {

	private static final Logger log = LoggerFactory.getLogger(RedisCacheErrorHandler.class);

	@Override
	public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {

		log.warn(
				"Redis cache GET failed. Cache: {}, Key: {}. "
						+ "Continuing without cache and fetching from DB. Error: {}",
				cache.getName(), key, exception.getMessage());

		// Swallow the cache error.
		// Spring treats this as a cache miss and executes the method.
	}

	@Override
	public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {

		log.warn("Redis cache PUT failed. Cache: {}, Key: {}. " + "Continuing without caching. Error: {}",
				cache.getName(), key, exception.getMessage());

		// Database operation has already executed.
		// Continue even if storing the result in Redis fails.
	}

	@Override
	public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {

		log.error("Redis cache EVICT failed. Cache: {}, Key: {}. Error: {}", cache.getName(), key,
				exception.getMessage());

		// Allow the business operation to continue.
	}

	@Override
	public void handleCacheClearError(RuntimeException exception, Cache cache) {

		log.error("Redis cache CLEAR failed. Cache: {}. Error: {}", cache.getName(), exception.getMessage());

		// Allow the business operation to continue.
	}
}
