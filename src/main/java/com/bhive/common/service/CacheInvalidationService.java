
package com.bhive.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bhive.common.cache.CacheNames;

@Service
public class CacheInvalidationService {

	private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);

	private final CacheManager cacheManager;

	public CacheInvalidationService(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	public void evictBusinessDataCaches() {
		log.info("FORCE CLEARING ALL BUSINESS CACHES");

		Cache dashboard = cacheManager.getCache(CacheNames.BUSINESS_DASHBOARD);
		Cache insights = cacheManager.getCache(CacheNames.BUSINESS_INSIGHTS);

		log.info("Dashboard cache object: {}", dashboard);
		log.info("Insights cache object: {}", insights);

		if (dashboard != null) {
			dashboard.clear();
			log.info("Dashboard cache cleared");
		}

		if (insights != null) {
			insights.clear();
			log.info("Insights cache cleared");
		}
	}

	public void evictOfferCaches() {
		evictAll(CacheNames.BUSINESS_OFFERS);
		evictAll(CacheNames.NEARBY_OFFERS);
	}

	public void evictLoyaltyRuleCaches() {
		evictAll(CacheNames.LOYALTY_RULES);
	}

	public void evictAppointmentServiceCaches() {
		evictAll(CacheNames.APPOINTMENT_SERVICES);
	}

	public void evictUserBusinessAccessCaches() {
		evictAll(CacheNames.BUSINESS_DASHBOARD);
		evictAll(CacheNames.BUSINESS_INSIGHTS);
		evictAll(CacheNames.BUSINESS_OFFERS);
		evictAll(CacheNames.APPOINTMENT_SERVICES);
	}

	public void evict(String cacheName, Object key) {
		executeAfterCommit(() -> {
			Cache cache = cacheManager.getCache(cacheName);

			if (cache != null) {
				cache.evict(key);
				log.info("Cache entry evicted. Cache: {}, Key: {}", cacheName, key);
			} else {
				log.warn("Cache not found: {}", cacheName);
			}
		});
	}

	public void evictAll(String cacheName) {

		log.info("Request received to clear cache: {}", cacheName);

		executeAfterCommit(() -> {
			Cache cache = cacheManager.getCache(cacheName);

			if (cache != null) {
				cache.clear();
				log.info("Successfully cleared cache: {}", cacheName);
			} else {
				log.warn("Cache not found: {}", cacheName);
			}
		});
	}

	private void executeAfterCommit(Runnable action) {

		if (TransactionSynchronizationManager.isActualTransactionActive()
				&& TransactionSynchronizationManager.isSynchronizationActive()) {

			log.info("Transaction active. Cache eviction scheduled after commit.");

			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					log.info("Transaction committed. Executing cache eviction.");
					action.run();
				}
			});

		} else {
			log.info("No active synchronized transaction. Executing cache eviction immediately.");
			action.run();
		}
	}
}
