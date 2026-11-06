package com.otboo.team03.global.config;

import com.otboo.team03.global.redis.RedisLockProvider.RedisLockAcquisitionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

@Configuration
@EnableRetry
public class RetryConfig {

    @Bean
    public RetryOperationsInterceptor jwtInvalidationRetryInterceptor() {
        ExceptionClassifierRetryPolicy retryPolicy = new ExceptionClassifierRetryPolicy();
        retryPolicy.setPolicyMap(Map.of(
                RedisLockAcquisitionException.class, new SimpleRetryPolicy(10),
                RedisConnectionFailureException.class, new SimpleRetryPolicy(3)
        ));

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(100);
        backOffPolicy.setMultiplier(2);

        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffPolicy(backOffPolicy)
                .build();
    }
}
