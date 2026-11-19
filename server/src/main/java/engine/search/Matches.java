package engine.search;

import java.util.List;
import java.util.Map;

/**
 * Résultat d'une recherche dans l'index (mot-clé ou RegEx sur l'index).
 *
 * @param occurrences nombre d'occurrences des termes cherchés, par livre
 * @param terms       termes cherchés : les mots de la requête pour un
 *                    mot-clé, les termes reconnus (les plus répandus d'abord)
 *                    pour une RegEx
 * @param truncated   vrai si des termes reconnus par la RegEx ont été
 *                    écartés, au-delà de {@link #MAX_TERMS}
 */
public record Matches(Map<Integer, Long> occurrences, List<String> terms, boolean truncated) {

    /**
     * Au-delà, une RegEx trop large (« .* ») ferait une requête énorme : on ne
     * garde que les termes présents dans le plus de livres.
     */
    public static final int MAX_TERMS = 2000;
}
