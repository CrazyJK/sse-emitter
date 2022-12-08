package com.hs.common.notificator;

import java.util.Map.Entry;

import org.infinispan.Cache;
import org.infinispan.health.CacheHealth;
import org.infinispan.health.HealthStatus;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CacheStatus {

    @Autowired
    EmbeddedCacheManager cacheManager;

    @Scheduled(fixedRate = 60 * 1000)
    private void print() {
        log.info("-------------------------------------------------------");
        log.info("    Cache status = {}", cacheManager.getStatus());
        log.info("    Cluster name = {}", cacheManager.getClusterName());
        log.info("    Cluster health = {}", cacheManager.getHealth().getClusterHealth().getHealthStatus());
        log.info("    Cluster members = {}", cacheManager.getMembers());
        log.info("    me = {}", cacheManager.getAddress());
        for (CacheHealth cacheHealth : cacheManager.getHealth().getCacheHealth()) {
            final String cacheName = cacheHealth.getCacheName();
            final HealthStatus status = cacheHealth.getStatus();
            final Cache<Object, Object> cache = cacheManager.getCache(cacheName);
            final int size = cache.size();
            
            log.info("     - {} = {}, {} objects", cacheName, status, size);
            
            for (Entry<Object, Object> entry : cache.entrySet()) {
                log.info("        > {} = {}", entry.getKey(), entry.getValue());                
            }
        }
        log.info("-------------------------------------------------------");
        
    }

}
