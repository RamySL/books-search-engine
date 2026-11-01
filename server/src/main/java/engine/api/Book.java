package engine.api;

/**
 * Fiche complète d'un livre, pour la vue Livre du client.
 *
 * @param author    null si l'auteur est inconnu
 * @param wordCount nombre de mots du texte, sans l'en-tête ni le pied de page Gutenberg
 * @param degree    nombre de voisins dans le graphe de Jaccard
 * @param cover     URL de la couverture sur gutenberg.org, voir {@link Covers}
 */
public record Book(
        int id,
        String title,
        String author,
        String language,
        int wordCount,
        int degree,
        double closeness,
        double pagerank,
        String cover) {
}
