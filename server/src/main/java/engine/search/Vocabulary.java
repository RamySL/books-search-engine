package engine.search;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Vocabulaire de l'index (table term) gardé en mémoire, partagé par la
 * recherche par mot-clé (préfixes) et la RegEx sur l'index (automate) :
 * parcourir des tableaux coûte bien moins cher qu'une requête en base par
 * terme.
 * <p>
 * Il est chargé à la première recherche, et rechargé tant qu'il est vide
 * (serveur démarré avant l'indexation). Après une nouvelle indexation, il
 * faut redémarrer le serveur.
 */
@Component
public class Vocabulary {

    /**
     * Les termes, rangés par nombre de livres décroissant puis par ordre
     * alphabétique : un indice petit est un terme répandu. {@code alphabetical}
     * donne les mêmes indices dans l'ordre alphabétique des mots, pour
     * chercher un préfixe par dichotomie.
     */
    public record Terms(int[] ids, String[] words, int[] alphabetical) {

        /** @param words dans l'ordre de {@code ids}, du plus répandu au moins répandu */
        static Terms of(int[] ids, String[] words) {
            int[] alphabetical = IntStream.range(0, words.length).boxed()
                    .sorted(Comparator.comparing(i -> words[i]))
                    .mapToInt(Integer::intValue).toArray();
            return new Terms(ids, words, alphabetical);
        }

        public int size() {
            return words.length;
        }

        /**
         * Les termes qui commencent par {@code prefix} (le préfixe lui-même
         * compris) sont contigus dans l'ordre alphabétique : une dichotomie
         * trouve le premier, puis on avance tant que le préfixe correspond.
         * Coût O(log V + k) pour k termes trouvés, comme un parcours
         * d'intervalle dans un arbre B.
         *
         * @return les indices de ces termes, du plus répandu au moins répandu
         */
        public int[] withPrefix(String prefix) {
            int low = 0;
            int high = alphabetical.length;
            // premier mot >= prefix
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (words[alphabetical[middle]].compareTo(prefix) < 0) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            int end = low;
            while (end < alphabetical.length && words[alphabetical[end]].startsWith(prefix)) {
                end++;
            }
            int[] found = Arrays.copyOfRange(alphabetical, low, end);
            Arrays.sort(found);
            return found;
        }
    }

    private final JdbcClient jdbc;
    private volatile Terms terms;

    Vocabulary(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Terms terms() {
        Terms current = terms;
        if (current == null) {
            current = load();
            if (current.size() > 0) {
                terms = current;
            }
        }
        return current;
    }

    private Terms load() {
        List<Integer> ids = new ArrayList<>();
        List<String> words = new ArrayList<>();
        jdbc.sql("SELECT id, word FROM term ORDER BY book_count DESC, word")
                .query(row -> {
                    ids.add(row.getInt("id"));
                    words.add(row.getString("word"));
                });
        return Terms.of(ids.stream().mapToInt(Integer::intValue).toArray(), words.toArray(String[]::new));
    }
}
