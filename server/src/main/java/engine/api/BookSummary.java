package engine.api;

/**
 * Livre tel qu'il apparaît dans une liste : résultats d'une recherche,
 * suggestions, catalogue, voisins.
 *
 * @param occurrences nombre d'occurrences de la requête dans le livre ; null
 *                    hors d'une recherche (catalogue, voisins)
 * @param cover       URL de la couverture sur gutenberg.org, voir {@link Covers}
 */
public record BookSummary(
        int id,
        String title,
        String author,
        String cover,
        Long occurrences,
        double closeness,
        double pagerank) {
}
