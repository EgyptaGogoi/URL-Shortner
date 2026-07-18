package com.urlshortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the OpenAPI metadata served at {@code /v3/api-docs} and rendered
 * by the Swagger UI at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Builds the top-level {@link OpenAPI} descriptor with project info and a
     * server entry derived from {@code app.base-url} so the "Try it out" feature
     * points at the right host automatically.
     *
     * @return the configured OpenAPI bean consumed by springdoc
     */
    @Bean
    public OpenAPI urlShortenerOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("URL Shortener API")
                .description("""
                    REST API for shortening long URLs, creating custom aliases, \
                    and redirecting short codes to their original destinations.

                    **Rate limiting:** `POST /api/shorten` is limited to 10 requests per minute per IP.

                    **SSRF protection:** submitted URLs must use `http` or `https` \
                    and must not resolve to private or loopback addresses.""")
                .version("1.0.0"))
            .addServersItem(new Server()
                .url(baseUrl)
                .description("Current server"));
    }
}
