package engine.indexing;

import engine.MeasuresProperties;
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape index-scaling sur le jeu d'essai : 3 livres de 11, 11 et 9 mots, donc
 * une seule taille d'échantillon. Leur index compte 10 termes et 14 lignes.
 */
@SpringBootTest
@Import(TestDatabase.class)
@Sql("/jeu-essai.sql")
class IndexScalingTest {

    @Autowired
    private JdbcClient jdbc;

    @TempDir
    private Path measures;

    private void measure() throws IOException, URISyntaxException {
        Path dataDir = Path.of(getClass().getResource("/jeu-essai").toURI());
        CorpusProperties corpus = new CorpusProperties(dataDir, dataDir.resolve("ids.txt"), URI.create("https://example.org"));
        new IndexScaling(corpus, new MeasuresProperties(measures), jdbc)
                .run(new DefaultApplicationArguments("index-scaling", "--runs=1"));
    }

    @Test
    void writesOneLinePerSampleSize() throws IOException, URISyntaxException {
        measure();

        List<String> lines = Files.readAllLines(measures.resolve("index-scaling.csv"));
        assertEquals(2, lines.size());
        assertEquals("books,words,terms,rows,seconds", lines.getFirst());
        assertTrue(lines.get(1).startsWith("3,31,10,14,"), lines.get(1));
    }

    @Test
    void failsOnAnEmptyCorpus() {
        jdbc.sql("TRUNCATE neighbor, occurrence, term, book").update();

        assertThrows(IllegalStateException.class, this::measure);
    }
}
