package engine.api;

/**
 * Ordre des résultats d'une recherche (paramètre {@code sort}), toujours
 * décroissant. À égalité, on départage par le nombre d'occurrences, puis par
 * l'id pour que l'ordre ne dépende pas de PostgreSQL.
 */
public enum Sort {
    /** nombre d'occurrences de la requête dans le livre (par défaut) */
    OCCURRENCES,
    /** centralité de proximité dans le graphe de Jaccard */
    CLOSENESS,
    /** PageRank dans le graphe de Jaccard */
    PAGERANK
}
