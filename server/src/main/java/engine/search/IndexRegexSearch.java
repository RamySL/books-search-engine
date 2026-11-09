package engine.search;

import egrep.automate.Determinisation;
import egrep.automate.Dfa;
import egrep.automate.Minimisation;
import egrep.automate.Ndfa;
import egrep.regex.RegexParser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Recherche par RegEx sur l'index : l'automate de la RegEx est appliqué aux
 * mots du vocabulaire, pas au texte des livres. Un terme est retenu si
 * l'automate le reconnaît en entier ; un livre est retenu s'il contient au
 * moins un terme retenu, et son score est la somme de leurs fréquences.
 * <p>
 * Le vocabulaire est gardé en mémoire : le parcourir coûte bien moins cher
 * qu'une requête en base par terme. Il est chargé à la première recherche, et
 * rechargé tant qu'il est vide (serveur démarré avant l'indexation). Après une
 * nouvelle indexation, il faut redémarrer le serveur.
 */
@Component
public class IndexRegexSearch {

    /**
     * Au-delà, une RegEx trop large (« .* ») ferait une requête énorme : on ne
     * garde que les termes présents dans le plus de livres.
     */
    public static final int MAX_TERMS = 2000;

    /** Termes rangés par nombre de livres décroissant, puis par ordre alphabétique. */
    private record Vocabulary(int[] ids, String[] words) {
    }

    /**
     * @param occurrences nombre d'occurrences des termes reconnus, par livre
     * @param terms       termes reconnus, les plus répandus d'abord
     * @param truncated   vrai si des termes reconnus ont été écartés
     */
    public record Result(Map<Integer, Long> occurrences, List<String> terms, boolean truncated) {
    }

    private final JdbcClient jdbc;
    private volatile Vocabulary vocabulary;

    IndexRegexSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @throws IllegalArgumentException si la RegEx est mal formée */
    public Result search(String regex) {
        // l'index est en minuscules
        Dfa dfa = compile(regex.toLowerCase(Locale.ROOT));
        Vocabulary vocabulary = vocabulary();
        // l'ordre du vocabulaire est conservé : les premiers reconnus sont les plus répandus
        int[] matches = IntStream.range(0, vocabulary.words().length).parallel()
                .filter(i -> dfa.accepte(vocabulary.words()[i]))
                .toArray();
        boolean truncated = matches.length > MAX_TERMS;
        if (truncated) {
            matches = Arrays.copyOf(matches, MAX_TERMS);
        }
        Integer[] termIds = new Integer[matches.length];
        List<String> terms = new ArrayList<>(matches.length);
        for (int i = 0; i < matches.length; i++) {
            termIds[i] = vocabulary.ids()[matches[i]];
            terms.add(vocabulary.words()[matches[i]]);
        }
        return new Result(occurrences(termIds), terms, truncated);
    }

    /** Chaîne du projet egrep : arbre syntaxique, ε-AFN, DFA, DFA minimal. */
    private static Dfa compile(String regex) {
        return Minimisation.moore(Determinisation.sousEnsembles(Ndfa.ahoUllman(RegexParser.analyser(regex))));
    }

    private Map<Integer, Long> occurrences(Integer[] termIds) {
        Map<Integer, Long> occurrences = new HashMap<>();
        if (termIds.length == 0) {
            return occurrences;
        }
        jdbc.sql("""
                        SELECT book_id, sum(frequency) AS occurrences
                        FROM occurrence
                        WHERE term_id = ANY(?)
                        GROUP BY book_id""")
                .param(termIds)
                .query(row -> {
                    occurrences.put(row.getInt("book_id"), row.getLong("occurrences"));
                });
        return occurrences;
    }

    private Vocabulary vocabulary() {
        Vocabulary current = vocabulary;
        if (current == null) {
            current = load();
            if (current.ids().length > 0) {
                vocabulary = current;
            }
        }
        return current;
    }

    private Vocabulary load() {
        List<Integer> ids = new ArrayList<>();
        List<String> words = new ArrayList<>();
        jdbc.sql("SELECT id, word FROM term ORDER BY book_count DESC, word")
                .query(row -> {
                    ids.add(row.getInt("id"));
                    words.add(row.getString("word"));
                });
        return new Vocabulary(ids.stream().mapToInt(Integer::intValue).toArray(), words.toArray(String[]::new));
    }
}
