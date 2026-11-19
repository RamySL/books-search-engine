package engine.search;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Vocabulaire de l'index (table term) gardé en mémoire pour la RegEx sur
 * l'index : parcourir des tableaux coûte bien moins cher qu'une requête en
 * base par terme.
 * <p>
 * Il est chargé à la première recherche, et rechargé tant qu'il est vide
 * (serveur démarré avant l'indexation). Après une nouvelle indexation, il
 * faut redémarrer le serveur.
 */
@Component
public class Vocabulary {

    /** Les termes, rangés par nombre de livres décroissant puis par ordre alphabétique. */
    public record Terms(int[] ids, String[] words) {

        public int size() {
            return words.length;
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
        return new Terms(ids.stream().mapToInt(Integer::intValue).toArray(), words.toArray(String[]::new));
    }
}
