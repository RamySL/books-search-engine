package engine.indexing;

import engine.OfflineStep;
import engine.corpus.Corpus;
import engine.corpus.CorpusProperties;
import org.postgresql.PGConnection;
import org.postgresql.copy.PGCopyOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * Étape index : construit l'index inversé des livres de la table book et le
 * charge dans term et occurrence.
 * <p>
 * Le chargement tient en une transaction : on vide les deux tables, on retire
 * les clés de occurrence, on charge par COPY, puis on recrée les clés, ce qui
 * construit leurs index en une fois au lieu de les tenir à jour ligne à ligne.
 * Si l'étape échoue, l'ancien index reste intact.
 */
@Component
class IndexStep implements OfflineStep {

    private static final Logger log = LoggerFactory.getLogger(IndexStep.class);

    private final CorpusProperties corpus;
    private final JdbcClient jdbc;
    private final DataSource dataSource;
    private final TransactionTemplate transaction;

    IndexStep(CorpusProperties corpus, JdbcClient jdbc, DataSource dataSource, TransactionTemplate transaction) {
        this.corpus = corpus;
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.transaction = transaction;
    }

    /** Un livre de la table book et sa taille en mots. */
    private record Book(int id, int words) {
    }

    /** Écrit des lignes au format texte de COPY. */
    @FunctionalInterface
    private interface Rows {
        void writeTo(Writer out) throws IOException;
    }

    @Override
    public String name() {
        return "index";
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Integer> ids = books();
        long start = System.nanoTime();
        InvertedIndex index = InvertedIndex.build(ids, this::text);
        log.info("Tables pondérées et inversion : {} livres, {} termes, {} lignes, en {} s",
                ids.size(), index.termCount(), index.rowCount(), seconds(start));
        long load = System.nanoTime();
        load(index);
        jdbc.sql("ANALYZE term, occurrence").update();
        log.info("Chargement en base en {} s", seconds(load));
        log.info("Index : term {} Mo, occurrence {} Mo", megabytes("term"), megabytes("occurrence"));
    }

    /** Les livres à indexer ; échoue si le corpus est incohérent. */
    private List<Integer> books() {
        List<Book> books = jdbc.sql("SELECT id, word_count FROM book ORDER BY id")
                .query((rs, row) -> new Book(rs.getInt("id"), rs.getInt("word_count")))
                .list();
        if (books.isEmpty()) {
            throw new IllegalStateException("Table book vide : lancer d'abord l'étape corpus");
        }
        for (Book book : books) {
            if (book.words() < Corpus.MIN_WORDS) {
                throw new IllegalStateException("Livre " + book.id() + " : " + book.words()
                        + " mots, sous le minimum de " + Corpus.MIN_WORDS);
            }
        }
        List<Integer> missing = books.stream().map(Book::id).filter(id -> !Files.exists(file(id))).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(missing.size() + " textes introuvables dans "
                    + corpus.booksDir() + ", dont celui du livre " + missing.getFirst());
        }
        if (books.size() < Corpus.MIN_BOOKS) {
            log.warn("Corpus de {} livres, sous le minimum de {} : à réserver au développement",
                    books.size(), Corpus.MIN_BOOKS);
        }
        return books.stream().map(Book::id).toList();
    }

    private Path file(int id) {
        return corpus.booksDir().resolve(id + ".txt");
    }

    private String text(int id) {
        try {
            return Files.readString(file(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void load(InvertedIndex index) {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("TRUNCATE occurrence, term").update();
            jdbc.sql("""
                    ALTER TABLE occurrence
                        DROP CONSTRAINT IF EXISTS occurrence_pkey,
                        DROP CONSTRAINT IF EXISTS occurrence_term_id_fkey,
                        DROP CONSTRAINT IF EXISTS occurrence_book_id_fkey""").update();
            copy("COPY term (id, word, book_count) FROM STDIN", index::writeTerms);
            copy("COPY occurrence (term_id, book_id, frequency) FROM STDIN", index::writeOccurrences);
            // mêmes clés, et mêmes noms, que dans le schéma
            jdbc.sql("""
                    ALTER TABLE occurrence
                        ADD PRIMARY KEY (term_id, book_id),
                        ADD FOREIGN KEY (term_id) REFERENCES term,
                        ADD FOREIGN KEY (book_id) REFERENCES book""").update();
        });
    }

    /** COPY sur la connexion de la transaction en cours. */
    private void copy(String sql, Rows rows) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (Writer out = new BufferedWriter(new OutputStreamWriter(
                new PGCopyOutputStream(connection.unwrap(PGConnection.class), sql), StandardCharsets.UTF_8))) {
            rows.writeTo(out);
        } catch (SQLException e) {
            throw new IllegalStateException("Échec de " + sql, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private long megabytes(String table) {
        return jdbc.sql("SELECT pg_total_relation_size(?::regclass)").param(table)
                .query(Long.class).single() / (1024 * 1024);
    }

    private static String seconds(long start) {
        return String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1e9);
    }
}
