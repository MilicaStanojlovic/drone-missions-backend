package com.project.drone_missions.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings for the hand-written mission cache, bound from {@code app.cache.mission.*}.
 *
 * <p>A record needs no {@code @ConstructorBinding} — constructor binding is the default when
 * there is a single constructor, and {@code spring-boot-starter-parent} already compiles with
 * {@code -parameters}. {@code @Validated} means a nonsensical value fails the application at
 * startup rather than at runtime.
 *
 * @param ttl         how long a cached mission or id list stays valid; accepts {@code 5m},
 *                    {@code 300s} or ISO-8601 {@code PT5M}
 * @param maxSize     most missions held at once
 * @param listMaxSize most cached query results held at once — smaller, because the key space
 *                    includes a free-text keyword filter
 */
@Validated
@ConfigurationProperties("app.cache.mission")
public record MissionCacheProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5m") Duration ttl,
        @DefaultValue("1000") @Positive int maxSize,
        @DefaultValue("200") @Positive int listMaxSize) {
}
