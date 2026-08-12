package com.basiclab.iot.system.job.supervision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class SupervisionAlertReviewRuntimeJobLockAspect {

    private static final String LOCK_PREFIX = "yfeieye:review:local-scheduler:";

    private final RedissonClient redissonClient;

    @Around("@annotation(com.basiclab.iot.system.job.supervision.SupervisionAlertReviewRuntimeJob)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        SupervisionAlertReviewRuntimeJob runtimeJob = AnnotatedElementUtils.findMergedAnnotation(
                AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass()),
                SupervisionAlertReviewRuntimeJob.class);
        if (runtimeJob == null) {
            throw new IllegalStateException("review runtime job annotation missing");
        }
        String handlerName = runtimeJob.value();
        RLock lock = null;
        boolean acquired = false;
        try {
            lock = redissonClient.getLock(LOCK_PREFIX + handlerName);
            acquired = lock.tryLock();
            if (!acquired) {
                log.info("[around][handler({}) skipped because another execution source owns the lock]",
                        handlerName);
                return "skipped=distributed_lock_held,handler=" + handlerName;
            }
            return joinPoint.proceed();
        } catch (Throwable error) {
            log.error("[around][handler({}) failed]", handlerName, error);
            throw error;
        } finally {
            if (acquired && lock != null) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception unlockError) {
                    log.error("[around][handler({}) lock release failed]", handlerName, unlockError);
                }
            }
        }
    }

}
