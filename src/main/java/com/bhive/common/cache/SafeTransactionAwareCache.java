package com.bhive.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class SafeTransactionAwareCache extends TransactionAwareCacheDecorator {

	private static final Logger log = LoggerFactory.getLogger(SafeTransactionAwareCache.class);

	private final Cache targetCache;

	public SafeTransactionAwareCache(Cache targetCache) {
		super(targetCache);
		this.targetCache = targetCache;
	}

	@Override
	public void put(Object key, Object value) {
		executeAfterCommit("PUT", key, () -> targetCache.put(key, value));
	}

	@Override
	public void evict(Object key) {
		executeAfterCommit("EVICT", key, () -> targetCache.evict(key));
	}

	@Override
	public void clear() {
		executeAfterCommit("CLEAR", null, () -> targetCache.clear());
	}

	private void executeAfterCommit(String operation, Object key, Runnable cacheOperation) {

		if (TransactionSynchronizationManager.isSynchronizationActive()) {

			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

				@Override
				public void afterCommit() {
					executeSafely(operation, key, cacheOperation);
				}
			});

		} else {
			// No active transaction synchronization.
			// Execute normally and let CacheErrorHandler handle failures.
			cacheOperation.run();
		}
	}

	private void executeSafely(String operation, Object key, Runnable cacheOperation) {

		try {

			cacheOperation.run();

		} catch (RuntimeException exception) {

			log.warn(
					"Redis cache {} failed after transaction commit. " + "Cache: {}, Key: {}. "
							+ "Continuing without cache operation. Error: {}",
					operation, targetCache.getName(), key, exception.getMessage());
		}
	}
}
