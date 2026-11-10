package com.perfumestore.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(2)
public class OrderTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(OrderTraceAspect.class);

    @Around("execution(* com.perfumestore.service.OrderServiceImpl.checkout(..))")
    public Object traceCheckout(ProceedingJoinPoint joinPoint) throws Throwable {

        log.info("TRACE enter checkout");

        try {
            return joinPoint.proceed();
        } finally {
            log.info("TRACE exit checkout");
        }
    }
}
