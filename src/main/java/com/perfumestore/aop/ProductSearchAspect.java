package com.perfumestore.aop;

import com.perfumestore.dto.response.PageResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ProductSearchAspect {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchAspect.class);

    @Pointcut("execution(* com.perfumestore.service.ProductServiceImpl.list(..))")
    private void productSearch() {

    }

    @Before("productSearch()")
    public void beforeSearch(JoinPoint joinPoint) {
        log.info("Calling service method: {}",joinPoint.getSignature().getName());
    }

    @AfterReturning(pointcut = "productSearch()", returning = "results")
    public void afterSearch(PageResponse<?> results) {
        log.info("Product search returned {} product(s), page {}, total {}",
                results.getContent().size(),
                results.getPage(),
                results.getTotalElements());
    }

    @AfterThrowing(pointcut = "execution(* com.perfumestore.service.ProductServiceImpl.get(..))",throwing = "error")
    public void logFailedLookup(JoinPoint joinPoint, Throwable error) {
        log.warn("{} failed: {}",joinPoint.getSignature().getName(), error.getMessage());
    }

    @After("execution(* com.perfumestore.service.ProductServiceImpl.get(..))")
    public void afterLookup() {
        log.info("get finished");
    }

    @AfterThrowing(
        pointcut = "execution(* com.perfumestore.service.ProductServiceImpl.delete(..))",throwing = "error")
    public void logDeleteFailure(Throwable error) {
        log.warn("delete failed: {}", error.getMessage());
    }
}
