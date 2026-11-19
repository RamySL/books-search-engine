package engine.recommendation;

import engine.api.BookSummary;
import engine.api.Neighbor;
import engine.books.BookRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Recommandations implicites : les livres proches, dans le graphe de Jaccard
 * (table neighbor), des meilleurs résultats d'une recherche. Le graphe est
 * précalculé hors ligne ; ici, on ne fait que lire des arêtes par la clé
 * primaire, ce qui reste rapide quel que soit le nombre de résultats.
 */
@Component
public class Recommendations {

    /** Nombre de premiers résultats dont on prend les voisins. */
    public static final int SEEDS = 3;

    /** Nombre maximal de suggestions renvoyées. */
    public static final int MAX_SUGGESTIONS = 10;

    private final JdbcClient jdbc;

    Recommendations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Voisins des premiers résultats, qui ne sont pas eux-mêmes affichés. Un
     * livre voisin de plusieurs premiers résultats compte une seule fois, avec
     * sa plus petite distance : plus il est proche de l'un d'eux, plus il est
     * haut dans la liste.
     *
     * @param seeds    ids des {@link #SEEDS} premiers résultats, dans l'ordre du tri
     * @param excluded ids des livres à ne pas suggérer : les premiers résultats et la page affichée
     * @return {@link #MAX_SUGGESTIONS} livres au plus, par distance croissante, puis par id
     */
    public List<BookSummary> suggestions(List<Integer> seeds, Collection<Integer> excluded) {
        if (seeds.isEmpty()) {
            return List.of();
        }
        // GROUP BY b.id suffit : les autres colonnes de book dépendent de la clé primaire
        return jdbc.sql("""
                        SELECT b.id, b.title, b.author, b.closeness, b.pagerank, min(n.distance) AS distance
                        FROM neighbor n
                        JOIN book b ON b.id = n.book_b
                        WHERE n.book_a = ANY(?) AND NOT b.id = ANY(?)
                        GROUP BY b.id
                        ORDER BY distance, b.id
                        LIMIT ?""")
                .param(seeds.toArray(Integer[]::new))
                .param(excluded.toArray(Integer[]::new))
                .param(MAX_SUGGESTIONS)
                .query((row, rowNum) -> BookRepository.summary(row, null))
                .list();
    }

    /** @return tous les voisins du livre, du plus proche au plus éloigné, puis par id */
    public List<Neighbor> neighbors(int bookId) {
        return jdbc.sql("""
                        SELECT b.id, b.title, b.author, b.closeness, b.pagerank, n.distance
                        FROM neighbor n
                        JOIN book b ON b.id = n.book_b
                        WHERE n.book_a = ?
                        ORDER BY n.distance, b.id""")
                .param(bookId)
                .query((row, rowNum) -> new Neighbor(BookRepository.summary(row, null), row.getDouble("distance")))
                .list();
    }
}
