package com.urlshortener;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires running PostgreSQL and Redis — start infrastructure first: docker compose up postgres redis")
@SpringBootTest
class UrlShortenerApplicationTests {

    @Test
    void contextLoads() {
    }
}
