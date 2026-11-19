package engine.search;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recherche par mot-clé dans l'index inversé. Chaque mot de la requête est un
 * préfixe : « prin » trouve prince, princess, print… (et prin lui-même), ce
 * qui couvre aussi les pluriels et les formes fléchies (« whale » trouve
 * whales). Une requête de plusieurs mots est un ET : un livre n'est retenu que
 * s'il contient, pour chaque mot de la requête, au moins un terme qui commence
 * par ce mot ; son score est la somme des fréquences des termes trouvés.
 * <p>
 * Les termes d'un préfixe sont trouvés par dichotomie dans le vocabulaire en
 * mémoire ({@link Vocabulary}), puis la base fait le ET et les sommes.
 */
@Component
public class KeywordSearch {

    private final JdbcClient jdbc;
    private final Vocabulary vocabulary;

    KeywordSearch(JdbcClient jdbc, Vocabulary vocabulary) {
        this.jdbc = jdbc;
        this.vocabulary = vocabulary;
    }

    /**
     * @param words mots de la requête, déjà passés par le tokeniseur, sans doublon
     * @return livres trouvés, et termes trouvés mot après mot ; aucun livre si
     *         un mot ne commence aucun terme de l'index
     */
    public Matches search(List<String> words) {
        Vocabulary.Terms vocabulary = this.vocabulary.terms();
        // pour chaque terme trouvé : son id et le numéro du mot de la requête qui l'a trouvé
        List<Integer> termIds = new ArrayList<>();
        List<Integer> groups = new ArrayList<>();
        List<String> terms = new ArrayList<>();
        boolean truncated = false;
        boolean missing = false;
        for (int group = 0; group < words.size(); group++) {
            int[] found = vocabulary.withPrefix(words.get(group));
            if (found.length > Matches.MAX_TERMS) {
                found = Arrays.copyOf(found, Matches.MAX_TERMS);
                truncated = true;
            }
            missing |= found.length == 0;
            for (int i : found) {
                termIds.add(vocabulary.ids()[i]);
                groups.add(group);
                terms.add(vocabulary.words()[i]);
            }
        }
        if (words.isEmpty() || missing) {
            return new Matches(Map.of(), terms, truncated);
        }
        return new Matches(occurrences(termIds, groups, words.size()), terms, truncated);
    }

    private Map<Integer, Long> occurrences(List<Integer> termIds, List<Integer> groups, int words) {
        Map<Integer, Long> occurrences = new HashMap<>();
        // un livre est retenu s'il a des occurrences pour les k mots de la requête
        jdbc.sql("""
                        SELECT o.book_id, sum(o.frequency) AS occurrences
                        FROM unnest(?::integer[], ?::integer[]) AS t(term_id, word)
                        JOIN occurrence o ON o.term_id = t.term_id
                        GROUP BY o.book_id
                        HAVING count(DISTINCT t.word) = ?""")
                .param(termIds.toArray(Integer[]::new))
                .param(groups.toArray(Integer[]::new))
                .param(words)
                .query(row -> {
                    occurrences.put(row.getInt("book_id"), row.getLong("occurrences"));
                });
        return occurrences;
    }
}
