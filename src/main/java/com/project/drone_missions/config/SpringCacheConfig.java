package com.project.drone_missions.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.project.drone_missions.data.access.JpaMissionDataAccess;
import com.project.drone_missions.data.access.SpringCacheMissionDataAccess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Wires the {@code @Cacheable} mission cache. The twin of {@link MissionCacheConfig} — same
 * shape, same properties, different implementation — so the two can be read side by side.
 *
 * <p>Selecting between them is a profile, not a property: {@code cache-spring} activates this
 * configuration and deactivates {@link MissionCacheConfig}, which carries the matching
 * {@code @Profile("!cache-spring")}. Exactly one {@code @Primary MissionDataAccess} therefore
 * exists in any context. With no profile set the application behaves exactly as it always has —
 * the hand-written cache is still the default.
 *
 * <p>Activate it from the run configuration or the environment, not from
 * {@code application.properties}:
 * <pre>
 *   mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=cache-spring"
 *   SPRING_PROFILES_ACTIVE=cache-spring
 *   SPRING_PROFILES_ACTIVE=local,cache-spring   (with the mail profile)
 * </pre>
 *
 * <p>{@code @Profile} sits on the class rather than on the beans so that {@code @EnableCaching}
 * itself is profile-scoped: with the profile inactive no caching proxy infrastructure is created
 * at all, preserving the "the disabled path costs nothing" property {@link MissionCacheConfig}
 * documents.
 */
@Slf4j
@Configuration
@Profile("cache-spring")
// proxyTargetClass so the proxy is a subclass of SpringCacheMissionDataAccess rather than a JDK
// proxy over MissionDataAccess. The stats reporter is @Scheduled and is not on the interface; a
// JDK proxy would not carry it and the scheduler could not invoke it.
@EnableCaching(proxyTargetClass = true)
@EnableConfigurationProperties(MissionCacheProperties.class)
public class SpringCacheConfig {

    /**
     * Caffeine rather than the default {@code ConcurrentMapCacheManager}, which has neither TTL
     * nor a size bound. Both caches are built from the same {@link MissionCacheProperties} the
     * hand-written cache uses, so the two implementations are sized identically and the
     * comparison is apples-to-apples — deliberately no separate {@code app.cache.spring.*} prefix.
     *
     * <p>Registered per cache rather than with one shared builder because the two have different
     * bounds: the list cache is smaller, its key space including a free-text keyword filter.
     * {@code recordStats()} is what makes the reporter on
     * {@code SpringCacheMissionDataAccess#sweepAndReport()} possible — {@code @code} rather than
     * {@code @link} because that method is package-private in {@code data.access} and so is not
     * resolvable from this package.
     */
    @Bean
    public CacheManager cacheManager(MissionCacheProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Absent missions are never cached (see SpringCacheMissionDataAccess#findById), so a null
        // value reaching the cache would be a bug — fail loudly rather than store it.
        manager.setAllowNullValues(false);
        // Fixes the cache set: by default this manager is dynamic and mints a cache for any name
        // asked of it, built with a bare Caffeine builder — no TTL and no size bound. A typo in a
        // @Cacheable name would then get a silently unbounded cache instead of failing.
        //
        // Order matters and is not interchangeable: setCacheNames overwrites cacheMap entries with
        // default-configured caches, so it must come BEFORE the registrations below, which then
        // replace those defaults with the configured instances.
        manager.setCacheNames(List.of(SpringCacheMissionDataAccess.MISSIONS,
                SpringCacheMissionDataAccess.MISSION_LISTS));
        manager.registerCustomCache(SpringCacheMissionDataAccess.MISSIONS,
                Caffeine.newBuilder()
                        .expireAfterWrite(properties.ttl())
                        .maximumSize(properties.maxSize())
                        .recordStats()
                        .build());
        manager.registerCustomCache(SpringCacheMissionDataAccess.MISSION_LISTS,
                Caffeine.newBuilder()
                        .expireAfterWrite(properties.ttl())
                        .maximumSize(properties.listMaxSize())
                        .recordStats()
                        .build());
        return manager;
    }

    /**
     * Injects the concrete {@link JpaMissionDataAccess} rather than the interface: it is
     * compile-time safe, needs no qualifier string, and cannot accidentally self-inject.
     *
     * <p>Keeps {@link MissionCacheConfig}'s {@code @ConditionalOnProperty} guard, so
     * {@code app.cache.mission.enabled=false} still yields the uncached baseline — under this
     * profile as well as the default one.
     *
     * <p>Announces itself at startup, in the same format {@link MissionCacheConfig} uses. Both
     * implementations boot cleanly and serve identical responses, so this line is what tells you
     * the profile actually took effect — a mistyped {@code springcache} activates no profile
     * Spring knows about, and leaves you silently on the hand-written cache.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.cache.mission", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SpringCacheMissionDataAccess springCacheMissionDataAccess(JpaMissionDataAccess delegate,
                                                                    CacheManager cacheManager,
                                                                    MissionCacheProperties properties) {
        log.info("mission cache: SpringCacheMissionDataAccess (@Cacheable + Caffeine) ttl={} entities={} lists={}",
                properties.ttl(), properties.maxSize(), properties.listMaxSize());
        return new SpringCacheMissionDataAccess(delegate, cacheManager);
    }
}
