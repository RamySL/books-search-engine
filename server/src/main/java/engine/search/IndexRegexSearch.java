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
 * mots du vocabulaire (gardé en mémoire, voir {@link Vocabulary}), pas au
 * texte des livres. Un terme est retenu si l'automate le reconnaît en entier ;
 * un livre est retenu s'il contient au moins un terme retenu, et son score est
 * la somme de leurs fréquences.
 */
@Component
public class IndexRegexSearch {

    private final JdbcClient jdbc;
    private final Vocabulary vocabulary;

    IndexRegexSearch(JdbcClient jdbc, Vocabulary vocabulary) {
        this.jdbc = jdbc;
        this.vocabulary = vocabulary;
    }

    /** @throws IllegalArgumentException si la RegEx est mal formée */
    public Matches search(String regex) {
        // l'index est en minuscules
        Dfa dfa = compile(regex.toLowerCase(Locale.ROOT));
        Vocabulary.Terms vocabulary = this.vocabulary.terms();
        // l'ordre du vocabulaire est conservé : les premiers reconnus sont les plus répandus
        int[] matches = IntStream.range(0, vocabulary.size()).parallel()
                .filter(i -> dfa.accepte(vocabulary.words()[i]))
                .toArray();
        boolean truncated = matches.length > Matches.MAX_TERMS;
        if (truncated) {
            matches = Arrays.copyOf(matches, Matches.MAX_TERMS);
        }
        Integer[] termIds = new Integer[matches.length];
        List<String> terms = new ArrayList<>(matches.length);
        for (int i = 0; i < matches.length; i++) {
            termIds[i] = vocabulary.ids()[matches[i]];
            terms.add(vocabulary.words()[matches[i]]);
        }
        return new Matches(occurrences(termIds), terms, truncated);
    }

    /** Chaîne du projet egrep : arbre syntaxique, ε-AFN, DFA, DFA minimal. */
    public static Dfa compile(String regex) {
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
}
