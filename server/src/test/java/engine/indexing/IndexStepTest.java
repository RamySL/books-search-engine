package engine.indexing;

import engine.TestDatabase;
import engine.corpus.CorpusProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Étape index sur la base de test, avec les textes de la mini-bibliothèque du jeu d'essai. */
@SpringBootTest
@Import(TestDatabase.class)
@Sql("/jeu-essai.sql")
class IndexStepTest {

    private static final List<String> TEXTS = List.of(
            "Sargon of Akkad founded an empire. The empire of Sargon fell.",
            "Hammurabi of Babylon wrote laws. Babylon was the city of Hammurabi.",
            "The empire of Akkad and the city of Babylon.");

    private static final String TERMS = "SELECT id || ' ' || word || ' ' || book_count FROM term ORDER BY id";
    private static final String OCCURRENCES =
            "SELECT term_id || ' ' || book_id || ' ' || frequency FROM occurrence ORDER BY term_id, book_id";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionTemplate transaction;

    @TempDir
    private Path dataDir;

    private void index() {
        CorpusProperties corpus = new CorpusProperties(dataDir, dataDir.resolve("ids.txt"), URI.create("https://example.org"));
        new IndexStep(corpus, jdbc, dataSource, transaction).run(new DefaultApplicationArguments("index"));
    }

    private void writeTexts() throws IOException {
        Path books = Files.createDirectories(dataDir.resolve("books"));
        for (int i = 0; i < TEXTS.size(); i++) {
            Files.writeString(books.resolve((i + 1) + ".txt"), TEXTS.get(i));
        }
    }

    /** Les livres du jeu d'essai ont 9 à 11 mots : on les grossit pour passer le contrôle. */
    private void passWordCountControl() {
        jdbc.sql("UPDATE book SET word_count = 10000").update();
    }

    private List<String> rows(String sql) {
        return jdbc.sql(sql).query(String.class).list();
    }

    @Test
    void rebuildsTheTestDataIndex() throws IOException {
        List<String> terms = rows(TERMS);
        List<String> occurrences = rows(OCCURRENCES);
        jdbc.sql("TRUNCATE occurrence, term").update();
        writeTexts();
        passWordCountControl();

        index();

        assertEquals(terms, rows(TERMS));
        assertEquals(occurrences, rows(OCCURRENCES));
        assertEquals(List.of("occurrence_book_id_fkey", "occurrence_pkey", "occurrence_term_id_fkey"),
                rows("""
                        SELECT conname::text FROM pg_constraint
                        WHERE conrelid = 'occurrence'::regclass AND contype IN ('p', 'f')
                        ORDER BY 1"""));
        // le graphe n'est pas touché
        assertEquals(4, jdbc.sql("SELECT count(*) FROM neighbor").query(Integer.class).single());
    }

    @Test
    void failsOnABookUnderTheMinimum() throws IOException {
        writeTexts();
        assertThrows(IllegalStateException.class, this::index);
    }

    @Test
    void failsOnAMissingText() {
        passWordCountControl();
        assertThrows(IllegalStateException.class, this::index);
    }
}
