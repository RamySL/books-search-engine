package engine;

import org.springframework.boot.ApplicationArguments;

/**
 * Étape de précalcul hors ligne. Un bean qui implémente cette interface se
 * lance par {@code java -jar server-1.0.jar <nom>}.
 */
public interface OfflineStep {

    /** @return le nom de l'étape, donné en premier argument du jar */
    String name();

    /** @param args arguments du jar, dont les options de l'étape (--limit=50…) */
    void run(ApplicationArguments args) throws Exception;
}
