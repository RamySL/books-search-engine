package engine.books;

import engine.api.Book;
import engine.api.BookSummary;
import engine.api.Covers;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Lecture de la table book : fiche d'un livre et catalogue. */
@Repository
public class BookRepository {

    private final JdbcClient jdbc;

    public BookRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Book> find(int id) {
        return jdbc.sql("""
                        SELECT id, title, author, language, word_count, degree, closeness, pagerank
                        FROM book
                        WHERE id = ?""")
                .param(id)
                .query(BookRepository::book)
                .optional();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM book").query(Long.class).single();
    }

    /** @return une page du catalogue, par ordre alphabétique des titres */
    public List<BookSummary> catalog(long offset, int limit) {
        return jdbc.sql("""
                        SELECT id, title, author, closeness, pagerank
                        FROM book
                        ORDER BY title, id
                        LIMIT ? OFFSET ?""")
                .param(limit)
                .param(offset)
                .query((row, rowNum) -> summary(row, null))
                .list();
    }

    private static Book book(ResultSet row, int rowNum) throws SQLException {
        int id = row.getInt("id");
        return new Book(id, row.getString("title"), row.getString("author"), row.getString("language"),
                row.getInt("word_count"), row.getInt("degree"), row.getDouble("closeness"),
                row.getDouble("pagerank"), Covers.url(id));
    }

    /**
     * Ligne de book → résumé, pour toute requête qui sélectionne id, title,
     * author, closeness et pagerank (aussi utilisé par la recherche).
     * @param occurrences null hors d'une recherche
     */
    public static BookSummary summary(ResultSet row, Long occurrences) throws SQLException {
        int id = row.getInt("id");
        return new BookSummary(id, row.getString("title"), row.getString("author"), Covers.url(id),
                occurrences, row.getDouble("closeness"), row.getDouble("pagerank"));
    }
}
