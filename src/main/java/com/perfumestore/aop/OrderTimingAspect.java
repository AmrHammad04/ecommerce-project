package com.perfumestore.aop;

import com.perfumestore.dto.response.OrderResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(3)
public class OrderTimingAspect {

    private static final Logger log = LoggerFactory.getLogger(OrderTimingAspect.class);

    @Pointcut("execution(* com.perfumestore.service.OrderServiceImpl.*(..))")
    private void anyOrderServiceMethod() {
    }

    @Pointcut("execution(* com.perfumestore.service.OrderServiceImpl.get*(..))"
            + " || execution(* com.perfumestore.service.OrderServiceImpl.history(..))"
            + " || execution(* com.perfumestore.service.OrderServiceImpl.all(..))")
    private void readMethod() {

    }

    @Pointcut("anyOrderServiceMethod() && !readMethod()")
    private void orderAction() {

    }

    @Before("execution(* com.perfumestore.service.OrderServiceImpl.checkout(..))"+ " && args(userId)")
    public void logCheckoutRequest(Long userId) {
        log.info("Checkout requested: user {}", userId);
    }

    @Around("orderAction()")
    public Object timeOperation(ProceedingJoinPoint joinPoint) throws Throwable {

        long start = System.currentTimeMillis();

        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMs = (System.currentTimeMillis() - start);
            log.info("{} took {} ms",joinPoint.getSignature().getName(), elapsedMs);
        }
    }

    @AfterReturning(pointcut = "execution(* com.perfumestore.service.OrderServiceImpl.checkout(..))",returning = "order")
    public void logCreatedOrder(OrderResponse order) {
        log.info("checkout returned order ID {}", order.getId());
    }

    @AfterThrowing(pointcut = "orderAction()", throwing = "error")
    public void logFailedAction(JoinPoint joinPoint, Throwable error) {
        log.warn("{} failed: {}",joinPoint.getSignature().getName(), error.getMessage());
    }
}
