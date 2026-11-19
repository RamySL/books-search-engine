package engine.search;

import java.util.List;
import java.util.Map;

/**
 * Résultat d'une recherche dans le vocabulaire (mot-clé ou RegEx sur l'index).
 *
 * @param occurrences nombre d'occurrences des termes trouvés, par livre
 * @param terms       termes trouvés, les plus répandus d'abord
 * @param truncated   vrai si des termes trouvés ont été écartés, au-delà de
 *                    {@link #MAX_TERMS}
 */
public record Matches(Map<Integer, Long> occurrences, List<String> terms, boolean truncated) {

    /**
     * Au-delà, une requête trop large (RegEx « .* », préfixe « co ») ferait
     * une requête énorme : on ne garde que les termes présents dans le plus de
     * livres.
     */
    public static final int MAX_TERMS = 2000;
}
