package com.hs.common.notificator;

import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.util.concurrent.IsolationLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/**
 * CacheConfig
 * 
 * https://jsonobject.tistory.com/558
 * https://jsonobject.tistory.com/441
 */
@Configuration
@Slf4j
public class CacheConfig {

    public static final long TIMEOUT = 10;
    
    @Autowired
    Environment environment;

    // 캐시를 총괄하는 매지저 빈 생성
    @Bean("cacheManager")
    public EmbeddedCacheManager cacheManager() {
        GlobalConfigurationBuilder global = new GlobalConfigurationBuilder().transport().defaultTransport().clusterName("notificator-cache-cluster").defaultCacheName("default-cache");

        global.serialization().marshaller(new JavaSerializationMarshaller()).allowList().addClasses(SseEmitter.class, String.class);

        return new DefaultCacheManager(new ConfigurationBuilderHolder(Thread.currentThread().getContextClassLoader(), global), true);
    }

    // 로컬 캐시 빈 생성
    @Bean("sseEmitterCache")
    public Cache<String, SseEmitter> sseEmitterCache(@Qualifier("cacheManager") EmbeddedCacheManager cacheManager) {
        final String cacheName = "sse-emitter-cache";

        ConfigurationBuilder config = new ConfigurationBuilder();
        // 로컬 캐시의 만료 시간을 설정
        config.expiration().lifespan(TIMEOUT, TimeUnit.MINUTES);
        // 캐시 모드: 로컬
        config.clustering().cacheMode(CacheMode.LOCAL); 
        config.locking().isolationLevel(IsolationLevel.READ_COMMITTED).useLockStriping(false).lockAcquisitionTimeout(TIMEOUT, TimeUnit.SECONDS);
        config.transaction().lockingMode(LockingMode.OPTIMISTIC).transactionMode(TransactionMode.NON_TRANSACTIONAL);

        cacheManager.defineConfiguration(cacheName, config.build());
        log.info("Created sseEmitterCache");
        
        return cacheManager.getCache(cacheName);
    }

    // 전송할 Event 데이터를 저장할 분산 캐시 빈 생성
    @Bean("sseEventCache")
    public Cache<String, String> sseEventCache(@Qualifier("cacheManager") EmbeddedCacheManager cacheManager) {
        final String cacheName = "sse-event-cache";

        ConfigurationBuilder config = new ConfigurationBuilder();
        config.expiration().lifespan(TIMEOUT, TimeUnit.MINUTES);
        config.clustering().cacheMode(CacheMode.REPL_ASYNC); // 캐시 모드: Replicated
        config.locking().isolationLevel(IsolationLevel.READ_COMMITTED).useLockStriping(false).lockAcquisitionTimeout(TIMEOUT, TimeUnit.SECONDS);
        config.transaction().lockingMode(LockingMode.OPTIMISTIC).transactionMode(TransactionMode.NON_TRANSACTIONAL);

        cacheManager.defineConfiguration(cacheName, config.build());
        log.info("Created sseEventCache");

        return cacheManager.getCache(cacheName);
    }

}