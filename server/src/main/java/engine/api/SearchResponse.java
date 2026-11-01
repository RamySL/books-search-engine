package engine.api;

import java.util.List;

/**
 * Réponse à une recherche, par mot-clé ou par RegEx. Les trois premiers champs
 * ont la même forme qu'une {@link Page}.
 *
 * @param count          nombre total de livres trouvés
 * @param next           URL relative de la page suivante, ou null
 * @param results        livres de cette page, dans l'ordre du tri demandé
 * @param suggestions    livres proches des premiers résultats dans le graphe de
 *                       Jaccard (10 au plus), absents des résultats affichés
 * @param terms          mots de l'index réellement cherchés : la requête après
 *                       le tokeniseur pour un mot-clé, les termes reconnus par
 *                       l'automate pour une RegEx sur l'index, vide pour une
 *                       RegEx sur le texte intégral
 * @param termsTruncated vrai si la RegEx reconnaît trop de termes et qu'on n'a
 *                       gardé que les plus fréquents
 * @param durationMs     temps de calcul côté serveur, en millisecondes
 */
public record SearchResponse(
        long count,
        String next,
        List<BookSummary> results,
        List<BookSummary> suggestions,
        List<String> terms,
        boolean termsTruncated,
        long durationMs) {
}
