package engine.search;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recherche par mot-clé dans l'index inversé (tables term et occurrence).
 * Une requête de plusieurs mots est un ET : un livre n'est retenu que s'il
 * contient tous les mots, et son score est la somme de leurs fréquences.
 */
@Component
public class KeywordSearch {

    private final JdbcClient jdbc;

    KeywordSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param words mots de l'index, déjà passés par le tokeniseur, sans doublon
     * @return nombre d'occurrences de la requête par livre ; vide si un mot est
     *         absent de l'index
     */
    public Map<Integer, Long> occurrences(List<String> words) {
        Map<Integer, Long> occurrences = new HashMap<>();
        if (words.isEmpty()) {
            return occurrences;
        }
        // un livre qui contient les k mots a k lignes dans occurrence
        jdbc.sql("""
                        SELECT o.book_id, sum(o.frequency) AS occurrences
                        FROM term t
                        JOIN occurrence o ON o.term_id = t.id
                        WHERE t.word = ANY(?)
                        GROUP BY o.book_id
                        HAVING count(*) = ?""")
                .param(words.toArray(String[]::new))
                .param(words.size())
                .query(row -> {
                    occurrences.put(row.getInt("book_id"), row.getLong("occurrences"));
                });
        return occurrences;
    }
}
