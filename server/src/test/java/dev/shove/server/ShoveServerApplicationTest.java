package dev.shove.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "shove.storage-root=target/test-storage"
})
@ActiveProfiles("dev")
class ShoveServerApplicationTest {

    @Test
    void contextLoads() {
    }
}

