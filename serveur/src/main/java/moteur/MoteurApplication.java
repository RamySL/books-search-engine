package moteur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée de l'application Spring Boot : le serveur web, qui sert l'API
 * REST sous /api et le client compilé (resources/static).
 */
@SpringBootApplication
public class MoteurApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoteurApplication.class, args);
    }
}
