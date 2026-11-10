package com.perfumestore.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class StoreActionAspect {

    private static final Logger log = LoggerFactory.getLogger(StoreActionAspect.class);

    @Before("@annotation(com.perfumestore.aop.LogStoreAction)")
    public void logAction(JoinPoint joinPoint) {
        log.info("Tracked action started: {}",joinPoint.getSignature().getName());
    }
}
