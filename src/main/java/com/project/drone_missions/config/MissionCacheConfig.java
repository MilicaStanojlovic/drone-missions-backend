package com.project.drone_missions.config;

import com.project.drone_missions.data.access.CachingMissionDataAccess;
import com.project.drone_missions.data.access.JpaMissionDataAccess;
import com.project.drone_missions.data.access.MissionDataAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/**
 * Wires the mission cache in front of the database-backed data access layer.
 *
 * <p>Turning the cache off is not a runtime flag: with {@code app.cache.mission.enabled=false}
 * the decorator bean is never created, so {@link JpaMissionDataAccess} becomes the only
 * candidate and is injected directly. The disabled path therefore costs nothing at all — no
 * branch on every call, and no no-op implementation to maintain.
 */
@Configuration
@EnableConfigurationProperties(MissionCacheProperties.class)
public class MissionCacheConfig {

    /** The cache's time source. Separate bean so tests can substitute a fixed clock. */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock cacheClock() {
        return Clock.systemUTC();
    }

    /**
     * Injects the concrete {@link JpaMissionDataAccess} rather than the interface: it is
     * compile-time safe, needs no qualifier string, and cannot accidentally self-inject.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.cache.mission", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public MissionDataAccess cachingMissionDataAccess(JpaMissionDataAccess delegate,
                                                      MissionCacheProperties properties,
                                                      Clock clock) {
        return new CachingMissionDataAccess(delegate, properties, clock);
    }
}
