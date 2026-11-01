package engine;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base des tests : un PostgreSQL jetable, démarré dans Docker par
 * Testcontainers, sur lequel Flyway applique les migrations. Les tests ne
 * touchent donc jamais la base de {@code make db} et son corpus. Docker doit
 * tourner pendant {@code make test}.
 * <p>
 * S'utilise avec {@code @Import(TestDatabase.class)} ; le conteneur est
 * partagé par toutes les classes de test qui ont la même configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        // même version que docker-compose.yml
        return new PostgreSQLContainer("postgres:18");
    }
}
