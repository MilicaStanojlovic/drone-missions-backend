package com.project.drone_missions.config;

import com.project.drone_missions.data.access.CachingMissionDao;
import com.project.drone_missions.data.access.JpaMissionDao;
import com.project.drone_missions.data.access.MissionDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * Wires the mission cache in front of the database-backed data access layer.
 *
 * <p>Turning the cache off is not a runtime flag: with {@code app.cache.mission.enabled=false}
 * the decorator bean is never created, so {@link JpaMissionDao} becomes the only
 * candidate and is injected directly. The disabled path therefore costs nothing at all — no
 * branch on every call, and no no-op implementation to maintain.
 *
 * <p>This is the default implementation: it is active unless the {@code cache-spring} profile
 * selects the {@code @Cacheable} one instead (see {@code SpringCacheConfig}). The two configs
 * carry opposite {@code @Profile} expressions, so exactly one {@code @Primary MissionDao}
 * exists in any context and a run with no profile set behaves as it always has.
 */
@Slf4j
@Configuration
@Profile("!cache-spring")
@EnableConfigurationProperties(MissionCacheProperties.class)
public class MissionCacheConfig {

    /** The cache's time source. Separate bean so tests can substitute a fixed clock. */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock cacheClock() {
        return Clock.systemUTC();
    }

    /**
     * Injects the concrete {@link JpaMissionDao} rather than the interface: it is
     * compile-time safe, needs no qualifier string, and cannot accidentally self-inject.
     *
     * <p>Announces itself at startup. Both implementations boot cleanly and serve identical
     * responses, so without this line a mistyped profile — {@code springcache} instead of
     * {@code cache-spring}, say — silently leaves you on this one, and the only tell is the
     * periodic reporter's prefix up to a report interval later.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.cache.mission", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public MissionDao cachingMissionDao(JpaMissionDao delegate,
                                         MissionCacheProperties properties,
                                         Clock clock) {
        log.info("mission cache: CachingMissionDao (hand-written TtlCache) ttl={} entities={} lists={}",
                properties.ttl(), properties.maxSize(), properties.listMaxSize());
        return new CachingMissionDao(delegate, properties, clock);
    }
}
