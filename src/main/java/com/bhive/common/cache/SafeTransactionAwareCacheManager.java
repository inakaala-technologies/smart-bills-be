package com.bhive.common.cache;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public class SafeTransactionAwareCacheManager implements CacheManager {

	private final CacheManager delegate;

	private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();

	public SafeTransactionAwareCacheManager(CacheManager delegate) {
		this.delegate = delegate;
	}

	@Override
	public Cache getCache(String name) {

		Cache cache = delegate.getCache(name);

		if (cache == null) {
			return null;
		}

		return cacheMap.computeIfAbsent(name, key -> new SafeTransactionAwareCache(cache));
	}

	@Override
	public Collection<String> getCacheNames() {
		return delegate.getCacheNames();
	}
}
