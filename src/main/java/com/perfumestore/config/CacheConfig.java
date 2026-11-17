package com.perfumestore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(@Value("${spring.cache.caffeine.spec}") String specification) {

        CaffeineCacheManager manager = new CaffeineCacheManager("categories","categoryPages","products","productPages");
        manager.setCacheSpecification(specification);
        manager.setAllowNullValues(false);

        return new TransactionAwareCacheManagerProxy(manager);
    }
}