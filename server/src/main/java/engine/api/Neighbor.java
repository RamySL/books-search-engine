package engine.api;

/**
 * Voisin d'un livre dans le graphe de Jaccard.
 *
 * @param distance distance de Jaccard pondérée entre les deux livres, entre 0
 *                 (même table d'index) et 1 (aucun mot commun)
 */
public record Neighbor(BookSummary book, double distance) {
}
