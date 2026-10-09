package com.bellidental;

import com.bellidental.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(TestcontainersConfiguration.class)
class SchemaMigrationIT {

    @Test
    void contextLoadsAndSchemaValidates() {
        // If context loads, Flyway migrated successfully and Hibernate validated the schema
    }
}
