package engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Mesures écrites par les étapes hors ligne pour le rapport (fichiers CSV).
 *
 * @param dir dossier des mesures ; un chemin relatif part du dossier de lancement du jar
 */
@ConfigurationProperties("measures")
public record MeasuresProperties(Path dir) {
}
