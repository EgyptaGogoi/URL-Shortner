package com.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configures a {@link RedisTemplate} with plain String serialization on both keys and values.
 * This overrides Spring Boot's default JDK serialization, which is not human-readable
 * and would make manual Redis inspection difficult.
 */
@Configuration
public class RedisConfig {

    /**
     * Produces a {@code RedisTemplate<String, String>} with {@link StringRedisSerializer}
     * on both the key and value channels.
     *
     * @param factory the auto-configured Lettuce connection factory
     * @return the configured template used by {@link com.urlshortener.service.UrlShortenerService}
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
