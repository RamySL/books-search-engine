package engine.corpus;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;

/**
 * Fichiers et source de l'étape corpus. Les chemins relatifs partent du
 * dossier de lancement du jar.
 *
 * @param dataDir dossier de travail : textes des livres, catalogue, livres rejetés
 * @param idsFile liste des livres du corpus, versionnée pour le reproduire
 * @param mirror  miroir de Project Gutenberg
 */
@ConfigurationProperties("corpus")
public record CorpusProperties(Path dataDir, Path idsFile, URI mirror) {

    /** @return le dossier des textes nettoyés, un fichier {@code <id>.txt} par livre retenu */
    public Path booksDir() {
        return dataDir.resolve("books");
    }

    /** Catalogue de Gutenberg, gardé pour ne pas le retélécharger. */
    Path catalogFile() {
        return dataDir.resolve("pg_catalog.csv.gz");
    }

    /** Livres écartés, un numéro par ligne : une reprise ne les retélécharge pas. */
    Path rejectedFile() {
        return dataDir.resolve("rejected.txt");
    }
}
