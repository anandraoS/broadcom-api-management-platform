package io.apiplatform.gateway.ratelimit;

import io.apiplatform.gateway.GatewayProperties;
import io.apiplatform.gateway.telemetry.TelemetryRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimiterConfig {
    @Bean
    RateLimiter rateLimiter(GatewayProperties props, ObjectProvider<StringRedisTemplate> redis, MeterRegistry registry,
                            TelemetryRecorder telemetry) {
        InMemoryRateLimiter local = new InMemoryRateLimiter();
        RateLimiter primary = props.redisEnabled() && redis.getIfAvailable() != null ? new RedisRateLimiter(redis.getObject()) : null;
        return new ResilientRateLimiter(primary, local, "CLOSED".equalsIgnoreCase(props.redisFailMode()),
                props.redisFallbackReplicas(), 5_000, System::currentTimeMillis, registry, telemetry);
    }
}
