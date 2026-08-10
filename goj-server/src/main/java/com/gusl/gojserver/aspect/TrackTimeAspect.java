package com.gusl.gojserver.aspect;


import com.gusl.gojserver.aspect.annotations.TrackTime;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class TrackTimeAspect {

    @Around("@annotation(trackTime)")
    public Object recordTime(ProceedingJoinPoint joinPoint, TrackTime trackTime) throws Throwable {

        long startTime = System.nanoTime();

        try {
            // 执行原来的业务方法
            return joinPoint.proceed();
        } finally {
            long elapsedNanos = System.nanoTime() - startTime;
            long elapsedMillis = elapsedNanos / 1_000_000;

            String methodName = joinPoint.getSignature().toShortString();

            log.info("业务名称={}, 方法={}, 耗时={}ms", trackTime.value(), methodName, elapsedMillis);
        }
    }

}
